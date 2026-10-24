package com.quintonc.vs_sails.ship;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** A tension-only, damped rope. It never pushes or freezes a ship. */
public final class AnchorTether {
    private AnchorTether() {}

    public static Vector3d force(Vector3dc point, Vector3dc target, Vector3dc velocity,
                                double ropeLength, double mass) {
        Vector3d delta = target.sub(point, new Vector3d());
        double distance = delta.length();
        if (!delta.isFinite() || !velocity.isFinite() || !Double.isFinite(mass)
                || !Double.isFinite(ropeLength) || ropeLength < 0 || mass <= 0
                || distance <= ropeLength || distance < 1e-6) return new Vector3d();
        delta.div(distance);
        double tension = Math.max(0, Math.min(mass * 4, mass * ((distance - ropeLength) * .8 - velocity.dot(delta) * 1.2)));
        return delta.mul(tension);
    }
}
