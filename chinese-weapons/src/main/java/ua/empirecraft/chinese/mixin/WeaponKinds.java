package ua.empirecraft.chinese.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.chinese.ChineseRules;
@Mixin(targets="ua.empirecraft.armory.OttomanCombat",remap=false)
public abstract class WeaponKinds {
 @Inject(method="weapon",at=@At("HEAD"),cancellable=true,require=1)
 private static void chinese$kind(String id,CallbackInfoReturnable<String> cir){var p=ChineseRules.weapon(id);if(p!=null)cir.setReturnValue(p);}
}
