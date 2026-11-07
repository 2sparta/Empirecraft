package ua.empirecraft.worldrules.mixin;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.worldrules.TradePolicy;

@Mixin(value=AbstractVillager.class,remap=false)
public abstract class VillagerOffersMixin {
    @Inject(method="m_6616_()Lnet/minecraft/world/item/trading/MerchantOffers;",at=@At("RETURN"))
    private void empirecraft$pruneSavedOffers(CallbackInfoReturnable<MerchantOffers> result) { TradePolicy.prune(result.getReturnValue()); }
    @Redirect(method="m_35277_",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/npc/VillagerTrades$ItemListing;m_213663_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/trading/MerchantOffer;"))
    private MerchantOffer empirecraft$validGeneratedTrade(VillagerTrades.ItemListing listing,Entity trader,RandomSource random) {
        return TradePolicy.offer(listing,trader,random);
    }
}
