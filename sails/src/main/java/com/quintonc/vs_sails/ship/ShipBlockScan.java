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
    private final int[] counts = new int[8];
    private int solidBlocks;
    private double expectedMass;
    private final java.util.List<BuoyMassMigration.BlockMass> blockMasses = new java.util.ArrayList<>();

    ShipBlockScan(AABBic bounds, Direction direction) {
        minX = bounds.minX(); minY = bounds.minY(); minZ = bounds.minZ();
        // VS block AABBs use exclusive maxima; the cursor visits a closed box.
        maxX = bounds.maxX() - 1; maxY = bounds.maxY() - 1; maxZ = bounds.maxZ() - 1;
        this.direction = direction;
        cursor = new SectionScanCursor(minX, minY, minZ, maxX, maxY, maxZ);
    }

    boolean matches(AABBic bounds, Direction direction) {
        return bounds != null && this.direction == direction
                && minX == bounds.minX() && minY == bounds.minY() && minZ == bounds.minZ()
                && maxX + 1 == bounds.maxX() && maxY + 1 == bounds.maxY() && maxZ + 1 == bounds.maxZ();
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
                pos.set(cursor.x,cursor.y,cursor.z);
                if (!controller.ownsPosition(server,pos)) {
                    // Never count or remass another ship's claimed storage chunks.
                    cursor.skipSection(); chunk = null; continue;
                }
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
                // Space outside this ship's bounds is air. Waiting for those neighbour chunks can
                // otherwise stall the entire recount forever when an edge sail touches a chunk border.
                Boolean north = occupied(server, controller, pos.north()), south = occupied(server, controller, pos.south());
                Boolean east = occupied(server, controller, pos.east()), west = occupied(server, controller, pos.west());
                if (north == null || south == null || east == null || west == null) return false;
                counts[0]++;
                boolean alongZ = (north || south) && !east && !west;
                boolean alongX = (east || west) && !north && !south;
                if (alongZ || alongX) {
                    boolean square = alongZ == (direction.getAxis() == Direction.Axis.X);
                    counts[square ? 1 : 2]++;
                }
            } else if (state.getBlock() instanceof SailBlock) counts[7]++;
            else if (state.getBlock() instanceof MagicBallastBlock) counts[4]++;
            else if (state.getBlock() instanceof BallastBlock) counts[3]++;
            else if (state.getBlock() instanceof BuoyBlock) counts[5]++;
            else if (state.getBlock() instanceof HelmBlock) counts[6]++;
            if (!state.isAir()) {
                var physics = org.valkyrienskies.mod.common.BlockStateInfo.INSTANCE.get(state);
                if (physics != null) {
                    double mass = physics.getFirst();
                    if (!Double.isFinite(mass) || mass < 0) throw new IllegalStateException("Invalid ship block mass");
                    expectedMass += mass;
                    blockMasses.add(new BuoyMassMigration.BlockMass(pos.immutable(),mass,physics.getSecond()));
                }
            }
            // VS treats solid block states as one displacement voxel; non-solid sails do not inflate this count.
            if (state.isSolid() && !(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) solidBlocks++;
            cursor.advance();
        }
        if (!cursor.isDone()) return false;
        if (!controller.owns(controller.ship)) return false;
        if (counts[5] > 0) BuoyMassMigration.repair(server,controller.ship,blockMasses,expectedMass);
        controller.publishBuoyancyVolume(solidBlocks,minX,minY,minZ,maxX+1,maxY+1,maxZ+1);
        controller.publishSailCounts(counts[0], counts[7]);
        controller.numSquareSails = counts[1];
        controller.numFnASails = counts[2];
        controller.numBallast = counts[3];
        controller.numMagicBallast = counts[4];
        controller.numBuoys = counts[5];
        controller.numHelms = counts[6];
        return true;
    }

    private Boolean occupied(ServerLevel world, SailsShipControl controller, BlockPos pos) {
        if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) return false;
        if (!controller.ownsPosition(world,pos)) return false;
        LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : !chunk.getBlockState(pos).isAir();
    }

}

