package ua.empirecraft.forging.mixin;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.stirdrem.overgeared.event.ModEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.forging.*;

@Mixin(value = ModEvents.class, remap = false)
public abstract class QualityAttributesMixin {
    @Inject(method = "onItemAttributes", at = @At("HEAD"), cancellable = true)
    private static void empirecraft$equipmentAttributes(ItemAttributeModifierEvent event, CallbackInfo ci) {
        if (EquipmentQuality.apply(event)) ci.cancel();
    }
    @Inject(method = "ARMORER_ITEMS", at = @At("RETURN"), cancellable = true)
    private static void empirecraft$noSteelTrades(CallbackInfoReturnable<List<Item>> ci) {
        ci.setReturnValue(ci.getReturnValue().stream().filter(item -> !EquipmentContent.removed(item)).toList());
    }
}
