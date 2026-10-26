package com.quintonc.vs_sails.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType;
import org.valkyrienskies.mod.common.BlockStateInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import java.util.List;

/** VS's remass operation using only the blocks already read by the bounded server-thread scan. */
final class BuoyMassMigration {
    record BlockMass(BlockPos pos,double mass,VsiBlockType type) {}
    static void repair(ServerLevel level,LoadedServerShip ship,List<BlockMass> blocks,double expectedMass) {
        double oldMass=ship.getInertiaData().getMass();
        if (!Double.isFinite(expectedMass) || expectedMass <= 0
                || Math.abs(oldMass-expectedMass) <= Math.max(1e-6,expectedMass*1e-6)) return;
        var air=BlockStateInfo.INSTANCE.get(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        if(air==null || blocks.isEmpty())return;
        var world=VSGameUtilsKt.getShipObjectWorld(level);
        String dimension=ship.getChunkClaimDimension();VsiBlockType airType=air.getSecond();
        boolean wasStatic=ship.isStatic();ship.setStatic(true);
        try {
            // Match VS 2.4.11's own /vs remass: clear voxel types, reset inertia, then rebuild both.
            // No Minecraft blocks are changed and no extra chunks are loaded.
            for(BlockMass block:blocks)if(!block.type.equals(airType))
                world.onSetBlock(block.pos.getX(),block.pos.getY(),block.pos.getZ(),dimension,block.type,airType,0,0);
            BlockPos origin=blocks.get(0).pos;
            world.onSetBlock(origin.getX(),origin.getY(),origin.getZ(),dimension,airType,airType,oldMass,0);
            for(BlockMass block:blocks)
                world.onSetBlock(block.pos.getX(),block.pos.getY(),block.pos.getZ(),dimension,airType,block.type,0,block.mass);
            SailsShipControl.LOGGER.info("Updated saved buoy ship {} mass from {} to {} kg",ship.getId(),oldMass,expectedMass);
        } finally { ship.setStatic(wasStatic); }
    }
}
