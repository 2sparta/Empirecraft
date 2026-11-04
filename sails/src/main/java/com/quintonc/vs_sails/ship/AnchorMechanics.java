package com.quintonc.vs_sails.ship;

import com.quintonc.vs_sails.blocks.AnchorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.quintonc.vs_sails.entity.AnchorEntities;
import com.quintonc.vs_sails.entity.AnchorVisualEntity;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public final class AnchorMechanics {
    private AnchorMechanics() {}

    public static void use(ServerLevel level, BlockPos capstan, Player player) {
        LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(level, capstan);
        if (ship == null) { message(player, "vs_sails.anchor.requires_ship"); return; }
        SailsShipControl control = SailsShipControl.getOrCreate(ship, level);
        if (control.anchorDeployed && !ownsAnchor(control,level)) forgetForeignAnchor(control);
        if (control.anchorDeployed) {
            if (control.anchorCapstan != capstan.asLong()) { message(player, "vs_sails.anchor.other_capstan"); return; }
            if (player.isShiftKeyDown()) { control.raiseAnchor(); message(player, "vs_sails.anchor.raising"); }
            else message(player, control.anchorRaising ? "vs_sails.anchor.raising" : control.anchorLocked ? "vs_sails.anchor.held" : "vs_sails.anchor.braking");
            return;
        }
        if (player.isShiftKeyDown()) { message(player, "vs_sails.anchor.raised"); return; }
        BlockPos anchor = null;
        for (int down = 1; down <= 32; down++) {
            BlockPos test = capstan.below(down);
            if (!level.hasChunkAt(test) || !control.ownsPosition(level,test)) break;
            if (level.getBlockState(test).getBlock() instanceof AnchorBlock) { anchor = test; break; }
        }
        if (anchor == null) { message(player, "vs_sails.anchor.missing_anchor"); return; }
        LoadedServerShip anchorShip = VSGameUtilsKt.getShipObjectManagingPos(level, anchor);
        if (anchorShip == null || anchorShip.getId() != ship.getId()) return;
        Vector3d point = ship.getTransform().getShipToWorld().transformPosition(new Vector3d(anchor.getX()+.5, anchor.getY(), anchor.getZ()+.5));
        BlockPos start = BlockPos.containing(point.x, point.y, point.z);
        boolean water = false;
        for (int down = 0; down <= 64; down++) {
            BlockPos test = start.below(down);
            if (test.getY() < level.getMinBuildHeight() || !level.hasChunkAt(test)) break;
            if (level.getFluidState(test).is(FluidTags.WATER)) water = true;
            if (!level.getBlockState(test).getCollisionShape(level, test).isEmpty()) {
                if (!water) break;
                Vector3d bottom = new Vector3d(test.getX()+.5, test.getY()+1.0, test.getZ()+.5);
                double length = point.distance(bottom);
                if (length > 64) break;
                control.anchorCapstan = capstan.asLong();
                control.anchorBlock = anchor.asLong();
                control.anchorX = bottom.x; control.anchorY = bottom.y; control.anchorZ = bottom.z;
                control.anchorLength = length;
                control.anchorDimension = ship.getChunkClaimDimension();
                control.anchorRaising = false;
                control.anchorProgress = 0;
                control.anchorWasStatic = ship.isStatic();
                control.anchorLocked = false;
                control.anchorReadyToHold = false;
                control.anchorDeployed = true;
                level.setBlockAndUpdate(anchor, level.getBlockState(anchor).setValue(AnchorBlock.DEPLOYED, true));
                message(player, "vs_sails.anchor.lowering");
                return;
            }
        }
        message(player, "vs_sails.anchor.no_bottom");
    }

    public static void tick(SailsShipControl control, ServerLevel level) {
        if (!control.anchorDeployed) {
            if (control.anchorLocked) release(control, true);
            return;
        }
        BlockPos capstan = BlockPos.of(control.anchorCapstan), anchor = BlockPos.of(control.anchorBlock);
        if (!ownsAnchor(control,level)) { forgetForeignAnchor(control); return; }
        // Only a verified, owned legacy anchor may release its former static hold.
        if(control.anchorLocked){
            if(!control.anchorWasStatic && !control.sailSafetyHeld && !control.motionSafetyTripped)control.ship.setStatic(false);
            control.anchorLocked=false;
        }
        if (!control.ship.getChunkClaimDimension().equals(control.anchorDimension)
                || !Double.isFinite(control.anchorLength) || control.anchorLength < 0 || control.anchorLength > 64
                || !Double.isFinite(control.anchorProgress) || !Double.isFinite(control.anchorX)
                || !Double.isFinite(control.anchorY) || !Double.isFinite(control.anchorZ)) { release(control, true); return; }
        if (!level.hasChunkAt(capstan) || !level.hasChunkAt(anchor)) { removeVisual(control); return; }
        if (!(level.getBlockState(capstan).getBlock() instanceof com.quintonc.vs_sails.blocks.CapstanBlock)
                || !(level.getBlockState(anchor).getBlock() instanceof AnchorBlock)) { release(control, true); return; }
        BlockState state = level.getBlockState(anchor);
        if (!state.getValue(AnchorBlock.DEPLOYED)) level.setBlockAndUpdate(anchor, state.setValue(AnchorBlock.DEPLOYED, true));
        Vector3d start = control.ship.getTransform().getShipToWorld().transformPosition(new Vector3d(anchor.getX()+.5, anchor.getY(), anchor.getZ()+.5));
        Vector3d capstanPoint = control.ship.getTransform().getShipToWorld().transformPosition(new Vector3d(capstan.getX()+.5, capstan.getY()+.05, capstan.getZ()+.5));
        Vector3d target = new Vector3d(control.anchorX, control.anchorY, control.anchorZ);
        if (capstanPoint.distance(target) > 80) { release(control, true); return; }
        double step = .35 / Math.max(1, control.anchorLength);
        control.anchorProgress = Math.max(0, Math.min(1, control.anchorProgress + (control.anchorRaising ? -step : step)));
        if (control.anchorRaising && control.anchorProgress <= 0) { release(control, true); return; }
        control.publishAnchorTether();
        Vector3d rendered = start.lerp(target, control.anchorProgress, new Vector3d());
        if (control.anchorVisual == null || control.anchorVisual.isRemoved() || control.anchorVisual.level() != level) {
            removeVisual(control);
            AnchorVisualEntity visual = AnchorEntities.TYPE.create(level);
            if (visual == null) return;
            visual.update(control.ship.getId(), control.anchorCapstan, new Vec3(capstanPoint.x, capstanPoint.y, capstanPoint.z),
                    new Vec3(rendered.x, rendered.y, rendered.z), state.setValue(AnchorBlock.DEPLOYED, false));
            if (level.addFreshEntity(visual)) control.anchorVisual = visual;
        } else control.anchorVisual.update(control.ship.getId(), control.anchorCapstan,
                new Vec3(capstanPoint.x, capstanPoint.y, capstanPoint.z), new Vec3(rendered.x, rendered.y, rendered.z), state.setValue(AnchorBlock.DEPLOYED, false));
    }

    private static boolean ownsAnchor(SailsShipControl control,ServerLevel level) {
        return control.owns(control.ship) && control.ownsPosition(level,BlockPos.of(control.anchorCapstan))
                && control.ownsPosition(level,BlockPos.of(control.anchorBlock));
    }

    /** Copied/stale anchors must not unfreeze the destination ship or edit the source anchor. */
    public static void forgetForeignAnchor(SailsShipControl control) {
        control.clearAnchorTether();
        control.anchorDeployed=false; control.anchorRaising=false; control.anchorLocked=false;
        control.anchorReadyToHold=false; control.anchorProgress=0; removeVisual(control);
    }

    public static void raise(SailsShipControl control) {
        if (!control.anchorDeployed) return;
        unlock(control);
        control.anchorRaising = true;
        control.anchorReadyToHold = false;
        control.clearAnchorTether();
    }

    public static void release(SailsShipControl control, boolean restoreBlock) {
        control.clearAnchorTether();
        control.anchorDeployed = false;
        control.anchorRaising = false;
        control.anchorReadyToHold = false;
        unlock(control);
        removeVisual(control);
        if (restoreBlock && control.world instanceof ServerLevel level) {
            BlockPos pos = BlockPos.of(control.anchorBlock);
            if (control.ownsPosition(level,pos) && level.hasChunkAt(pos)) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof AnchorBlock && state.getValue(AnchorBlock.DEPLOYED))
                    level.setBlockAndUpdate(pos, state.setValue(AnchorBlock.DEPLOYED, false));
            }
        }
        control.anchorProgress = 0;
    }

    private static void unlock(SailsShipControl control) {
        if (control.anchorLocked && !control.anchorWasStatic && !control.sailSafetyHeld && !control.motionSafetyTripped && control.owns(control.ship)) control.ship.setStatic(false);
        control.anchorLocked = false;
    }

    private static void removeVisual(SailsShipControl control) {
        if (control.anchorVisual != null) { control.anchorVisual.discard(); control.anchorVisual = null; }
    }

    private static void message(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
