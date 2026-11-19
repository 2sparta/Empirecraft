package ua.empirecraft.trmt;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Surface categories consumed only by TRMT's existing walking and erosion logic. */
public final class Surfaces {
    private Surfaces() {}

    public static Block trackedBlock(BlockState state) {
        return equivalent(state.m_60734_());
    }

    public static boolean matches(BlockState state, Block expected) {
        return state.m_60713_(expected) || equivalent(state.m_60734_()) == expected;
    }

    private static Block equivalent(Block block) {
        var id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) return block;
        String path = id.m_135815_();
        if (id.m_135827_().equals("empirecraft_tfc") && path.endsWith("/chernozem")) {
            if (path.equals("grass/chernozem")) return Blocks.f_50440_;
            if (path.equals("dirt/chernozem") || path.equals("rooted_dirt/chernozem")) return Blocks.f_50493_;
        }
        if (!id.m_135827_().equals("tfc")) return block;
        if (path.startsWith("grass/") || path.startsWith("clay_grass/")
                || path.equals("peat_grass") || path.equals("kaolin_clay_grass")) return Blocks.f_50440_;
        if (path.startsWith("dirt/") || path.startsWith("rooted_dirt/") || path.startsWith("clay/")
                || path.equals("peat") || path.endsWith("kaolin_clay")) return Blocks.f_50493_;
        if (path.startsWith("sand/")) return Blocks.f_49992_;
        return block;
    }
}
