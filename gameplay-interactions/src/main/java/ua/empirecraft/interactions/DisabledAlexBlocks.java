package ua.empirecraft.interactions;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.HashSet;
import java.util.Set;

public final class DisabledAlexBlocks {
    private static final Set<String> IDS = Set.of("transmutation_table", "ender_residue", "sculk_boomer");
    private static volatile Set<Block> disabled = Set.of();
    private DisabledAlexBlocks() {}

    static void load() {
        var blocks = new HashSet<Block>();
        for (String name : IDS) {
            var key = new ResourceLocation("alexsmobs", name);
            if (ForgeRegistries.BLOCKS.containsKey(key)) blocks.add(ForgeRegistries.BLOCKS.getValue(key));
        }
        disabled = Set.copyOf(blocks);
    }

    public static boolean contains(BlockState state) { return disabled.contains(state.getBlock()); }
}
