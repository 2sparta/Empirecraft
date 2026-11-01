package com.quintonc.vs_sails.ship;

import org.joml.Vector3dc;

/** Last-resort limits, far outside the sailing controller's 18 blocks/s envelope. */
public final class ShipMotionSafety {
    private ShipMotionSafety() {}
    public static boolean unsafe(Vector3dc position, Vector3dc velocity, Vector3dc omega) {
        return position == null || velocity == null || omega == null
                || !position.isFinite() || !velocity.isFinite() || !omega.isFinite()
                || Math.abs(position.x()) > 29999000 || Math.abs(position.z()) > 29999000
                || Math.abs(position.y()) > 2048 || velocity.lengthSquared() > 96*96
                || omega.lengthSquared() > 12*12;
    }
    public static boolean needsHull(int cloth, int hull) { return cloth > 0 && hull == 0; }
}
