package ua.empirecraft.interactions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class FireRules {
    private FireRules() {}

    public static boolean combustible(Level level, BlockPos pos, Direction face) {
        BlockState block = level.getBlockState(pos);
        return block.is(Blocks.TNT) || block.isFlammable(level, pos, face);
    }

    public static boolean supported(Level level, BlockPos pos) {
        for (Direction towardFuel : Direction.values()) {
            BlockPos fuel = pos.relative(towardFuel);
            if (level.hasChunkAt(fuel) && combustible(level, fuel, towardFuel.getOpposite())) return true;
        }
        return false;
    }

    public static boolean canStart(Level level, BlockPos pos, BlockState state) {
        return !(state.getBlock() instanceof BaseFireBlock)
            || level.getBlockState(pos).getBlock() instanceof BaseFireBlock
            || supported(level, pos);
    }
}
