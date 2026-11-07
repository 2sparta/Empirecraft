package ua.empirecraft.worldrules.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.worldrules.ItemPolicy;
import ua.empirecraft.worldrules.RulesState;
import ua.empirecraft.worldrules.TradePolicy;
import ua.empirecraft.worldrules.TradeStack;

@Mixin(value=ItemStack.class,remap=false)
public abstract class ItemStackMixin {
    @Shadow private Item f_41589_;
    @Shadow private CompoundTag f_41590_;
    @Unique private void empirecraft$sanitize() {
        if(!RulesState.active || f_41589_==null)return;
        Item original=f_41589_,replacement=ItemPolicy.replacement(original);
        if(TradePolicy.retired(original,f_41590_))((TradeStack)(Object)this).empirecraft$markRetiredTradeItem();
        if(replacement!=original)((ItemStack)(Object)this).setItem(replacement);
        f_41590_=ItemPolicy.clean(original,f_41590_);
    }
    @Inject(method="m_41720_()Lnet/minecraft/world/item/Item;",at=@At("HEAD"))
    private void empirecraft$item(CallbackInfoReturnable<Item> ci) { empirecraft$sanitize(); }
    @Inject(method={"m_41783_()Lnet/minecraft/nbt/CompoundTag;","m_41784_()Lnet/minecraft/nbt/CompoundTag;"},at=@At("HEAD"))
    private void empirecraft$tag(CallbackInfoReturnable<CompoundTag> ci) { empirecraft$sanitize(); }
    @Inject(method="m_41785_()Lnet/minecraft/nbt/ListTag;",at=@At("HEAD"))
    private void empirecraft$enchantments(CallbackInfoReturnable<ListTag> ci) { empirecraft$sanitize(); }
    @Inject(method="m_41751_(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("TAIL"))
    private void empirecraft$setTag(CompoundTag tag,CallbackInfo ci) { empirecraft$sanitize(); }
    @Inject(method="m_41739_(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",at=@At("HEAD"))
    private void empirecraft$save(CompoundTag tag,CallbackInfoReturnable<CompoundTag> ci) { empirecraft$sanitize(); }
    @Inject(method="m_41663_(Lnet/minecraft/world/item/enchantment/Enchantment;I)V",at=@At("HEAD"),cancellable=true)
    private void empirecraft$noEnchant(Enchantment enchantment,int level,CallbackInfo ci) { if(RulesState.active)ci.cancel(); }
}
