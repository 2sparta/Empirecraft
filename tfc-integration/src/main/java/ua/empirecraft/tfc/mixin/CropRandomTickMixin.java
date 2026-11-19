package ua.empirecraft.tfc.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.empirecraft.tfc.CropAgronomy;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class CropRandomTickMixin {
    @Redirect(method = "m_8714_", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/block/state/BlockState;m_222972_(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void empirecraft$grow(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CropAgronomy.randomTick(state, level, pos, random);
    }
}
