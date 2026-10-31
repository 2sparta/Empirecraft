package ua.empirecraft.recruits;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.network.NetworkEvent;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** No Bukkit classes cross Forge's loader. Queries are immutable; mutations stay on server thread. */
public final class ArmyHooks {
    public static final String FOOD_SEQ="EmpirecraftArmyFoodSeq",MANAGED="EmpirecraftArmyManaged",GARRISON="EmpirecraftArmyEstate";
    static volatile Object port;static volatile Method method;
    public static final ThreadLocal<UUID> SENDER=new ThreadLocal<>();
    static volatile Map<String,Object> config=Map.of();
    static final Map<UUID,Map<String,Object>> units=new ConcurrentHashMap<>();
    public static synchronized void attach(Object plugin)throws Exception{
        Method m=plugin.getClass().getMethod("armyRequest",String.class,Map.class);
        @SuppressWarnings("unchecked") Map<String,Object> caps=(Map<String,Object>)m.invoke(plugin,"protocol",Map.of());
        if(!Objects.equals(caps.get("protocol"),2))throw new IllegalStateException("Army protocol 2 required");port=plugin;method=m;config=Map.copyOf(caps);
    }
    public static synchronized void detach(){port=null;method=null;config=Map.of();}
    @SuppressWarnings("unchecked") public static Map<String,Object> call(String action,Map<String,Object> values){
        try{Object p=port;Method m=method;if(p==null||m==null)return Map.of();return (Map<String,Object>)m.invoke(p,action,values);}catch(ReflectiveOperationException|RuntimeException e){return Map.of();}
    }
    public static boolean managed(AbstractRecruitEntity r){return r.getIsOwned()||r.getPersistentData().m_128471_(MANAGED);}
    public static boolean garrison(AbstractRecruitEntity r){return !r.getPersistentData().m_128461_(GARRISON).isEmpty();}
    static String owner(Entity e){if(e instanceof AbstractRecruitEntity r)return r.getOwnerUUID()==null?null:r.getOwnerUUID().toString();if(e instanceof TamableAnimal a)return a.m_21805_()==null?null:a.m_21805_().toString();return null;}
    public static Map<String,Object> pair(Entity source,LivingEntity target){
        var d=new HashMap<String,Object>();d.put("source",source.m_20148_().toString());d.put("target",target.m_20148_().toString());d.put("ownerA",owner(source));d.put("ownerB",owner(target));d.put("recruitA",source instanceof AbstractRecruitEntity);d.put("recruitB",target instanceof AbstractRecruitEntity);
        d.put("civilian",target instanceof AbstractVillager||target instanceof Animal);d.put("monster",target instanceof Enemy);
        d.put("worldA",source.m_9236_().m_46472_().m_135782_().toString());d.put("xA",source.m_20185_());d.put("zA",source.m_20189_());d.put("worldB",target.m_9236_().m_46472_().m_135782_().toString());d.put("xB",target.m_20185_());d.put("zB",target.m_20189_());return d;
    }
    public static void remember(Entity projectile,Entity source){
        if(!(source instanceof AbstractRecruitEntity recruit)||!managed(recruit)||projectile.m_9236_().m_5776_())return;var tag=projectile.getPersistentData();tag.m_128362_("ArmyShooter",source.m_20148_());tag.m_128359_("ArmyOwner",owner(source)==null?"":owner(source));tag.m_128359_("ArmyWorld",source.m_9236_().m_46472_().m_135782_().toString());tag.m_128347_("ArmyX",source.m_20185_());tag.m_128347_("ArmyZ",source.m_20189_());
    }
    public static Map<String,Object> projectileData(Entity direct,LivingEntity target){
        if(direct==null||!direct.getPersistentData().m_128403_("ArmyShooter"))return null;var tag=direct.getPersistentData();var d=new HashMap<String,Object>();d.put("source",tag.m_128342_("ArmyShooter").toString());d.put("ownerA",tag.m_128461_("ArmyOwner"));d.put("recruitA",true);d.put("target",target.m_20148_().toString());d.put("ownerB",owner(target));d.put("recruitB",target instanceof AbstractRecruitEntity);d.put("civilian",target instanceof AbstractVillager||target instanceof Animal);d.put("monster",target instanceof Enemy);d.put("worldA",tag.m_128461_("ArmyWorld"));d.put("xA",tag.m_128459_("ArmyX"));d.put("zA",tag.m_128459_("ArmyZ"));d.put("worldB",target.m_9236_().m_46472_().m_135782_().toString());d.put("xB",target.m_20185_());d.put("zB",target.m_20189_());return d;
    }
    public static boolean damageAllowed(net.minecraft.world.damagesource.DamageSource source,LivingEntity target){
        Entity actor=source.m_7639_();if(actor instanceof Projectile projectile)return projectileAllowed(projectile,target);if(actor!=null)return allows(actor,target);var saved=projectileData(source.m_7640_(),target);return saved==null||Boolean.TRUE.equals(call("policy",saved).get("allow"));
    }
    public static boolean projectileAllowed(Projectile projectile,LivingEntity target){Entity owner=projectile.m_19749_();if(owner!=null)return allows(owner,target);var data=projectileData(projectile,target);return data==null||Boolean.TRUE.equals(call("policy",data).get("allow"));}
    public static boolean relevant(Entity a,Entity b){return a instanceof AbstractRecruitEntity r&&managed(r)||b instanceof AbstractRecruitEntity rb&&managed(rb);}
    public static boolean allows(Entity source,LivingEntity target){
        if(!relevant(source,target))return true;if(source instanceof Enemy)return true;
        return Boolean.TRUE.equals(call("policy",pair(source,target)).get("allow"));
    }
    public static boolean command(UUID player,AbstractRecruitEntity unit){
        if(!managed(unit))return true;if(player==null)return false;
        // Ownership transfers take effect before the next political snapshot.
        if(!garrison(unit)&&!player.equals(unit.getOwnerUUID()))return false;
        return Boolean.TRUE.equals(call("canCommand",Map.of("actor",player.toString(),"unit",unit.m_20148_().toString())).get("allow"));
    }
    public static Map<String,Object> observe(AbstractRecruitEntity r){
        var d=new HashMap<String,Object>();d.put("unit",r.m_20148_().toString());d.put("owner",owner(r));d.put("hunger",(double)r.getHunger());var result=call("observe",d);if(!result.isEmpty()){units.put(r.m_20148_(),result);r.getPersistentData().m_128379_(MANAGED,true);Object e=result.get("estate");r.getPersistentData().m_128359_(GARRISON,e==null?"":e.toString());}
        return result;
    }
    public static Entity lookup(UUID id){var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null||id==null)return null;for(ServerLevel level:server.m_129785_()){Entity e=level.m_8791_(id);if(e!=null)return e;}return null;}
    /** Client-supplied UUIDs never substitute for the authenticated packet sender. */
    public static boolean packet(Object message,NetworkEvent.Context context){
        ServerPlayer sender=context.getSender();if(sender==null)return false;SENDER.set(sender.m_20148_());
        try{
            String type=message.getClass().getSimpleName();
            boolean factionEdit=Set.of("MessageSaveTeamSettings","MessageLeaveTeam","MessageRemoveFromTeam","MessageAddPlayerToTeam","MessageSendJoinRequestTeam","MessageChangeDiplomacyStatus","MessageAddEmbargoFaction","MessageAddEmbargo","MessageRemoveEmbargo","MessageUpdateClaim","MessageDeleteClaim","MessageSendTreaty","MessageAnswerTreaty").contains(type);
            if(factionEdit&&sender.m_5647_()!=null&&sender.m_5647_().m_5758_().startsWith("ec_"))return false;
            if(factionEdit)for(Field f:message.getClass().getDeclaredFields())if(f.getType()==String.class){f.setAccessible(true);Object value=f.get(message);if(value instanceof String text&&text.startsWith("ec_"))return false;}
            for(Field f:message.getClass().getDeclaredFields()){
                if(f.getType()!=UUID.class)continue;f.setAccessible(true);UUID value=(UUID)f.get(message);if(value==null)continue;String name=f.getName().replace("_","").toLowerCase(Locale.ROOT);
                if(Set.of("playeruuid","playerid").contains(name)&&!sender.m_20148_().equals(value))return false;
                boolean recruitField=name.equals("recruit")||name.equals("recruituuid")||name.equals("recruitid")||name.equals("uuid")&&Set.of("MessageFollowGui","MessageAggroGui","MessageMountEntityGui","MessageDismountGui","MessageClearTargetGui","MessageClearUpkeepGui","MessageRecruitGui","MessageOpenDisbandScreen","MessageOpenPromoteScreen","MessageListen","MessageDebugGui").contains(type);
                if(recruitField&&lookup(value) instanceof AbstractRecruitEntity r&&managed(r)){
                    if(!command(sender.m_20148_(),r))return false;
                    // Estate troops cannot be privately reassigned, disbanded or put into spawn eggs.
                    if(garrison(r)&&Set.of("MessageAssignRecruitToPlayer","MessageDisband","MessageWriteSpawnEgg","MessagePromoteRecruit","MessageDoPayment").contains(type))return false;
                }
            }return true;
        }catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
    public static Map<String,Object> diagnose(String unit,String target){Entity a=lookup(UUID.fromString(unit)),b=lookup(UUID.fromString(target));if(!(a instanceof AbstractRecruitEntity)||!(b instanceof LivingEntity))return Map.of("reason","Обидві сутності мають бути завантажені");return call("policy",pair(a,(LivingEntity)b));}
    public static int orders(String actorId,String estate,String order,String targetId){
        UUID actor=UUID.fromString(actorId);Entity leader=lookup(actor);if(!(leader instanceof ServerPlayer))return 0;Entity target=targetId==null?null:lookup(UUID.fromString(targetId));int count=0;
        for(var r:RecruitsIntegration.LOADED.values()){
            var state=units.get(r.m_20148_());if(state==null||!estate.equals(state.get("estate"))||!command(actor,r)||r.m_9236_()!=leader.m_9236_()||r.m_20280_(leader)>128*128)continue;
            switch(order){case "hold"->{r.m_6710_(null);r.setFollowState(2);r.setAggroState(0);}case "follow"->{r.setProtectUUID(Optional.of(actor));r.setFollowState(5);r.setAggroState(0);}case "defend"->{r.setFollowState(2);r.setAggroState(0);}case "attack"->{if(!(target instanceof LivingEntity living)||!allows(r,living))continue;r.m_6710_(living);}default->throw new IllegalArgumentException("Наказ: follow, hold, defend або attack UUID");}count++;
        }return count;
    }
    public static boolean relationManaged(String a,String b){if(a.startsWith("ec_")||b.startsWith("ec_"))return true;return Boolean.TRUE.equals(call("relation",Map.of("a",a,"b",b)).get("managed"));}
}
