package ua.empirecraft.content.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.ninni.spawn.server.block.HeliostoneBlock",remap=false)
public abstract class HotStoneMixin {
    @Inject(method="m_141947_",at=@At("HEAD"),cancellable=true)
    private void empirecraft$inert(CallbackInfo ci){ci.cancel();}
}
