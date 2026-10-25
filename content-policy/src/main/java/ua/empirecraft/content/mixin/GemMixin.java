package ua.empirecraft.content.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.ninni.spawn.server.block.DaytimeGemBlock",remap=false)
public abstract class GemMixin {
    @Inject(method="m_213898_",at=@At("HEAD"),cancellable=true)
    private void empirecraft$inert(CallbackInfo ci){ci.cancel();}
}
