package com.quintonc.vs_sails.ship;

import com.quintonc.vs_sails.blocks.AnchorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public final class AnchorMechanics {
    private AnchorMechanics() {}

    public static void use(ServerLevel level, BlockPos capstan, Player player) {
        LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, capstan);
        if (ship == null) { message(player, "vs_sails.anchor.requires_ship"); return; }
        SailsShipControl control = SailsShipControl.getOrCreate(ship, level);
        if (control.anchorDeployed) {
            if (control.anchorCapstan != capstan.asLong()) { message(player, "vs_sails.anchor.other_capstan"); return; }
            if (player.isShiftKeyDown()) { control.releaseAnchor(); message(player, "vs_sails.anchor.raised"); }
            else message(player, "vs_sails.anchor.already_lowered");
            return;
        }
        BlockPos anchor = null;
        for (int down = 1; down <= 32; down++) {
            BlockPos test = capstan.below(down);
            if (!level.hasChunkAt(test)) break;
            if (level.getBlockState(test).getBlock() instanceof AnchorBlock) { anchor = test; break; }
        }
        if (anchor == null) { message(player, "vs_sails.anchor.missing_anchor"); return; }
        LoadedServerShip anchorShip = VSGameUtilsKt.getShipObjectManagingPos(level, anchor);
        if (anchorShip == null || anchorShip.getId() != ship.getId()) return;
        Vector3d point = ship.getTransform().getShipToWorld().transformPosition(new Vector3d(anchor.getX()+.5, anchor.getY()+.5, anchor.getZ()+.5));
        BlockPos start = BlockPos.containing(point.x, point.y, point.z);
        boolean water = false;
        for (int down = 0; down <= 64; down++) {
            BlockPos test = start.below(down);
            if (test.getY() < level.getMinBuildHeight() || !level.hasChunkAt(test)) break;
            if (level.getFluidState(test).is(FluidTags.WATER)) water = true;
            if (!level.getBlockState(test).getCollisionShape(level, test).isEmpty()) {
                if (!water) break;
                Vector3d bottom = new Vector3d(test.getX()+.5, test.getY()+1.0, test.getZ()+.5);
                double length = point.distance(bottom) + .5;
                if (length > 64) break;
                control.anchorCapstan = capstan.asLong();
                control.anchorBlock = anchor.asLong();
                control.anchorX = bottom.x; control.anchorY = bottom.y; control.anchorZ = bottom.z;
                control.anchorLength = length;
                control.anchorDimension = ship.getChunkClaimDimension();
                control.anchorDeployed = true;
                message(player, "vs_sails.anchor.lowered");
                return;
            }
        }
        message(player, "vs_sails.anchor.no_bottom");
    }

    private static void message(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
