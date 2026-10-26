package ua.empirecraft.forging.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.stirdrem.overgeared.screen.FletchingStationMenu;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = FletchingStationMenu.class, remap = false)
public abstract class FletchingPotionMixin {
    @Shadow @Final private Container input;
    @Shadow @Final private ResultContainer result;
    @Inject(method = "isPotion", at = @At("HEAD"), cancellable = true)
    private void empirecraft$rejectPotion(ItemStack stack, CallbackInfoReturnable<Boolean> ci) { ci.setReturnValue(false); }
    @Inject(method = "updateResultSlot", at = @At("HEAD"), cancellable = true)
    private void empirecraft$noPotionOutput(CallbackInfo ci) {
        if (!input.m_8020_(3).m_41619_()) {
            result.m_6836_(4, ItemStack.f_41583_);
            ((AbstractContainerMenu)(Object)this).m_38946_();
            ci.cancel();
        }
    }
}
