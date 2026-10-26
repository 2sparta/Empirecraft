package ua.empirecraft.forging.mixin.client;

import java.util.List;
import net.stirdrem.overgeared.compat.jei.JEIOvergearedModPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = JEIOvergearedModPlugin.class, remap = false)
public abstract class PotionArrowJeiMixin {
    @Inject(method = "generatePotionConversions", at = @At("HEAD"), cancellable = true)
    private void empirecraft$noPotionConversions(CallbackInfoReturnable<List<?>> ci) { ci.setReturnValue(List.of()); }
}
