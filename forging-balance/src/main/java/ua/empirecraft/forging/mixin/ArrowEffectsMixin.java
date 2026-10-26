package ua.empirecraft.forging.mixin;

import net.stirdrem.overgeared.entity.custom.UpgradeArrowEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Also neutralizes previously fired and saved mod arrows. */
@Mixin(value = UpgradeArrowEntity.class, remap = false)
public abstract class ArrowEffectsMixin {
    @Inject(method = {"m_7761_", "makeAreaOfEffectCloud"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void empirecraft$noArrowEffects(CallbackInfo ci) { ci.cancel(); }
}
