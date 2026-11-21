package ua.empirecraft.siegeworks.vs.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import me.mss1r.siegeworks.entity.base.AbstractSiegeEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Mirror the rotation actually applied by VS, once; native aiming otherwise overwrites it. */
@Mixin(targets="org.valkyrienskies.mod.common.util.EntityDragger",remap=false)public abstract class CarrierYaw {
 @WrapOperation(method="dragEntitiesWithShips",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;m_146922_(F)V"))
 private void yaw(Entity entity,float target,Operation<Void> original){
  float delta=net.minecraft.util.Mth.wrapDegrees(target-entity.getYRot());original.call(entity,target);
  if(entity instanceof AbstractSiegeEntity engine&&!entity.level().isClientSide)engine.setTrackedYaw(engine.getTrackedYaw()+delta);
 }
}
