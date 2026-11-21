package ua.empirecraft.siegeworks.vs.mixin;
import java.util.function.Predicate;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import me.mss1r.siegeworks.entity.base.SiegeProjectile;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.empirecraft.siegeworks.vs.ShipRaycast;
@Mixin(targets="net.minecraft.world.entity.projectile.ThrowableProjectile",remap=false)public abstract class ProjectileClip {
 @WrapOperation(method="m_8119_()V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/ProjectileUtil;m_278158_(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"))
 private HitResult clip(Entity self,Predicate<Entity> target,Operation<HitResult> original){
  HitResult old=original.call(self,target);if(!(self instanceof SiegeProjectile))return old;
  var hit=ShipRaycast.first(self.level(),self,self.position(),self.position().add(self.getDeltaMovement()),true,s->false);
  return hit!=null&&(old.getType()==HitResult.Type.MISS||self.position().distanceToSqr(hit.location())<self.position().distanceToSqr(old.getLocation())+1e-8)?hit.result():old;
 }
}
