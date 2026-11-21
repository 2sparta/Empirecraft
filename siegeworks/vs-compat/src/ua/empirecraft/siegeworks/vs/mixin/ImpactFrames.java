package ua.empirecraft.siegeworks.vs.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.mss1r.axiomata.ballistics.*;
import me.mss1r.axiomata.ballistics.profile.ProjectilePhysicsProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import ua.empirecraft.siegeworks.vs.Frames;
@Mixin(value=ImpactResolver.class,remap=false)public abstract class ImpactFrames {
 private static final String DRIVE="drive(Lnet/minecraft/server/level/ServerLevel;Lme/mss1r/axiomata/ballistics/profile/ProjectilePhysicsProfile;DLnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/player/Player;DLme/mss1r/axiomata/ballistics/ImpactSource;)Lme/mss1r/axiomata/ballistics/ImpactResults$Drive;";
 @WrapMethod(method=DRIVE)private ImpactResults.Drive drive(ServerLevel level,ProjectilePhysicsProfile p,double diameter,Vec3 entry,BlockPos first,Vec3 velocity,Player breaker,double max,ImpactSource source,Operation<ImpactResults.Drive> original){
  Ship ship=VSGameUtilsKt.getShipManagingPos(level,first);
  if(Frames.current()!=null)return original.call(level,p,diameter,entry,first,velocity,breaker,max,source);
  if(ship==null){if(source!=null&&source.direct()!=null)source.direct().getPersistentData().remove("ECImpactShip");return original.call(level,p,diameter,entry,first,velocity,breaker,max,source);}
  if(source!=null&&source.direct()!=null)source.direct().getPersistentData().putLong("ECImpactShip",ship.getId());
  ImpactResults.Drive hit;try(var frame=Frames.enter(ship)){hit=original.call(level,p,diameter,Frames.local(ship,entry),first,Frames.direction(ship,velocity,true),breaker,max,source);}
  return new ImpactResults.Drive(hit.passedThrough(),Frames.world(ship,hit.position()),hit.speed(),hit.block(),Frames.world(ship,hit.mouth()),Frames.direction(ship,hit.face(),false));
 }
 @WrapMethod(method="stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lme/mss1r/axiomata/ballistics/profile/ProjectilePhysicsProfile;DDLnet/minecraft/world/entity/player/Player;Lme/mss1r/axiomata/ballistics/ImpactSource;)V")
 private void stop(ServerLevel level,Vec3 center,Vec3 outward,Vec3 along,ProjectilePhysicsProfile p,double speed,double diameter,Player player,ImpactSource source,Operation<Void> original){
  Ship ship=source==null||source.direct()==null||!source.direct().getPersistentData().contains("ECImpactShip")?null:VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(source.direct().getPersistentData().getLong("ECImpactShip"));
  if(ship==null||Frames.current()!=null){original.call(level,center,outward,along,p,speed,diameter,player,source);return;}
  try(var frame=Frames.enter(ship)){original.call(level,Frames.local(ship,center),Frames.direction(ship,outward,true),Frames.direction(ship,along,true),p,speed,diameter,player,source);}
 }
}
