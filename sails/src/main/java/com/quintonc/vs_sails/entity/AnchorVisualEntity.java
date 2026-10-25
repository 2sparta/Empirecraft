package com.quintonc.vs_sails.entity;

import com.quintonc.vs_sails.ship.SailsShipControl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** One non-persistent render entity per deployed anchor; it never places world blocks. */
public final class AnchorVisualEntity extends Entity {
    private static final EntityDataAccessor<Vector3f> END = SynchedEntityData.defineId(AnchorVisualEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<BlockState> STATE = SynchedEntityData.defineId(AnchorVisualEntity.class, EntityDataSerializers.BLOCK_STATE);
    private long shipId, capstan;
    private Vec3 previousEnd = Vec3.ZERO, renderedEnd = Vec3.ZERO;
    private boolean renderInitialized;

    public AnchorVisualEntity(EntityType<? extends AnchorVisualEntity> type, Level level) {
        super(type, level); noPhysics = true; setNoGravity(true);
    }
    @Override protected void defineSynchedData() {
        entityData.define(END, new Vector3f());
        entityData.define(STATE, Blocks.AIR.defaultBlockState());
    }
    public void update(long shipId, long capstan, Vec3 start, Vec3 bottom, BlockState state) {
        this.shipId = shipId; this.capstan = capstan;
        setPos(start);
        Vec3 delta = bottom.subtract(start);
        entityData.set(END, new Vector3f((float) delta.x, (float) delta.y, (float) delta.z));
        entityData.set(STATE, state);
    }
    public BlockState anchorState() { return entityData.get(STATE); }
    public Vec3 anchorOffset(float partialTick) { return previousEnd.lerp(renderedEnd, partialTick); }
    @Override public void tick() {
        super.tick();
        Vector3f end = entityData.get(END);
        Vec3 target = new Vec3(end.x, end.y, end.z);
        if (!renderInitialized) { previousEnd = renderedEnd = target; renderInitialized = true; }
        previousEnd = renderedEnd;
        renderedEnd = level().isClientSide ? renderedEnd.lerp(target, .65) : target;
        if (level() instanceof ServerLevel server && tickCount % 20 == 0) {
            var ship = VSGameUtilsKt.getShipObjectWorld(server).getLoadedShips().getById(shipId);
            var control = ship == null ? null : ship.getAttachment(SailsShipControl.class);
            if (control == null || !control.anchorDeployed || control.anchorCapstan != capstan) discard();
        }
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }
    @Override public boolean isPickable() { return false; }
}
