package ua.empirecraft.forging.mixin;

import net.minecraft.world.item.ItemStack;
import net.stirdrem.overgeared.util.QualityHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.forging.*;

@Mixin(value = QualityHelper.class, remap = false)
public abstract class QualityDurabilityMixin {
    @Inject(method = "getDurabilityMultiplier", at = @At("HEAD"), cancellable = true)
    private static void empirecraft$equipmentDurability(ItemStack stack, CallbackInfoReturnable<Float> ci) {
        if (EquipmentQuality.equipment(stack)) ci.setReturnValue(QualityStats.durability(EquipmentQuality.quality(stack)));
    }
}
