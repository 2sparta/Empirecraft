package net.fretux.knockedback.mixin;
import net.fretux.knockedback.Triage;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class RecoverySprint {
 @Inject(method="m_6858_(Z)V",at=@At("HEAD"),cancellable=true,remap=false)
 private void empirecraft$blockSprint(boolean sprinting,CallbackInfo ci){if(sprinting&&Triage.blocksSprint((LivingEntity)(Object)this))ci.cancel();}
}
