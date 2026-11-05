package ua.townyrecruits.mixin;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.townyrecruits.tactics.*;

/** Covers ordinary clicks, shift-click and drag distribution using vanilla's slot limits. */
@Mixin(value = Slot.class, remap = false)
public abstract class BombSlotMixin {
    @Inject(method = "m_5857_", at = @At("HEAD"), cancellable = true)
    private void acceptBomb(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        Slot slot = (Slot)(Object)this;
        if (RecruitBombs.bomb(stack) && RecruitBombs.inventory(slot.f_40218_))
            ci.setReturnValue(slot.getSlotIndex() >= 6 && RecruitBombs.count(slot.f_40218_, slot.getSlotIndex()) == 0);
    }
    @Inject(method = "m_5866_", at = @At("HEAD"), cancellable = true)
    private void limitBomb(ItemStack stack, CallbackInfoReturnable<Integer> ci) {
        Slot slot = (Slot)(Object)this;
        if (RecruitBombs.bomb(stack) && RecruitBombs.inventory(slot.f_40218_))
            ci.setReturnValue(TacticalMath.bombRoom(RecruitBombs.count(slot.f_40218_, slot.getSlotIndex())));
    }
}
