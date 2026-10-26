package ua.empirecraft.forging.mixin;

import net.minecraft.world.item.ItemStack;
import net.stirdrem.overgeared.recipe.FletchingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FletchingRecipe.class, remap = false)
public abstract class FletchingRecipeMixin {
    @Inject(method = {"getTippedResult", "getLingeringResult"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void empirecraft$noPotionResult(CallbackInfoReturnable<ItemStack> ci) { ci.setReturnValue(ItemStack.f_41583_); }
    @Inject(method = {"hasTippedResult", "hasLingeringResult"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void empirecraft$noPotionVariant(CallbackInfoReturnable<Boolean> ci) { ci.setReturnValue(false); }
}
