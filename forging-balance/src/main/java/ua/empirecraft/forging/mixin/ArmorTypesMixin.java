package ua.empirecraft.forging.mixin;

import net.stirdrem.overgeared.item.ToolType;
import net.stirdrem.overgeared.item.ToolTypeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ToolTypeRegistry.class, remap = false)
public abstract class ArmorTypesMixin {
    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private static void empirecraft$registerArmorTypes(CallbackInfo ci) {
        // Data reload listeners are server-only; the drafting screen also needs these on clients.
        for (String type : new String[]{"armor", "helmet", "chestplate", "leggings", "boots"}) ToolType.of(type);
    }
}
