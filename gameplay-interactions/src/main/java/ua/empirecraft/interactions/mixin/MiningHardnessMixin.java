package ua.empirecraft.interactions.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.interactions.MaterialBalance;

@Mixin(value = BlockBehaviour.BlockStateBase.class, remap = false)
public abstract class MiningHardnessMixin {
    @Inject(method = "m_60800_", at = @At("RETURN"), cancellable = true)
    private void empirecraft$mining(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> result) {
        var material = MaterialBalance.of(((BlockState) (Object) this).getBlock());
        if (material != null && result.getReturnValue() >= 0) {
            result.setReturnValue(Math.max(result.getReturnValue(), material.mining()));
        }
    }
}
