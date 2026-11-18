package ua.empirecraft.interactions.mixin;

import net.dries007.tfc.common.items.FluidContainerItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.interactions.Glassware;

@Mixin(value = FluidContainerItem.class, remap = false)
public abstract class GlasswareNameMixin {
    @Inject(method = "m_7626_", at = @At("HEAD"), cancellable = true)
    private void empirecraft$boiledName(ItemStack stack, CallbackInfoReturnable<Component> result) {
        if (Glassware.isBoiled(Glassware.contents(stack))) {
            var name = ((Item) (Object) this).getDescriptionId(stack);
            result.setReturnValue(Component.translatable(name + ".filled",
                Component.translatable("item.legendarysurvivaloverhaul.purified_water_bottle")));
        }
    }
}
