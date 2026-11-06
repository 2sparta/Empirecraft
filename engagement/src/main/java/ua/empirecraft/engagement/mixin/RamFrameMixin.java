package ua.empirecraft.engagement.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.engagement.RamCombat;
@Pseudo @Mixin(targets="ua.empirecraft.shipphysics.CollisionDamage$Frame",remap=false)
public abstract class RamFrameMixin {
    @Inject(method="<init>",at=@At("RETURN")) private void capture(CallbackInfo ci){RamCombat.frameCreated(this);}
}
