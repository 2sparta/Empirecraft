package ua.empirecraft.interactions.mixin;

import net.dries007.tfc.common.items.GlassBottleItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.interactions.Glassware;

@Mixin(value = GlassBottleItem.class, remap = false)
public abstract class GlassBottleDrinkMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
        target = "Lnet/dries007/tfc/common/items/FluidContainerItem;<init>(Lnet/minecraft/world/item/Item$Properties;Ljava/util/function/Supplier;Lnet/minecraft/tags/TagKey;ZZ)V"),
        index = 2)
    private static TagKey<Fluid> empirecraft$glassFluids(TagKey<Fluid> original) {
        return Glassware.BOTTLE_FLUIDS;
    }

    @Inject(method = "afterEmptyFailed", at = @At("HEAD"), cancellable = true)
    private void empirecraft$startDrink(IFluidHandler handler, Level level, Player player, ItemStack stack,
                                       InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> result) {
        var fluid = handler.getFluidInTank(0);
        if (Glassware.isKettleDrink(fluid) && !player.isCrouching()) {
            result.setReturnValue(Glassware.fullServing(level, fluid)
                ? ItemUtils.startUsingInstantly(level, player, hand) : InteractionResultHolder.fail(stack));
        }
    }

    @Inject(method = "m_5922_", at = @At("HEAD"), cancellable = true)
    private void empirecraft$finishDrink(ItemStack stack, Level level, LivingEntity entity,
                                        CallbackInfoReturnable<ItemStack> result) {
        if (Glassware.drink(stack, level, entity)) result.setReturnValue(stack);
    }
}
