package ua.empirecraft.tfc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** LSO supplies the temperature; its reverse TFC bridge must remain disabled. */
@Mixin(targets="sfiomn.legendarysurvivaloverhaul.common.integration.terrafirmacraft.TerraFirmaCraftUtil",remap=false)
public abstract class LsoClimateMixin {
    @Inject(method="shouldUseTerraFirmaCraftTemp",at=@At("HEAD"),cancellable=true)
    private static void empirecraft$independentClimate(CallbackInfoReturnable<Boolean> cir){cir.setReturnValue(false);}
}
