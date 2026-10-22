package ua.empirecraft.lockscompat.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.lockscompat.LockedInventory;

@Mixin(value = InvWrapper.class, remap = false)
public abstract class InvWrapperMixin {
    @Shadow public abstract Container getInv();

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void rejectInsert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> result) {
        if (LockedInventory.isLocked(getInv())) result.setReturnValue(stack);
    }

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true)
    private void rejectExtract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> result) {
        if (LockedInventory.isLocked(getInv())) result.setReturnValue(ItemStack.f_41583_);
    }

    @Inject(method = "setStackInSlot", at = @At("HEAD"), cancellable = true)
    private void rejectReplacement(int slot, ItemStack stack, CallbackInfo result) {
        if (LockedInventory.isLocked(getInv())) result.cancel();
    }
}
