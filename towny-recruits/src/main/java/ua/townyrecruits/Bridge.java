package ua.townyrecruits;

import com.talhanation.recruits.*;
import com.talhanation.recruits.entities.*;
import com.talhanation.recruits.world.*;
import com.talhanation.recruits.config.RecruitsServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.*;
import net.minecraft.core.BlockPos;
import java.util.*;

public final class Bridge {
    public static Bridge INSTANCE;
    final MinecraftServer server;
    public final ArmyData data;
    final Set<AbstractRecruitEntity> loaded=Collections.newSetFromMap(new IdentityHashMap<>());
    public Policy.Snapshot snapshot=Policy.Snapshot.empty();
    TownyAccess towny;
    public boolean ready,dirty=true;
    boolean indexOk=false,censusAttempted=false;
    long ticks;
    Bridge(MinecraftServer server) {
        this.server=server;data=ArmyData.get(server.m_129783_());
        for(ServerLevel level:server.m_129785_())for(Entity entity:level.m_8583_())if(entity instanceof AbstractRecruitEntity r)loaded.add(r);
    }
    void tick() {
        ticks++;
        if(dirty || ticks%40==0) {
            dirty=false;
            try {
                if(towny==null)towny=new TownyAccess(()->{dirty=true;refreshNow();});
                refreshNow();
            } catch(Exception e) {unavailable(e);}
        }
        if(!ready)return;
        if(ticks%40==0){boolean changed=false;for(var n:snapshot.nations().values()){
            var f=FactionEvents.recruitsFactionManager.getFactionByStringID(Policy.team(n.id()));
            if(f!=null && f.getNPCs()!=count(n.id())){f.setNPCs(count(n.id()));changed=true;}
        }if(changed)FactionEvents.recruitsFactionManager.save(server.m_129783_());}
        // Stagger work across all 40 ticks; no whole-world entity scans per tick.
        for(AbstractRecruitEntity r:List.copyOf(loaded)){
            if(r.m_213877_()){loaded.remove(r);continue;}
            if(Math.floorMod(r.m_19879_(),40)!=ticks%40)continue;
            updateUnit(r);
            if(r.getIsOwned()) Supplies.tick(this,r);
        }
    }
    void unavailable(Exception e){
        if(ready || ticks==1 || ticks%1200==0)TownyRecruits.LOG.warn("Towny unavailable; hiring, army combat and chest access blocked: {}",e.toString());
        ready=false;if(towny!=null){towny.close();towny=null;}
    }
    void refreshNow() {
        if(towny==null)return;
        try{
            if(!censusAttempted){
                censusAttempted=true;
                try{var roots=towny.worldRoots();roots.add(server.m_129843_(net.minecraft.world.level.storage.LevelResource.f_78182_));data.indexWorlds(roots);indexOk=true;}
                catch(Exception e){indexOk=false;TownyRecruits.LOG.error("Entity census failed. Hiring is disabled; fix the region and restart.",e);}
            }
            Policy.Snapshot next=towny.snapshot();
            boolean changed=!next.equals(snapshot) || !ready;
            snapshot=next;ready=true;
            if(ticks%40==0)towny.claims.clear();
            if(changed){data.reconcile(next);syncFactions();for(var r:List.copyOf(loaded))updateUnit(r);}
        }catch(Exception e){unavailable(e);}
    }
    public void dataRemove(AbstractRecruitEntity r){if(!r.getIsOwned())data.remove(r.m_20148_());}
    public int limit(UUID nation){Policy.Nation n=snapshot.nations().get(nation);return n==null?0:Policy.limit(n.towns(),data.overrides.get(nation));}
    public int count(UUID nation){return data.counts.getOrDefault(nation,0);}
    public UUID nation(Entity e){
        if(e instanceof Player)return snapshot.residents().get(e.m_20148_());
        if(e instanceof AbstractRecruitEntity r){ArmyData.Unit u=data.units.get(r.m_20148_());return u==null||u.retired()?null:u.nation();}
        return null;
    }
    public static Boolean combat(LivingEntity a,LivingEntity b){
        Bridge s=INSTANCE;
        if(s==null || !(a instanceof AbstractRecruitEntity || b instanceof AbstractRecruitEntity))return null;
        if(!s.ready)return false;
        if(!b.m_6084_() || b instanceof Player p && (p.m_7500_() || p.m_5833_()))return false;
        if((a instanceof Player||a instanceof AbstractRecruitEntity) && (b instanceof Player||b instanceof AbstractRecruitEntity)) {
            if(a instanceof AbstractRecruitEntity r && !r.getIsOwned() && s.nation(a)==null)return null;
            return Policy.relation(s.snapshot,s.nation(a),s.nation(b))<0;
        }
        return null;
    }
    public boolean canHire(Player player,AbstractRecruitEntity r,boolean notify){
        UUID n=snapshot.residents().get(player.m_20148_());
        boolean ok=ready && indexOk && !r.getIsOwned() && n!=null && count(n)<limit(n);
        if(!ok && notify)player.m_213846_(Component.m_237113_("TownyRecruits: потрібна нація та вільний ліміт армії. "+(n==null?"":count(n)+"/"+limit(n))));
        return ok;
    }
    public void hired(Player p,AbstractRecruitEntity r){
        UUID n=snapshot.residents().get(p.m_20148_());if(n==null)return;
        data.put(r.m_20148_(),new ArmyData.Unit(p.m_20148_(),n,false));loaded.add(r);updateUnit(r);
    }
    public void updateUnit(AbstractRecruitEntity r){
        if(!ready)return;
        if(!r.getIsOwned()){data.remove(r.m_20148_());return;}
        ArmyData.Unit u=data.units.get(r.m_20148_());
        if(u==null){UUID owner=r.getOwnerUUID();if(owner==null)return;UUID n=snapshot.residents().get(owner);u=new ArmyData.Unit(owner,n,n==null);data.put(r.m_20148_(),u);}
        if(u.retired() || !snapshot.nations().containsKey(u.nation()) || !Objects.equals(u.nation(),snapshot.residents().get(r.getOwnerUUID()))){
            r.disband(null,false,false);Team old=r.m_5647_();if(old!=null)server.m_129896_().m_6519_(r.m_20149_(),(PlayerTeam)old);
            r.setAggroState(3);r.m_6710_(null);data.remove(r.m_20148_());return;
        }
        PlayerTeam t=server.m_129896_().m_83489_(Policy.team(u.nation()));
        if(t!=null && r.m_5647_()!=t)server.m_129896_().m_6546_(r.m_20149_(),t);
        r.m_20340_(false);r.paymentTimer=1; // Salary is also unconditionally disabled by Mixin.
        LivingEntity target=r.m_5448_();if(target!=null && Boolean.FALSE.equals(combat(r,target)))r.m_6710_(null);
    }
    public boolean allowed(AbstractRecruitEntity r,BlockPos pos){
        if(!ready || !(r.m_20193_() instanceof ServerLevel l))return false;
        try{return towny.allowed(l,pos,nation(r));}catch(Exception e){unavailable(e);return false;}
    }
    void syncFactions(){
        var manager=FactionEvents.recruitsFactionManager;if(manager==null){ready=false;dirty=true;return;}
        ServerLevel level=server.m_129783_();ServerScoreboard board=server.m_129896_();
        Set<String> desired=new HashSet<>();for(UUID id:snapshot.nations().keySet())desired.add(Policy.team(id));
        for(RecruitsFaction f:new ArrayList<>(manager.getFactions()))if(f.getStringID().startsWith("tr_")&&!desired.contains(f.getStringID())){
            String id=f.getStringID();manager.removeTeam(id);PlayerTeam t=board.m_83489_(id);if(t!=null)board.m_83475_(t);
            var dip=FactionEvents.recruitsDiplomacyManager;
            for(String key:new ArrayList<>(FactionEvents.recruitsTreatyManager.getTreaties().keySet())){
                String[] pair=key.split("\\|");if(pair.length==2 && (pair[0].equals(id)||pair[1].equals(id)))FactionEvents.recruitsTreatyManager.removeTreaty(pair[0],pair[1],level);
            }
            dip.diplomacyMap.remove(id);dip.diplomacyMap.values().forEach(x->x.remove(id));
            dip.embargoMap.replaceAll((k,v)->String.join(",",Arrays.stream(v.split(",")).filter(x->!x.equals(id)).toList()));dip.embargoMap.values().removeIf(String::isBlank);
        }
        // Remove managed membership even for offline players; retain unrelated scoreboard entries.
        for(PlayerTeam t:board.m_83491_())if(t.m_5758_().startsWith("tr_")){
            Set<String> wanted=new HashSet<>();for(var n:snapshot.nations().values())if(Policy.team(n.id()).equals(t.m_5758_()))wanted.addAll(n.members().values());
            for(String entry:new ArrayList<>(t.m_6809_()))if(!wanted.contains(entry) && !isEntityUuid(entry))board.m_6519_(entry,t);
        }
        for(Policy.Nation n:snapshot.nations().values()){
            String id=Policy.team(n.id());PlayerTeam team=board.m_83489_(id);if(team==null)team=board.m_83492_(id);
            team.m_83353_(Component.m_237113_(n.name()));team.m_83355_(false);team.m_83346_(Team.Visibility.ALWAYS);
            RecruitsFaction f=manager.getFactionByStringID(id);
            if(f==null){manager.addTeam(id,n.name(),n.king(),n.kingName(),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.f_42672_).m_41739_(new CompoundTag()),(byte)0,ChatFormatting.WHITE);f=manager.getFactionByStringID(id);}
            f.setTeamDisplayName(n.name());f.setTeamLeaderID(n.king());f.setTeamLeaderName(n.kingName());f.setMaxPlayers(0);f.setMaxNPCs(limit(n.id()));f.setMaxNPCsPerPlayer(Integer.MAX_VALUE);f.setNPCs(count(n.id()));
            f.members.clear();for(var member:n.members().entrySet()){f.addMember(member.getKey(),member.getValue());board.m_6546_(member.getValue(),team);}f.setPlayers(n.members().size());
        }
        var dip=FactionEvents.recruitsDiplomacyManager;
        for(UUID a:snapshot.nations().keySet())for(UUID b:snapshot.nations().keySet())if(!a.equals(b)){
            int rel=Policy.relation(snapshot,a,b);dip.diplomacyMap.computeIfAbsent(Policy.team(a),k->new HashMap<>()).put(Policy.team(b),rel<0?RecruitsDiplomacyManager.DiplomacyStatus.ENEMY:rel>0?RecruitsDiplomacyManager.DiplomacyStatus.ALLY:RecruitsDiplomacyManager.DiplomacyStatus.NEUTRAL);
        }
        // Replace SavedData maps, including deletions (upstream save alone only merges).
        RecruitsDiplomacySaveData saved=RecruitsDiplomacySaveData.get(level);
        if(saved.getDiplomacyMap()!=dip.diplomacyMap){saved.getDiplomacyMap().clear();dip.diplomacyMap.forEach((k,v)->saved.getDiplomacyMap().put(k,new HashMap<>(v)));}
        saved.setEmbargoMap(new HashMap<>(dip.embargoMap));saved.m_77762_();dip.broadcastDiplomacyMapToAll(level);
        manager.save(level);
    }
    static boolean isEntityUuid(String s){try{UUID.fromString(s);return true;}catch(IllegalArgumentException e){return false;}}
    void close(){if(towny!=null)towny.close();loaded.clear();ready=false;}
}
