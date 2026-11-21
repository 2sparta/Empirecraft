package ua.empirecraft.siegeworks.vs;
import java.util.*;
import me.mss1r.siegeworks.entity.base.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.WorldEntityHandler;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
/** At most one bounded deck query per machine per game tick. VS applies carrier motion once. */
@Mod.EventBusSubscriber(modid="siegeworks") public final class Decks {
 static final class State {long tick=Long.MIN_VALUE;Ship ship;Long previous;double yaw;}
 static final Map<Entity,State> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
 public static Ship contact(Entity e){
  var state=CACHE.computeIfAbsent(e,k->new State());long now=e.level().getGameTime();if(state.tick==now)return state.ship;state.tick=now;state.ship=null;
  if(e.isPassenger()||e.isInWater()||!VSGameUtilsKt.getShipsIntersecting(e.level(),e.getBoundingBox().inflate(.4,1,.4)).iterator().hasNext())return null;
  double w=Math.min(.7,e.getBbWidth()*.3);
  for(double[] f:new double[][]{{0,0},{w,w},{w,-w},{-w,w},{-w,-w}}){Vec3 a=e.position().add(f[0],.2,f[1]);var h=ShipRaycast.first(e.level(),e,a,a.add(0,-.85,0),false,b->false);if(h!=null&&h.state()!=null&&h.ship()!=null&&h.normal().y>.35){state.ship=h.ship();break;}}
  return state.ship;
 }
 public static void tick(AbstractSiegeEntity e){
  Ship s=contact(e);var state=CACHE.computeIfAbsent(e,k->new State());
  if(e instanceof IEntityDraggingInformationProvider p){var drag=p.getDraggingInformation();if(s!=null){drag.setLastShipStoodOn(s.getId());drag.setShouldImpulseMovement(false);}else if(state.previous!=null&&state.previous.equals(drag.getLastShipStoodOn()))drag.setLastShipStoodOn(null);}
  if(s!=null){double yaw=-Math.toDegrees(Math.atan2(s.getShipToWorld().m20(),s.getShipToWorld().m22()));if(!e.level().isClientSide&&Objects.equals(state.previous,s.getId())){float delta=net.minecraft.util.Mth.wrapDegrees((float)(yaw-state.yaw));e.setTrackedYaw(e.getTrackedYaw()+delta);}state.yaw=yaw;}
  state.previous=s==null?null:s.getId();
 }
 @SubscribeEvent(priority=EventPriority.LOWEST)public static void join(EntityJoinLevelEvent event){
  Entity e=event.getEntity();if(!(e instanceof AbstractSiegeEntity)&&!(e instanceof SiegeProjectile))return;
  var storage=VSGameUtilsKt.getShipManagingPos(e.level(),e.position());boolean converted=storage!=null;
  if(converted)WorldEntityHandler.INSTANCE.moveEntityFromShipyardToWorld(e,storage);
  if(e instanceof SiegeProjectile ball&&!e.level().isClientSide&&!event.loadedFromDisk()&&!converted&&!e.getPersistentData().getBoolean("ECShipLaunch")){
   Entity owner=ball.getOwner();if(owner instanceof AbstractSiegeEntity){Ship s=contact(owner);if(s!=null){VSGameUtilsKt.applyShipVelocity(e,s);e.getPersistentData().putBoolean("ECShipLaunch",true);}}
  }
 }
 @SubscribeEvent public static void stop(ServerStoppingEvent e){CACHE.clear();}
}
