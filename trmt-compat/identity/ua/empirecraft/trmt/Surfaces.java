package ua.empirecraft.trmt;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla TRMT handles vanilla surfaces; regional soils are handled before its walking handler. */
public final class Surfaces {
    private Surfaces() {}
    public static Block trackedBlock(BlockState state) { return state.m_60734_(); }
    public static boolean matches(BlockState state, Block expected) { return state.m_60713_(expected); }
}
