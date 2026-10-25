package com.quintonc.vs_sails.ship;

import com.quintonc.vs_sails.blocks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.primitives.AABBic;

/** Incremental recount: never loads chunks, and publishes only a complete snapshot. */
final class ShipBlockScan {
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final SectionScanCursor cursor;
    private final Direction direction;
    private final int[] counts = new int[7];

    ShipBlockScan(AABBic bounds, Direction direction) {
        minX = bounds.minX(); minY = bounds.minY(); minZ = bounds.minZ();
        maxX = bounds.maxX(); maxY = bounds.maxY(); maxZ = bounds.maxZ();
        this.direction = direction;
        cursor = new SectionScanCursor(minX, minY, minZ, maxX, maxY, maxZ);
    }

    boolean matches(AABBic bounds, Direction direction) {
        return bounds != null && this.direction == direction
                && minX == bounds.minX() && minY == bounds.minY() && minZ == bounds.minZ()
                && maxX == bounds.maxX() && maxY == bounds.maxY() && maxZ == bounds.maxZ();
    }

    boolean step(Level world, SailsShipControl controller) {
        if (!(world instanceof ServerLevel server)) return false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Keep chunk references only for this server tick: a later step must recheck loading.
        LevelChunk chunk = null;
        int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
        for (int budget = 0; budget < 8192 && !cursor.isDone(); budget++) {
            if (chunk == null || chunkX != cursor.sectionX || chunkZ != cursor.sectionZ) {
                chunkX = cursor.sectionX; chunkZ = cursor.sectionZ;
                chunk = server.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) return false;
            }
            int index = chunk.getSectionIndex(cursor.y);
            if (index < 0 || index >= chunk.getSections().length) { cursor.skipSection(); continue; }
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir()) { cursor.skipSection(); continue; }
            pos.set(cursor.x, cursor.y, cursor.z);
            BlockState state = section.getBlockState(cursor.x & 15, cursor.y & 15, cursor.z & 15);
            if (state.getBlock() instanceof SailBlock sail && state.getValue(SailBlock.SET)) {
                // calculateSailType reads four neighbours; none may load a missing chunk.
                if (((cursor.x & 15) == 0 && server.getChunkSource().getChunkNow(chunkX - 1, chunkZ) == null)
                        || ((cursor.x & 15) == 15 && server.getChunkSource().getChunkNow(chunkX + 1, chunkZ) == null)
                        || ((cursor.z & 15) == 0 && server.getChunkSource().getChunkNow(chunkX, chunkZ - 1) == null)
                        || ((cursor.z & 15) == 15 && server.getChunkSource().getChunkNow(chunkX, chunkZ + 1) == null)) return false;
                counts[0]++;
                char type = sail.calculateSailType(world, pos, controller);
                if (type == 's') counts[1]++;
                if (type == 'f') counts[2]++;
            } else if (state.getBlock() instanceof MagicBallastBlock) counts[4]++;
            else if (state.getBlock() instanceof BallastBlock) counts[3]++;
            else if (state.getBlock() instanceof BuoyBlock) counts[5]++;
            else if (state.getBlock() instanceof HelmBlock) counts[6]++;
            cursor.advance();
        }
        if (!cursor.isDone()) return false;
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

