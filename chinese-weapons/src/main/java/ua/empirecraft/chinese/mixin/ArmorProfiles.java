package ua.empirecraft.chinese.mixin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.armory.Profile;
import ua.empirecraft.chinese.ChineseRules;
@Mixin(value=Profile.class,remap=false)
public abstract class ArmorProfiles {
 @Inject(method="of",at=@At("HEAD"),cancellable=true,require=1)
 private static void chinese$profile(ItemStack stack,Recipe<?> original,CallbackInfoReturnable<Profile> cir){var p=ChineseRules.profile(stack);if(p!=null)cir.setReturnValue(p);}
}
