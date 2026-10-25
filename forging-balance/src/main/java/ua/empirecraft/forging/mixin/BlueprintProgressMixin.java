package ua.empirecraft.forging.mixin;

import net.stirdrem.overgeared.BlueprintQuality;
import net.stirdrem.overgeared.item.custom.BlueprintItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlueprintItem.class, remap = false)
public abstract class BlueprintProgressMixin {
    @Inject(method = "getUsesToNextLevel(Lnet/stirdrem/overgeared/BlueprintQuality;)I", at = @At("RETURN"), cancellable = true, require = 1)
    private static void empirecraft$slowerResearch(BlueprintQuality quality, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Math.multiplyExact(cir.getReturnValueI(), 3));
    }
}
