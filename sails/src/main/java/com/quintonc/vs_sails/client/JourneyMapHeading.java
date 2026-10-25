package com.quintonc.vs_sails.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Optional JourneyMap adapter; the map must see world yaw without changing the player's input yaw. */
public final class JourneyMapHeading {
    private JourneyMapHeading() {}

    public static float entityYaw(LivingEntity entity) {
        return mountedYaw(entity, entity.getYRot(), entity.getXRot());
    }

    public static float cameraYaw(Camera camera) {
        // VS Camera.setRotationWithShipTransform stores local yaw/pitch, but rotates its quaternion.
        return mountedYaw(camera.getEntity(), camera.getYRot(), camera.getXRot());
    }

    private static float mountedYaw(Entity entity, float yaw, float pitch) {
        // Unmounted players already get world yaw through EntityDragger. Avoid double rotation.
        if (entity != null && entity == Minecraft.getInstance().player
                && VSGameUtilsKt.getShipMountedTo(entity) instanceof ClientShip ship) {
            return (float) MapHeading.worldYaw(ship.getRenderTransform().getShipToWorldRotation(), yaw, pitch);
        }
        return yaw;
    }
}
