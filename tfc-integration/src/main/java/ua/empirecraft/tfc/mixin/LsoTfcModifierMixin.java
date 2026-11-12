package ua.empirecraft.tfc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="sfiomn.legendarysurvivaloverhaul.common.integration.terrafirmacraft.TerraFirmaCraftModifier",remap=false)
public abstract class LsoTfcModifierMixin {
    @Inject(method="getWorldInfluence",at=@At("HEAD"),cancellable=true)
    private void empirecraft$noReverseBridge(CallbackInfoReturnable<Float> cir){cir.setReturnValue(0f);}
}
