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
    private int clothBlocks, hullBlocks;
    private final SailExposure exposure=new SailExposure();
    private double copiedBuoys;
    private final java.util.List<WaterContact.Voxel> displacementBlocks = new java.util.ArrayList<>();
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
            var materials=com.quintonc.vs_sails.compat.CopycatMaterials.parts(server,pos,state);
            java.util.List<BlockState> effective=materials.isEmpty()?java.util.List.of(state):materials.stream().map(p->p.material()).toList();
            boolean open=effective.stream().anyMatch(m->m.getBlock() instanceof SailBlock && m.getValue(SailBlock.SET));
            boolean furled=effective.stream().anyMatch(m->m.getBlock() instanceof SailBlock && !m.getValue(SailBlock.SET));
            if (open || furled) clothBlocks++;
            if (effective.stream().anyMatch(m -> !m.isAir() && !(m.getBlock() instanceof SailBlock)
                    && !(m.getBlock() instanceof SailToggleBlock) && !(m.getBlock() instanceof BaseHelmBlock)
                    && !(m.getBlock() instanceof WindFlagBlock) && !(m.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)
                    && (m.isSolid() || m.getBlock() instanceof BuoyBlock))) hullBlocks++;
            boolean wooden=effective.stream().anyMatch(ShipBlockScan::wooden);
            int along=direction.getAxis()==Direction.Axis.X?pos.getX():pos.getZ();
            int across=direction.getAxis()==Direction.Axis.X?pos.getZ():pos.getX();
            if(open||furled)exposure.addSail(along,across,pos.getY(),open);
            if(wooden)exposure.addWood(across,pos.getY());
            boolean copiedBuoy=!materials.isEmpty() && effective.stream().anyMatch(m->m.getBlock() instanceof BuoyBlock);
            double copyVolume=materials.stream().filter(p->p.material().getBlock() instanceof BuoyBlock).mapToDouble(p->p.volume()).sum();
            copiedBuoys+=copyVolume;
            if (state.getBlock() instanceof MagicBallastBlock) counts[4]++;
            else if (state.getBlock() instanceof BallastBlock) counts[3]++;
            else if (state.getBlock() instanceof BuoyBlock) counts[5]++;
            else if (state.getBlock() instanceof HelmBlock) counts[6]++;
            if (!state.isAir()) {
                var physics = org.valkyrienskies.mod.common.BlockStateInfo.INSTANCE.get(state);
                if (physics != null) {
                    double mass = com.quintonc.vs_sails.compat.CopycatMaterials.mass(server,pos,state,physics.getFirst());
                    if (!Double.isFinite(mass) || mass < 0) throw new IllegalStateException("Invalid ship block mass");
                    expectedMass += mass;
                    blockMasses.add(new BuoyMassMigration.BlockMass(pos.immutable(),mass,physics.getSecond()));
                }
            }
            // VS treats solid block states as one displacement voxel; non-solid sails do not inflate this count.
            if ((state.isSolid() || state.getBlock() instanceof BuoyBlock || copiedBuoy) && !(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) {
                solidBlocks++;
                double volume=copiedBuoy?copyVolume:state.getCollisionShape(server,pos).toAabbs().stream().mapToDouble(a->a.getXsize()*a.getYsize()*a.getZsize()).sum();
                if(volume>0)displacementBlocks.add(new WaterContact.Voxel(pos.immutable(),Math.min(1,volume)));
            }
            cursor.advance();
        }
        if (!cursor.isDone()) return false;
        if (!controller.owns(controller.ship)) return false;
        if (counts[5] > 0 || copiedBuoys > 0 || clothBlocks > 0)
            BuoyMassMigration.repair(server,controller.ship,blockMasses,expectedMass);
        controller.publishWaterBlocks(displacementBlocks,counts[5]>0 || copiedBuoys>0);
        controller.copiedBuoyVolume=copiedBuoys;
        controller.publishBuoyancyVolume(solidBlocks,minX,minY,minZ,maxX+1,maxY+1,maxZ+1);
        var rig=exposure.count();
        controller.publishSailCounts(rig.open(), rig.furled());
        controller.numSquareSails = rig.open();
        controller.numFnASails = counts[2];
        controller.numBallast = counts[3];
        controller.numMagicBallast = counts[4];
        controller.numBuoys = counts[5];
        controller.numHelms = counts[6];
        controller.publishHullSafety(clothBlocks,hullBlocks);
        return true;
    }

    private static boolean wooden(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.PLANKS)
                || state.getSoundType()==net.minecraft.world.level.block.SoundType.WOOD
                || state.getSoundType()==net.minecraft.world.level.block.SoundType.BAMBOO_WOOD
                || state.getSoundType()==net.minecraft.world.level.block.SoundType.CHERRY_WOOD;
    }
}
