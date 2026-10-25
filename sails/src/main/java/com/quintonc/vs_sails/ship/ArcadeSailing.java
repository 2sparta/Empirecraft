package com.quintonc.vs_sails.ship;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Arcade sailing uses the helm's bow, with positive but very weak headwind drive. */
public final class ArcadeSailing {
    public static final double STOP_SPEED = .12;
    public static final double STOP_ANGULAR_SPEED = .045;
    private ArcadeSailing() {}

    public static Vector3d heading(Quaterniondc rotation, double bowX, double bowZ) {
        Vector3d result = new Vector3d(bowX, 0, bowZ);
        if (rotation == null || !result.isFinite()) return result.zero();
        rotation.transform(result); result.y = 0;
        double length = result.length();
        return result.isFinite() && Double.isFinite(length) && length > 1e-9 ? result.div(length) : result.zero();
    }

    public static double targetSpeed(Vector3dc heading, double direction, double strength,
                                     int sails, double power, double mass, boolean windEnabled) {
        if (heading == null || !heading.isFinite() || heading.lengthSquared() < .5 || sails <= 0
                || !Double.isFinite(power) || power <= 0 || !Double.isFinite(mass) || mass <= 0
                || !Double.isFinite(direction) || !Double.isFinite(strength)) return 0;
        double capacity = Math.min(1, Math.sqrt(sails * power / (mass * 4)));
        if (!windEnabled) return 6 * capacity;
        double radians = Math.toRadians(direction % 360);
        double alignment = (heading.x() * Math.cos(radians) + heading.z() * Math.sin(radians)) * Math.signum(strength);
        double reach = Math.max(0, Math.min(1, (alignment + 1) * .5));
        double windFactor = .02 + .98 * reach * reach;
        return 6 * capacity * Math.min(1, strength * strength) * windFactor;
    }

    /** Bounded world acceleration: strong lateral grip and a smooth forward speed servo. */
    public static Vector3d acceleration(Vector3dc heading, Vector3dc velocity, double target, boolean hasHelm) {
        if (heading == null || velocity == null || !heading.isFinite() || !velocity.isFinite()
                || heading.lengthSquared() < .5 || !Double.isFinite(target) || target < 0) return new Vector3d();
        double forward = velocity.x() * heading.x() + velocity.z() * heading.z();
        double drive = clamp((target - forward) * .9, -3, 2.5);
        Vector3d lateral = new Vector3d(velocity.x() - heading.x() * forward, 0, velocity.z() - heading.z() * forward);
        lateral.mul(hasHelm ? -8 : -2);
        limit(lateral, 12);
        return lateral.add(heading.x() * drive, 0, heading.z() * drive);
    }

    public static Vector3d angularAcceleration(Vector3dc omega, double turn, double forwardSpeed, boolean braking) {
        if (omega == null || !omega.isFinite() || !Double.isFinite(turn) || !Double.isFinite(forwardSpeed)) return new Vector3d();
        double rate = braking ? 0 : clamp(turn, -1, 1) * Math.toRadians(25)
                * (.35 + .65 * Math.min(1, Math.abs(forwardSpeed) / 2));
        return new Vector3d(clamp(-omega.x() * 2, -2, 2), clamp((rate - omega.y()) * 5, -2.5, 2.5), clamp(-omega.z() * 2, -2, 2));
    }

    public static Vector3d anchorBrake(Vector3dc velocity) {
        if (velocity == null || !velocity.isFinite()) return new Vector3d();
        Vector3d result = new Vector3d(velocity).mul(-6);
        limit(result, 10);
        return result;
    }

    public static boolean canHold(Vector3dc velocity, Vector3dc omega) {
        return velocity != null && omega != null && velocity.isFinite() && omega.isFinite()
                && velocity.lengthSquared() <= STOP_SPEED * STOP_SPEED
                && omega.lengthSquared() <= STOP_ANGULAR_SPEED * STOP_ANGULAR_SPEED;
    }

    private static void limit(Vector3d vector, double maximum) {
        double length = vector.length();
        if (length > maximum) vector.mul(maximum / length);
    }
    private static double clamp(double v, double low, double high) { return Math.max(low, Math.min(high, v)); }
}
