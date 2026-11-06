package ua.empirecraft.engagement;

import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import static ua.empirecraft.engagement.CombatPolicy.*;
import static ua.empirecraft.engagement.Combatants.*;

public final class EngagementRuntime {
    private static long warningAt;
    private static final Map<Long,Long> crewTagged=new HashMap<>();
    private static final Map<UUID,Long> companyTagged=new HashMap<>();
    private EngagementRuntime(){}
    public static long now(ServerLevel level){return level.getServer().overworld().getGameTime();}
    public static int duration(){try{return Math.max(1,Math.min(3600,((Number)Class.forName("me.toastymop.combatlog.CombatConfig$Config").getField("combatTime").get(null)).intValue()))*20;}catch(ReflectiveOperationException|LinkageError e){return 1000;}}
    public static void playerTag(ServerPlayer player){
        if(player.isCreative()||player.isSpectator())return;
        try{Class.forName("me.toastymop.combatlog.CombatCheck").getMethod("setCombat",Player.class).invoke(null,player);}catch(ClassNotFoundException absent){/* Standalone testing without CombatLog. */}
        catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("CombatLog integration unavailable",error);}
    }
    private static void tagKey(ServerLevel level,String key,long end){
        var ledger=CombatLedger.get(level.getServer());long before=ledger.until.getOrDefault(key,0L);if(before>=end)return;ledger.until.put(key,end);ledger.setDirty();
        if(key.startsWith("P:")&&(before<=now(level)||end-before>=20)){
            var player=level.getServer().getPlayerList().getPlayer(UUID.fromString(key.substring(2)));if(player!=null)playerTag(player);
        }
    }
    public static void tag(Party party,ServerLevel level,boolean company){
        long end=now(level)+duration();if(party.ship()!=null){tagKey(level,shipKey(party.ship()),end);tagCrew(level,party.ship(),end);}
        if(party.person()!=null)tagKey(level,personKey(party.person()),end);
        if(party.entity()!=null&&unit(party.entity())!=null)tagKey(level,entityKey(party.entity().getUUID()),end);
        if(company&&party.ship()==null&&party.group()!=null){
            tagKey(level,groupKey(party.group()),end);
            long tick=now(level);
            if(!Objects.equals(companyTagged.put(party.group(),tick),tick))for(var world:level.getServer().getAllLevels())for(Entity soldier:world.getAllEntities()){
                var unit=unit(soldier);if(unit!=null&&party.group().equals(unit.group()))tagKey(level,entityKey(soldier.getUUID()),end);
            }
        }
    }
    public static void tagCrew(ServerLevel level,long ship,long end){
        long tick=now(level);if(Objects.equals(crewTagged.put(ship,tick),tick))return;
        for(var candidate:level.getServer().getAllLevels()){
            var body=org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(candidate).getLoadedShips().getById(ship);
            if(body!=null&&body.getChunkClaimDimension().equals(org.valkyrienskies.mod.common.VSGameUtilsKt.getDimensionId(candidate))){level=candidate;break;}
        }
        for(Entity entity:level.getAllEntities())if(participant(entity)){
            var physical=Deck.support(entity);if(physical==null||physical.getId()!=ship)continue;
            String key=entity instanceof ServerPlayer?personKey(entity.getUUID()):entityKey(entity.getUUID());tagKey(level,key,end);
        }
    }
    public static void inheritCompany(Entity soldier){
        if(!(soldier.level() instanceof ServerLevel level))return;var unit=unit(soldier);if(unit==null||unit.group()==null)return;
        long end=CombatLedger.get(level.getServer()).until.getOrDefault(groupKey(unit.group()),0L);
        if(end>now(level))tagKey(level,entityKey(soldier.getUUID()),end);
    }
    public static Decision decide(Party attacker,Party victim,ServerLevel level,boolean commit){
        boolean self=attacker.entity()!=null&&attacker.entity()==victim.entity()||victim.entity()==null&&attacker.ship()!=null&&attacker.ship().equals(victim.ship());Decision decision=CombatPolicy.decide(attacker.side(),victim.side(),self);
        if(commit){if(decision.tagAttacker())tag(attacker,level,true);
            // LivingAttack runs before Bukkit/Towny. Do not manufacture consent
            // for an unrelated civilian before Towny's damage decision exists.
            if(decision.tagVictim()&&(victim.side().kind()!=Kind.PERSON||victim.side().active()))tag(victim,level,true);}
        return decision;
    }
    public static void damaged(Entity victim,DamageSource source){
        if(!(victim.level() instanceof ServerLevel level))return;
        var from=damageSource(source);var to=party(victim);
        if(to.side().kind()==Kind.PERSON&&(from.side().asset()||from.side().active())&&decide(from,to,level,false).allow())tag(to,level,false);
    }
    public static boolean attack(Entity attacker,Entity victim,boolean commit){
        if(!(victim.level() instanceof ServerLevel level))return true;
        try{return decide(attacker==null?environment():party(attacker),party(victim),level,commit).allow();}
        catch(RuntimeException unavailable){warn(unavailable);return !participant(victim)&&!(attacker!=null&&participant(attacker));}
    }
    public static Party environment(){return new Party(new Side(Kind.ENVIRONMENT,Flag.NONE,Water.WILDERNESS,false,true),"","",null,null,null,null);}
    public static Party damageSource(DamageSource source){
        Entity direct=source.getDirectEntity(),owner=source.getEntity();var shot=ShotOrigins.find(direct);if(shot!=null)return shot.party();
        var scoped=ShotOrigins.current();if(scoped!=null)return scoped.party();return owner==null?environment():party(owner);
    }
    public static boolean hurt(Entity victim,DamageSource source,boolean commit){
        if(!(victim.level() instanceof ServerLevel level))return true;
        if(source.getDirectEntity()==null&&source.getEntity()==null&&ShotOrigins.current()==null&&!source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))return true;
        try{return decide(damageSource(source),party(victim),level,commit).allow();}catch(RuntimeException unavailable){warn(unavailable);return !participant(victim);}
    }
    /** A new combat override never changes the ordinary recruit diplomacy or starts fights by itself. */
    public static Boolean recruitOverride(LivingEntity a,LivingEntity b){
        if(!(a.level() instanceof ServerLevel)||!participant(a)||!participant(b))return null;
        var ua=unit(a);var ub=unit(b);UUID oa=ua==null?a.getUUID():ua.commander(),ob=ub==null?b.getUUID():ub.commander();
        if(Objects.equals(oa,ob))return null;
        if(!active(a)&&!active(b))return null;
        return attack(a,b,false);
    }
    public static void tick(ServerLevel level){
        long now=now(level);var ledger=CombatLedger.get(level.getServer());if(now%20==0)ledger.expire(now);
        NavalFlags.tick(level);
        for(Entity entity:level.getAllEntities())if(participant(entity)){
            var before=Deck.previous(entity);var aboard=Deck.support(entity);
            if(aboard!=null){
                if(before!=null&&before.ship().getId()!=aboard.getId()&&now-before.tick()<=200&&Combatants.crew(entity,before.ship())){
                    try {
                        var from=Combatants.ship(level,before.ship(),entity,entity instanceof ServerPlayer?entity.getUUID():unit(entity).commander(),unit(entity)==null?null:unit(entity).group());
                        var to=Combatants.ship(level,aboard,null,null,null);
                        if(!from.town().equals(to.town())&&!Boolean.TRUE.equals(Government.read(level,"friendly",Map.of("a",from.town(),"b",to.town())).get("friendly"))){
                            var decision=decide(from,to,level,true);if(!decision.allow()){Deck.rejectBoarding(entity,before);continue;}
                        }
                    }catch(RuntimeException unavailable){Deck.rejectBoarding(entity,before);warn(unavailable);continue;}
                }
                if(Combatants.crew(entity,aboard))Deck.remember(entity,aboard);
                long end=ledger.until.getOrDefault(shipKey(aboard.getId()),0L);if(end>now)tagKey(level,entity instanceof ServerPlayer?personKey(entity.getUUID()):entityKey(entity.getUUID()),end);
            }
        }
        ShotOrigins.tick(level);
    }
    static void warn(Throwable error){long now=System.currentTimeMillis();if(now>=warningAt){warningAt=now+30000;org.apache.logging.log4j.LogManager.getLogger("EmpirecraftEngagement").warn("Combat waits for authoritative state",error);}}
    public static void clear(){crewTagged.clear();companyTagged.clear();}
}
