package ua.empirecraft.tfc.mixin;
import net.dries007.tfc.common.blockentities.NestBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.tfc.AnimalIntegration;

@Mixin(value=NestBoxBlockEntity.class,remap=false)
public abstract class NestMixin {
    @Inject(method="hatchEggs",at=@At("HEAD"),cancellable=true)
    private void empirecraft$keepDeniedEggs(long since,CallbackInfo ci){
        AnimalIntegration.hatchNest((NestBoxBlockEntity)(Object)this);ci.cancel();
    }
}
