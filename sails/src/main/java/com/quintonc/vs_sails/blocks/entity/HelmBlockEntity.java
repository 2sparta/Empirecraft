package com.quintonc.vs_sails.blocks.entity;

import com.quintonc.vs_sails.ValkyrienSails;
import com.quintonc.vs_sails.config.ConfigUtils;
import com.quintonc.vs_sails.registration.SailsBlocks;
import com.quintonc.vs_sails.ship.SailsShipControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.api.SeatedControllingPlayer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.core.api.ships.Wing;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

public class HelmBlockEntity extends BaseHelmBlockEntity {

    public static final Logger LOGGER = LoggerFactory.getLogger("helm_entity");

    private static final double rudderarea = Double.parseDouble(ConfigUtils.config.getOrDefault("rudder-power","1.0"));

    public HelmBlockEntity(BlockPos pos, BlockState state) {
        super(ValkyrienSails.HELM_BLOCK_ENTITY, pos, state);
        wheelAngle = 360;
        maxAngle = 720;
        wheelInterval = Integer.parseInt(ConfigUtils.config.getOrDefault("wheel-interval","6"));
    }

    public static void tick(Level world, BlockPos pos, BlockState state) {
        //do seated controlling player stuff and have their impulses affect the turnval
        // changes by some amount each tick

        if (!world.isClientSide) {

            if (VSGameUtilsKt.isBlockInShipyard(world, pos)) {
                ChunkPos chunkPos = world.getChunk(pos).getPos();
                LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos((ServerLevel) world, chunkPos);

                if (ship != null) {
                    SeatedControllingPlayer playerControl = ship.getAttachment(SeatedControllingPlayer.class);

                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof HelmBlockEntity blockEntity) {
                        if (playerControl != null && blockEntity.hasPilot() && !blockEntity.getFirstItem().isEmpty()) {
                            if (playerControl.getLeftImpulse() < 0) {
                                blockEntity.rotateWheelRight(state, (ServerLevel)world, pos);
                            } else if (playerControl.getLeftImpulse() > 0) {
                                blockEntity.rotateWheelLeft(state, (ServerLevel)world, pos);
                            }
                        }

                        if (blockEntity.getFirstItem().isEmpty() || ship.getShipAABB() == null) return;
                        SailsShipControl control = SailsShipControl.getOrCreate(ship, world);
                        Vec3i sternDirection = control.shipDirection.getNormal();
                        AABBic dims = ship.getShipAABB();
                        double length = sternDirection.getX() == 0 ? dims.maxZ() - dims.minZ() + 1 : dims.maxX() - dims.minX() + 1;
                        double area = rudderarea * (dims.maxX() - dims.minX() + 1.0) * (dims.maxZ() - dims.minZ() + 1.0) / 100.0;
                        Vector3d localVelocity = ship.getTransform().getWorldToShip().transformDirection(ship.getVelocity(), new Vector3d());
                        double signedSpeed = -localVelocity.x * sternDirection.getX() - localVelocity.z * sternDirection.getZ();
                        double angle = Math.toRadians((blockEntity.wheelAngle - 360.0) / 10.0);
                        double density = control.waterAmount > .001 ? 998.0 : Double.parseDouble(ConfigUtils.config.getOrDefault("air-density", "124.0"));
                        double force = com.quintonc.vs_sails.ship.SailingMath.rudderForce(angle, signedSpeed, density,
                                ship.getInertiaData().getMass(), area,
                                Boolean.parseBoolean(ConfigUtils.config.getOrDefault("realistic-rudder", "true")));
                        Vector3d stern = new Vector3d(ship.getInertiaData().getCenterOfMassInShip())
                                .add(sternDirection.getX() * length / 2, 0, sternDirection.getZ() * length / 2)
                                .sub(ship.getTransform().getPositionInShip());
                        control.applyRotDependentForceToPos(new Vector3d(sternDirection.getZ() * force, 0, -sternDirection.getX() * force), stern);

                    }
                }
            }
        }
    }
}
