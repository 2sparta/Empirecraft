package com.quintonc.vs_sails.client;

import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** Minecraft's local look vector transformed exactly once into world coordinates. */
public final class MapHeading {
    private MapHeading() {}

    public static double worldYaw(Quaterniondc shipRotation, double yaw, double pitch) {
        if (shipRotation == null || !Double.isFinite(yaw) || !Double.isFinite(pitch)) return yaw;
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        Vector3d look = new Vector3d(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        shipRotation.transform(look);
        if (!look.isFinite() || look.x * look.x + look.z * look.z < 1e-12) {
            look.set(-Math.sin(y), 0, Math.cos(y)); shipRotation.transform(look);
        }
        return look.isFinite() && look.x * look.x + look.z * look.z >= 1e-12
                ? Math.toDegrees(Math.atan2(-look.x, look.z)) : yaw;
    }
}
