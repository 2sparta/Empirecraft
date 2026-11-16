package ua.empirecraft.armory.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.armory.OttomanCombat;
@Mixin(targets="ua.empirecraft.forging.GunsmithRules",remap=false)
public abstract class OttomanProfileMixin {
    @Inject(method="profile",at=@At("HEAD"),cancellable=true,require=1)
    private static void armory$ottomanProfile(String id,CallbackInfoReturnable<String> cir){var profile=OttomanCombat.profile(id);if(profile!=null)cir.setReturnValue(profile);}
}
