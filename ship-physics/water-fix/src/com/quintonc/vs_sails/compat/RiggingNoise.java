package com.quintonc.vs_sails.compat;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
/** Water/air neighbor notices do not change sails or displacement solids. Flood topology has its own change hook. */
public final class RiggingNoise {
    private RiggingNoise() {}
    public static boolean irrelevant(BlockState state) { return state.isAir() || state.getBlock() instanceof LiquidBlock; }
}
