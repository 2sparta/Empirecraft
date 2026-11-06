package ua.empirecraft.engagement;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import static ua.empirecraft.engagement.CombatPolicy.*;
import static ua.empirecraft.engagement.Combatants.*;

/** Immutable launch authority survives TNT conversion, child projectiles, chunk saves and delayed blasts. */
public final class ShotOrigins {
    public static final String KEY="EmpirecraftEngagementShot";
    public record Origin(ServerLevel level,Side launch,String key,String town,UUID person,UUID group,Long ship,UUID entity,long born) {
        public Party party(){
            Entity actor=entity==null?null:level.getEntity(entity);var ledger=CombatLedger.get(level.getServer());long now=EngagementRuntime.now(level);
            boolean neutralLaunch=launch.kind()==Kind.SHIP&&launch.flag()==Flag.STATE&&!launch.active()&&(launch.water()==Water.OWN||launch.water()==Water.ALLIED);
            boolean active=!neutralLaunch&&(ledger.active(key,now)||launch.active()||actor!=null&&Combatants.active(actor));
            return new Party(new Side(launch.kind(),launch.flag(),launch.water(),active,launch.ready()),key,town,person,group,ship,actor);
        }
        public CompoundTag save(){
            var t=new CompoundTag();t.putString("Kind",launch.kind().name());t.putString("Flag",launch.flag().name());t.putString("Water",launch.water().name());t.putBoolean("Active",launch.active());t.putBoolean("Ready",launch.ready());
            t.putString("Key",key);t.putString("Town",town);if(person!=null)t.putUUID("Person",person);if(group!=null)t.putUUID("Group",group);if(ship!=null)t.putLong("Ship",ship);if(entity!=null)t.putUUID("Entity",entity);t.putLong("Born",born);return t;
        }
    }
    public static final class Scope implements AutoCloseable {
        private final Origin before;
        private final Long automation;
        private Scope(Origin next,boolean launcher){before=CURRENT.get();automation=AUTOMATION.get();if(next==null)CURRENT.remove();else CURRENT.set(next);if(launcher&&next!=null&&next.ship()!=null)AUTOMATION.set(next.ship());else AUTOMATION.remove();}
        @Override public void close(){if(before==null)CURRENT.remove();else CURRENT.set(before);if(automation==null)AUTOMATION.remove();else AUTOMATION.set(automation);}
    }
    private static final ThreadLocal<Origin> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Long> AUTOMATION=new ThreadLocal<>();
    private static final Map<UUID,Entity> tracked=new HashMap<>();
    private static final Map<UUID,Vec3> positions=new HashMap<>();
    private static final Map<UUID,Integer> stopped=new HashMap<>();
    private ShotOrigins(){}
    public static Origin current(){return CURRENT.get();}
    public static Scope enter(Origin origin){return new Scope(origin,false);}
    public static Scope launcher(Origin origin){return new Scope(origin,true);}
    public static Long automationShip(){return AUTOMATION.get();}
    public static Scope enter(Entity entity){return enter(find(entity));}
    public static Origin of(Party party,ServerLevel level){return new Origin(level,party.side(),party.key(),party.town(),party.person(),party.group(),party.ship(),party.entity()==null?null:party.entity().getUUID(),EngagementRuntime.now(level));}
    public static Origin find(Entity entity){
        if(entity==null||!(entity.level() instanceof ServerLevel level))return null;
        var root=entity.getPersistentData();if(!root.contains(KEY))return null;var t=root.getCompound(KEY);
        try{return new Origin(level,new Side(Kind.valueOf(t.getString("Kind")),Flag.valueOf(t.getString("Flag")),Water.valueOf(t.getString("Water")),t.getBoolean("Active"),t.getBoolean("Ready")),t.getString("Key"),t.getString("Town"),t.hasUUID("Person")?t.getUUID("Person"):null,t.hasUUID("Group")?t.getUUID("Group"):null,t.contains("Ship")?t.getLong("Ship"):null,t.hasUUID("Entity")?t.getUUID("Entity"):null,t.getLong("Born"));}
        catch(IllegalArgumentException malformed){return null;}
    }
    public static boolean dangerous(Entity entity){
        var id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());if(id==null)return false;String path=id.getPath();
        if(Set.of("fishing_bobber","ender_pearl","eye_of_ender","experience_bottle").contains(path))return false;
        return entity instanceof Projectile||entity instanceof PrimedTnt||entity instanceof AreaEffectCloud||path.contains("bomb")||path.contains("cannon")||path.contains("grapeshot");
    }
    public static void joined(Entity entity,boolean loading){
        if(!(entity.level() instanceof ServerLevel level)||!dangerous(entity))return;
        Origin origin=find(entity);
        if(origin==null&&!loading){
            origin=current();Entity owner=entity instanceof Projectile p?p.getOwner():entity instanceof PrimedTnt t?t.getOwner():entity instanceof AreaEffectCloud c?c.getOwner():null;
            if(origin==null&&entity.getPersistentData().contains("EmpirecraftGrapeshotShip")){
                long id=entity.getPersistentData().getLong("EmpirecraftGrapeshotShip");var ship=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);
                if(ship!=null)origin=of(Combatants.ship(level,ship,owner,owner==null?null:owner.getUUID(),null),level);
            }
            if(origin==null&&owner!=null){origin=find(owner);if(origin==null&&participant(owner))origin=of(party(owner),level);}
            if(origin==null)origin=of(EngagementRuntime.environment(),level);
            entity.getPersistentData().put(KEY,origin.save());
        }
        // Older saves and unattended land cannons still need a scoped source:
        // penetration may change hull blocks without an ordinary impact event.
        if(origin==null){origin=of(EngagementRuntime.environment(),level);entity.getPersistentData().put(KEY,origin.save());}
        tracked.put(entity.getUUID(),entity);positions.put(entity.getUUID(),worldPoint(entity));
        if(!loading&&origin.launch().asset()&&!neutral(origin))EngagementRuntime.tag(origin.party(),level,true);
        if(!loading)forecast(entity,origin,40);
    }
    private static boolean neutral(Origin origin){var side=origin.launch;return side.kind()==Kind.SHIP&&side.flag()==Flag.STATE&&!side.active()&&(side.water()==Water.OWN||side.water()==Water.ALLIED);}
    public static Vec3 worldPoint(Entity entity){
        var ship=VSGameUtilsKt.getLoadedShipManagingPos(entity.level(),entity.getX(),entity.getY(),entity.getZ());return ship==null?entity.position():CombatRaycast.worldPoint(ship,entity.position());
    }
    private static Vec3 worldVelocity(Entity entity){
        var ship=VSGameUtilsKt.getLoadedShipManagingPos(entity.level(),entity.getX(),entity.getY(),entity.getZ());return ship==null?entity.getDeltaMovement():CombatRaycast.worldDirection(ship,entity.getDeltaMovement());
    }
    private static boolean segment(Entity entity,Origin origin,Vec3 from,Vec3 to,boolean enforce){
        if(!Double.isFinite(from.lengthSqr()+to.lengthSqr())||from.distanceToSqr(to)>256*256){if(enforce)entity.discard();return false;}
        var hit=CombatRaycast.firstIgnoring(entity.level(),entity,from,to,true,state->false,origin.ship());
        if(hit==null)return true;if(hit.state()==null)return false;
        if(hit.ship() instanceof org.valkyrienskies.core.api.ships.LoadedServerShip ship){
            var target=Combatants.ship((ServerLevel)entity.level(),ship,null,null,null);
            var decision=EngagementRuntime.decide(origin.party(),target,(ServerLevel)entity.level(),true);
            if(enforce&&!decision.allow())entity.discard();
        }
        return false;
    }
    private static void forecast(Entity entity,Origin origin,int ticks){
        Vec3 point=worldPoint(entity),speed=worldVelocity(entity);if(speed.lengthSqr()<1e-7)return;
        double gravity=entity instanceof PrimedTnt?.04:entity.getType().toString().contains("arrow")?.05:.03;
        for(int i=0;i<ticks&&entity.isAlive();i++){
            Vec3 next=point.add(speed);if(!segment(entity,origin,point,next,false))break;point=next;speed=speed.scale(.99).add(0,-gravity,0);
        }
    }
    public static void tick(ServerLevel level){
        int forecasts=0;
        for(var entry:List.copyOf(tracked.entrySet())){
            Entity entity=entry.getValue();if(entity.level()!=level)continue;
            Origin origin=find(entity);if(!entity.isAlive()||origin==null||EngagementRuntime.now(level)-origin.born()>72000){forget(entity.getUUID());continue;}
            Vec3 point=worldPoint(entity),previous=positions.put(entity.getUUID(),point);if(previous!=null)segment(entity,origin,previous,point,true);
            if(!entity.isAlive())continue;
            Vec3 speed=worldVelocity(entity);boolean stationary=speed.lengthSqr()<.0001&&!(entity instanceof PrimedTnt);
            int idle=stationary?stopped.merge(entity.getUUID(),1,Integer::sum):0;if(!stationary)stopped.remove(entity.getUUID());
            if(idle<20&&!neutral(origin))EngagementRuntime.tag(origin.party(),level,true);
            if(idle<20&&forecasts++<128)forecast(entity,origin,1);
        }
    }
    public static void forget(UUID id){tracked.remove(id);positions.remove(id);stopped.remove(id);}
    public static boolean incoming(ServerLevel level,org.valkyrienskies.core.api.ships.LoadedServerShip ship){
        var box=ship.getWorldAABB();if(box==null)return true;var bounds=new net.minecraft.world.phys.AABB(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()).inflate(6);
        for(Entity entity:tracked.values())if(entity.level()==level&&entity.isAlive()){
            var source=find(entity);if(source==null||Objects.equals(source.ship(),ship.getId()))continue;Vec3 position=worldPoint(entity),speed=worldVelocity(entity);
            if(speed.lengthSqr()>.0001&&bounds.clip(position,position.add(speed.scale(100))).isPresent())return true;
        }return false;
    }
    public static void clear(){tracked.clear();positions.clear();stopped.clear();CURRENT.remove();AUTOMATION.remove();}
}
