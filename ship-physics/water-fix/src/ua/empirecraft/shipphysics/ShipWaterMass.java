package ua.empirecraft.shipphysics;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
/** Finite ship water already contributes its exact volume through Sails' FloodLoad.
 * Native fluid voxel types remain unchanged: only the duplicate constant-per-cell mass is removed. */
public final class ShipWaterMass {
    private ShipWaterMass() {}
    public static boolean water(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER);
    }
    public static boolean masslessChange(BlockState before, BlockState after, double oldMass, double newMass) {
        return oldMass == 0 && newMass == 0 && (before.isAir() || water(before)) && (after.isAir() || water(after));
    }
}
