package ua.empirecraft.siegeworks.vs.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.mss1r.axiomata.ballistics.*;
import me.mss1r.axiomata.ballistics.profile.ProjectilePhysicsProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import ua.empirecraft.siegeworks.vs.Frames;
@Mixin(value=ImpactResolver.class,remap=false)public abstract class BlastFrames {
 @WrapMethod(method="blast(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DLnet/minecraft/world/entity/player/Player;Lme/mss1r/axiomata/ballistics/ImpactSource;)V")
 private void blast(ServerLevel level,Vec3 center,Vec3 outward,double energy,Player player,ImpactSource source,Operation<Void> original){
  original.call(level,center,outward,energy,player,source);if(Frames.current()!=null||!Double.isFinite(energy)||energy<=0)return;
  int count=0;for(var ship:VSGameUtilsKt.getShipsIntersecting(level,new AABB(center,center).inflate(12))){if(++count>32)break;try(var scope=Frames.enter(ship)){original.call(level,Frames.local(ship,center),Frames.direction(ship,outward,true),energy,player,source);}}
 }
 @WrapMethod(method="ignite")private void ignite(ServerLevel level,Vec3 center,ProjectilePhysicsProfile.Fire fire,Player player,Operation<Void> original){
  original.call(level,center,fire,player);if(Frames.current()!=null||!Double.isFinite(fire.radius())||fire.radius()<=0)return;
  int count=0;for(var ship:VSGameUtilsKt.getShipsIntersecting(level,new AABB(center,center).inflate(Math.min(12,fire.radius())))){if(++count>32)break;try(var scope=Frames.enter(ship)){original.call(level,Frames.local(ship,center),fire,player);}}
 }
}
