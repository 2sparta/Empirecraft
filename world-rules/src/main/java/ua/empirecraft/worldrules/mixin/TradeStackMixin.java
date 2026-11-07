package ua.empirecraft.worldrules.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.worldrules.*;

@Mixin(value=ItemStack.class,priority=3500,remap=false)
public abstract class TradeStackMixin implements TradeStack {
    @Shadow private Item f_41589_;
    @Shadow private CompoundTag f_41590_;
    @Unique private boolean empirecraft$retiredTradeItem;
    @Override public boolean empirecraft$retiredTradeItem() { return empirecraft$retiredTradeItem; }
    @Override public void empirecraft$markRetiredTradeItem() { empirecraft$retiredTradeItem=true; }
    @Unique private void empirecraft$captureOrigin() {
        if(RulesState.active && !empirecraft$retiredTradeItem && TradePolicy.retired(f_41589_,f_41590_))empirecraft$retiredTradeItem=true;
    }
    @Inject(method={"m_41720_()Lnet/minecraft/world/item/Item;","m_41783_()Lnet/minecraft/nbt/CompoundTag;","m_41784_()Lnet/minecraft/nbt/CompoundTag;"},at=@At("HEAD"))
    private void empirecraft$readOrigin(CallbackInfoReturnable<?> result) { empirecraft$captureOrigin(); }
    @Inject(method="m_41751_(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("TAIL"))
    private void empirecraft$tagOrigin(CompoundTag tag,CallbackInfo ci) { empirecraft$captureOrigin(); }
    @Inject(method="m_41777_()Lnet/minecraft/world/item/ItemStack;",at=@At("RETURN"))
    private void empirecraft$copyOrigin(CallbackInfoReturnable<ItemStack> result) {
        if(empirecraft$retiredTradeItem && !result.getReturnValue().m_41619_())((TradeStack)(Object)result.getReturnValue()).empirecraft$markRetiredTradeItem();
    }
    @Inject(method="m_41663_(Lnet/minecraft/world/item/enchantment/Enchantment;I)V",at=@At("HEAD"))
    private void empirecraft$attemptedEnchant(Enchantment enchantment,int level,CallbackInfo ci) {
        if(RulesState.active)empirecraft$retiredTradeItem=true;
    }
}
