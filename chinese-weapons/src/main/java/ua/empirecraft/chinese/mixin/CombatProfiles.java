package ua.empirecraft.chinese.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.chinese.ChineseRules;
@Mixin(targets="ua.empirecraft.forging.GunsmithRules",remap=false)
public abstract class CombatProfiles {
 @Inject(method="profile",at=@At("HEAD"),cancellable=true,require=1)
 private static void chinese$profile(String id,CallbackInfoReturnable<String> cir){var p=ChineseRules.combat(id);if(p!=null)cir.setReturnValue(p);}
}
