package ua.empirecraft.lockscompat.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.lockscompat.LockedInventory;

/** TFC uses its own wrapper, which does not inherit Forge's InvWrapper. */
@Mixin(targets="net.dries007.tfc.common.capabilities.InventoryWrapper",remap=false)
public abstract class TFCInventoryMixin {
    @Shadow public abstract Container container();
    @Inject(method="insertItem",at=@At("HEAD"),cancellable=true)
    private void insert(int slot,ItemStack stack,boolean simulate,CallbackInfoReturnable<ItemStack> result){
        if(LockedInventory.isLocked(container()))result.setReturnValue(stack);
    }
    @Inject(method="extractItem",at=@At("HEAD"),cancellable=true)
    private void extract(int slot,int amount,boolean simulate,CallbackInfoReturnable<ItemStack> result){
        if(LockedInventory.isLocked(container()))result.setReturnValue(ItemStack.f_41583_);
    }
    @Inject(method="setStackInSlot",at=@At("HEAD"),cancellable=true)
    private void replace(int slot,ItemStack stack,CallbackInfo result){
        if(LockedInventory.isLocked(container()))result.cancel();
    }
}
