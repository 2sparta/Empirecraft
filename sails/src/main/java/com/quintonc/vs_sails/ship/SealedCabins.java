package com.quintonc.vs_sails.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

/** Recheck topology even when the passenger stays in the same ship block after a breach. */
public final class SealedCabins {
    private SealedCabins() {}

    public static void refresh(Entity entity) {
        var level = entity.level();
        if (entity.tickCount % 5 != 0 || !ValkyrienSkies.isConnectivityEnabled(level.isClientSide)) return;
        boolean sealed = false;
        // Native connectivity knows the hull's rotated storage coordinates and its air components.
        var box = entity.getBoundingBox().inflate(.1);
        var bounds = new AABBd(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        for (Ship ship : VSGameUtilsKt.getShipObjectWorld(level).getAllShips()
                .getIntersecting(bounds, VSGameUtilsKt.getDimensionId(level))) {
            Vector3d local = ship.getWorldToShip().transformPosition(new Vector3d(entity.getX(), entity.getY(), entity.getZ()));
            BlockPos pos = BlockPos.containing(local.x, local.y, local.z);
            if (CabinAirSpace.sealed(level, ship, pos)) {
                sealed = true;
                break;
            }
        }
        ((IEntityDraggingInformationProvider)entity).vs$setInSealedArea(sealed);
    }
}
