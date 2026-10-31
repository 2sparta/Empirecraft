package ua.empirecraft.recruits;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.*;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.*;

/** Only ec_* factions are managed. Missing snapshots never delete factions or their recruits. */
public final class FactionSync {
    public static final ThreadLocal<Boolean> AUTH=ThreadLocal.withInitial(()->false);
    private static int ticks;
    @SuppressWarnings("unchecked") public static void tick(){
        if(++ticks%2!=0)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server==null||FactionEvents.recruitsFactionManager==null||FactionEvents.recruitsDiplomacyManager==null)return;
        var response=ArmyHooks.call("factions",Map.of());Object rows=response.get("factions");if(!(rows instanceof List<?> list)||!response.containsKey("updated")||System.currentTimeMillis()-((Number)response.get("updated")).longValue()>5000)return;
        ServerLevel level=server.m_129783_();var board=server.m_129896_();boolean dirty=false;
        try{
            AUTH.set(true);var ids=new ArrayList<String>();
            for(Object row:list){Map<String,Object> data=(Map<String,Object>)row;String id=data.get("id").toString();if(!id.startsWith("ec_"))continue;ids.add(id);
                String name=data.get("name").toString(),leader=data.get("leader").toString(),leaderName=data.get("leaderName").toString();var manager=FactionEvents.recruitsFactionManager;var faction=manager.getFactionByStringID(id);
                if(faction==null){manager.addTeam(id,name,UUID.fromString(leader),leaderName,net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("minecraft","white_banner")).m_7968_().serializeNBT(),(byte)0,ChatFormatting.WHITE);faction=manager.getFactionByStringID(id);dirty=true;}
                if(faction==null)continue;
                if(!leader.equals(faction.getTeamLeaderUUID().toString())||!leaderName.equals(faction.getTeamLeaderName())||!name.equals(faction.getTeamDisplayName())){faction.setTeamLeaderID(UUID.fromString(leader));faction.setTeamLeaderName(leaderName);faction.setTeamDisplayName(name);dirty=true;}
                PlayerTeam team=board.m_83489_(id);if(team==null){team=board.m_83492_(id);dirty=true;}team.m_83355_(false);team.m_83362_(true);team.m_83353_(Component.m_237113_(name));
                var expected=new HashMap<UUID,String>();for(Object member:(List<?>)data.get("members")){var m=(Map<String,Object>)member;expected.put(UUID.fromString(m.get("uuid").toString()),m.get("name").toString());}
                for(var member:new ArrayList<>(faction.getMembers()))if(!expected.containsKey(member.getUUID())){faction.removeMember(member.getName());if(board.m_83500_(member.getName())==team)board.m_83495_(member.getName());dirty=true;}
                var existing=new HashSet<UUID>();for(var member:faction.getMembers())existing.add(member.getUUID());
                for(var member:expected.entrySet()){
                    if(!existing.contains(member.getKey())){faction.addMember(member.getKey(),member.getValue());dirty=true;}
                    if(board.m_83500_(member.getValue())!=team){board.m_6546_(member.getValue(),team);dirty=true;}
                }
            }
            for(String a:ids)for(String b:ids)if(!a.equals(b)){
                var result=ArmyHooks.call("relation",Map.of("a",a,"b",b));Object desired=result.get("relation");if(desired==null)continue;
                var status=RecruitsDiplomacyManager.DiplomacyStatus.valueOf(desired.toString());
                if(FactionEvents.recruitsDiplomacyManager.getRelation(a,b)!=status){FactionEvents.recruitsDiplomacyManager.setRelation(a,b,status,level,true);dirty=true;}
            }
            if(dirty){FactionEvents.recruitsFactionManager.save(level);FactionEvents.recruitsDiplomacyManager.save(level);FactionEvents.recruitsFactionManager.broadcastFactionsToAll(level);FactionEvents.recruitsFactionManager.broadcastOwnFactionToAll(level);FactionEvents.recruitsDiplomacyManager.broadcastDiplomacyMapToAll(level);}
        }finally{AUTH.remove();}
    }
    public static void unit(AbstractRecruitEntity r,String id){
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null||FactionEvents.recruitsFactionManager==null)return;PlayerTeam team=server.m_129896_().m_83489_(id);if(team==null)return;
        var old=r.m_5647_();if(old!=null&&old.m_5758_().equals(id))return;
        ServerLevel level=(ServerLevel)r.m_9236_();if(old!=null){FactionEvents.removeRecruitFromTeam(r,old,level);FactionEvents.addNPCToData(level,old.m_5758_(),-1);}
        FactionEvents.addRecruitToTeam(r,team,level);FactionEvents.addNPCToData(level,id,1);
    }
}
