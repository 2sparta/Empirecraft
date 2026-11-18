package ua.empirecraft.interactions.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.interactions.DisabledAlexBlocks;
import ua.empirecraft.interactions.FireRules;

@Mixin(value = Level.class, remap = false)
public abstract class WorldPlacementMixin {
    @Inject(method = "m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"), cancellable = true)
    private void empirecraft$placement(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> result) {
        Level level = (Level) (Object) this;
        if (!FireRules.canStart(level, pos, state) ||
            DisabledAlexBlocks.contains(state) && level.getBlockState(pos).getBlock() != state.getBlock()) {
            result.setReturnValue(false);
        }
    }
}
