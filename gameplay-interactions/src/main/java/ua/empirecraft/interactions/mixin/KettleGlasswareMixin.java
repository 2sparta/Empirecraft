package ua.empirecraft.interactions.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.interactions.Glassware;
import umpaz.farmersrespite.common.block.entity.KettleBlockEntity;

@Mixin(value = KettleBlockEntity.class, remap = false)
public abstract class KettleGlasswareMixin {
    @Inject(method = "fluidExtract", at = @At("HEAD"), cancellable = true)
    private void empirecraft$transferGlass(KettleBlockEntity kettle, ItemStack input, ItemStack output,
                                         CallbackInfoReturnable<ItemStack> result) {
        if (!Glassware.bottle(input)) return;
        var served = Glassware.transfer(kettle.getLevel(), kettle.getFluidTank(), input, output);
        if (!served.isEmpty()) kettle.setChanged();
        result.setReturnValue(served);
    }
}
