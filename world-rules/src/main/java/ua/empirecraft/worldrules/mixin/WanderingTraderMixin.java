package ua.empirecraft.worldrules.mixin;

import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.worldrules.TradePolicy;

@Mixin(value=WanderingTrader.class,remap=false)
public abstract class WanderingTraderMixin {
    @Inject(method="m_8099_()V",at=@At("TAIL"))
    private void empirecraft$noNightPotion(CallbackInfo ci) {
        var trader=(WanderingTrader)(Object)this;
        trader.f_21345_.m_262460_(goal->goal instanceof UseItemGoal<?> && ((UseItemGoalAccess)goal).empirecraft$useItem().m_41720_() instanceof PotionItem);
    }
    @Inject(method="m_7378_(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("TAIL"))
    private void empirecraft$clearOldBottle(CompoundTag data,CallbackInfo ci) {
        var trader=(WanderingTrader)(Object)this;
        if(trader.m_21205_().m_41720_() instanceof PotionItem) {
            trader.m_5810_(); trader.m_8061_(EquipmentSlot.MAINHAND,ItemStack.f_41583_);
        }
    }
    @Redirect(method="m_7604_()V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/npc/VillagerTrades$ItemListing;m_213663_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/trading/MerchantOffer;"))
    private MerchantOffer empirecraft$validRareTrade(VillagerTrades.ItemListing listing,Entity trader,RandomSource random) {
        return TradePolicy.offer(listing,trader,random);
    }
}
