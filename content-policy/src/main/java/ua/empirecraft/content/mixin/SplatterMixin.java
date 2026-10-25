package ua.empirecraft.content.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.ninni.spawn.server.block.MagmaticSplatterBlock",remap=false)
public abstract class SplatterMixin {
    @Inject(method={"m_7892_","m_213897_"},at=@At("HEAD"),cancellable=true)
    private void empirecraft$inert(CallbackInfo ci){ci.cancel();}
}
