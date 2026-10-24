package com.quintonc.vs_sails.ship;

import com.quintonc.vs_sails.blocks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.primitives.AABBic;

/** Incremental recount: never loads chunks, and publishes only a complete snapshot. */
final class ShipBlockScan {
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private int x, y, z;
    private final int[] counts = new int[7];

    ShipBlockScan(AABBic bounds) {
        minX = bounds.minX(); minY = bounds.minY(); minZ = bounds.minZ();
        maxX = bounds.maxX(); maxY = bounds.maxY(); maxZ = bounds.maxZ();
        x = minX; y = minY; z = minZ;
    }

    boolean step(Level world, SailsShipControl controller) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int budget = 0; budget < 8192 && y <= maxY; budget++) {
            pos.set(x, y, z);
            if (!world.hasChunkAt(pos)) return false;
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof SailBlock sail && state.getValue(SailBlock.SET)) {
                counts[0]++;
                char type = sail.calculateSailType(world, pos, controller);
                if (type == 's') counts[1]++;
                if (type == 'f') counts[2]++;
            } else if (state.getBlock() instanceof MagicBallastBlock) counts[4]++;
            else if (state.getBlock() instanceof BallastBlock) counts[3]++;
            else if (state.getBlock() instanceof BuoyBlock) counts[5]++;
            else if (state.getBlock() instanceof HelmBlock) counts[6]++;
            if (++x > maxX) { x = minX; if (++z > maxZ) { z = minZ; y++; } }
        }
        if (y <= maxY) return false;
        controller.numSails = counts[0];
        controller.numSquareSails = counts[1];
        controller.numFnASails = counts[2];
        controller.numBallast = counts[3];
        controller.numMagicBallast = counts[4];
        controller.numBuoys = counts[5];
        controller.numHelms = counts[6];
        return true;
    }
}
