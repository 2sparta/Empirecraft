package ua.townyrecruits.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.townyrecruits.tactics.RecruitBombs;

@Mixin(targets = {"com.talhanation.recruits.inventory.RecruitInventoryMenu$1",
    "com.talhanation.recruits.inventory.RecruitInventoryMenu$2",
    "com.talhanation.recruits.inventory.RecruitInventoryMenu$3"}, remap = false)
public abstract class BombEquipmentSlotMixin {
    @Inject(method = "m_5857_", at = @At("HEAD"), cancellable = true)
    private void storageOnly(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (RecruitBombs.bomb(stack)) ci.setReturnValue(false);
    }
}
