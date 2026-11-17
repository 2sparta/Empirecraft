package ua.empirecraft.chinese.mixin;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.chinese.ChineseRules;
@Mixin(value=ItemStack.class,remap=false)
public abstract class Enchantments {
 @Inject(method="m_41785_",at=@At("HEAD"),cancellable=true,require=1)
 private void chinese$noMagic(CallbackInfoReturnable<ListTag> cir){if(ChineseRules.subject((ItemStack)(Object)this))cir.setReturnValue(new ListTag());}
 @Inject(method="m_41793_",at=@At("HEAD"),cancellable=true,require=1)
 private void chinese$notEnchanted(CallbackInfoReturnable<Boolean> cir){if(ChineseRules.subject((ItemStack)(Object)this))cir.setReturnValue(false);}
 @Inject(method="m_41792_",at=@At("HEAD"),cancellable=true,require=1)
 private void chinese$notEnchantable(CallbackInfoReturnable<Boolean> cir){if(ChineseRules.subject((ItemStack)(Object)this))cir.setReturnValue(false);}
}
