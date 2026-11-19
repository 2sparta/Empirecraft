package ua.empirecraft.tfc.mixin;

import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.tfc.Chernozem;

@Mixin(value = BlockEntityType.class, remap = false)
public abstract class ChernozemFarmlandMixin {
    @Inject(method = "m_155262_", at = @At("HEAD"), cancellable = true)
    private void empirecraft$validateFarmland(BlockState state, CallbackInfoReturnable<Boolean> result) {
        if (Chernozem.isFarmland(state.getBlock()) && (Object)this == TFCBlockEntities.FARMLAND.get()) {
            result.setReturnValue(true);
        }
    }
}
