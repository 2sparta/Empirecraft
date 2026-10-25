package ua.empirecraft.content.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.ninni.spawn.server.block.UnstableHeliostoneBlock",remap=false)
public abstract class UnstableMixin {
    @Inject(method={"m_213897_","m_214162_","m_142525_","m_48792_","m_141947_"},at=@At("HEAD"),cancellable=true)
    private void empirecraft$inert(CallbackInfo ci){ci.cancel();}
}
