package ua.empirecraft.earth.mixin;
import net.dries007.tfc.util.climate.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.earth.*;
@Mixin(value=Climate.class,remap=false)
public abstract class ClimateMixin {
    @Inject(method="model",at=@At("RETURN"),cancellable=true)
    private static void earth$model(Level level,CallbackInfoReturnable<ClimateModel> cir){
        if(EarthMod.enabled(level)&&!(cir.getReturnValue() instanceof EarthClimate))cir.setReturnValue(new EarthClimate(cir.getReturnValue()));
    }
}
