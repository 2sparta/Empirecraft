package ua.empirecraft.content.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.ninni.spawn.server.block.entity.HeartOfTheSunBlockEntity",remap=false)
public abstract class VolcanoMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private static void empirecraft$inert(CallbackInfo ci){ci.cancel();}
}
