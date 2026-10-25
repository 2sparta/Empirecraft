package com.quintonc.vs_sails.ship;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Arcade sailing uses the helm's bow, with positive but very weak headwind drive. */
public final class ArcadeSailing {
    public static final double STOP_SPEED = .12;
    public static final double STOP_ANGULAR_SPEED = .045;
    public static final double MAX_SPEED = 18;
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
        return targetSpeed(heading, direction, strength, sails, 0, power, mass, windEnabled);
    }

    public static double targetSpeed(Vector3dc heading, double direction, double strength,
                                     int sails, int furled, double power, double mass, boolean windEnabled) {
        boolean reducedRig = sails <= 0 && furled > 0;
        int cloth = reducedRig ? furled : sails;
        if (heading == null || !heading.isFinite() || heading.lengthSquared() < .5 || cloth <= 0
                || !Double.isFinite(power) || power <= 0 || !Double.isFinite(mass) || mass <= 0
                || !Double.isFinite(direction) || !Double.isFinite(strength)) return 0;
        double capacity = Math.min(1, Math.sqrt(cloth * power / mass));
        double maximum = reducedRig ? 2 : MAX_SPEED;
        if (!windEnabled) return maximum * capacity;
        return maximum * capacity * Math.sqrt(Math.min(1, Math.abs(strength)))
                * windFactor(heading, direction, strength);
    }

    /** Same signed world vector as the flag and particles; heading already includes ship rotation. */
    public static double windFactor(Vector3dc heading, double direction, double strength) {
        if (heading == null || !heading.isFinite() || heading.lengthSquared() < .5
                || !Double.isFinite(direction) || !Double.isFinite(strength)) return 0;
        double radians = Math.toRadians(direction % 360);
        double alignment = (heading.x() * Math.cos(radians) + heading.z() * Math.sin(radians)) * Math.signum(strength);
        double reach = Math.max(0, Math.min(1, (alignment + 1) * .5));
        return .02 + .98 * reach * (2 - reach);
    }

    /** Bounded world acceleration: strong lateral grip and a smooth forward speed servo. */
    public static Vector3d acceleration(Vector3dc heading, Vector3dc velocity, double target, boolean hasHelm) {
        if (heading == null || velocity == null || !heading.isFinite() || !velocity.isFinite()
                || heading.lengthSquared() < .5 || !Double.isFinite(target) || target < 0) return new Vector3d();
        double forward = velocity.x() * heading.x() + velocity.z() * heading.z();
        // A fixed 5 m/s² drive stalls near 4–5 blocks/s against VS water drag, even at target 18.
        // Keep a gentle launch, then raise available thrust with speed; overspeed always brakes.
        double driveLimit = Math.min(40, 5 + 3 * Math.max(0, forward));
        double drive = clamp((target - forward) * 6, -12, driveLimit);
        Vector3d lateral = new Vector3d(velocity.x() - heading.x() * forward, 0, velocity.z() - heading.z() * forward);
        lateral.mul(hasHelm ? -8 : -2);
        limit(lateral, 12);
        return lateral.add(heading.x() * drive, 0, heading.z() * drive);
    }

    /** Furled cloth keeps a little windage; it is not an anchor or a powered full stop. */
    public static Vector3d coastingAcceleration(Vector3dc heading, Vector3dc velocity, double target,
                                                double mass, boolean hasHelm) {
        if (!Double.isFinite(target) || target < 0) return new Vector3d();
        return slowAcceleration(heading, velocity, target, mass, hasHelm, false);
    }

    public static boolean reverseEngaged(boolean engaged, boolean requested, Vector3dc velocity) {
        return requested && velocity != null && velocity.isFinite()
                && (engaged || Math.hypot(velocity.x(), velocity.z()) < 1.5);
    }

    public static Vector3d reverseAcceleration(Vector3dc heading, Vector3dc velocity, double mass,
                                               boolean hasHelm) {
        return slowAcceleration(heading, velocity, -1.5, mass, hasHelm, true);
    }

    private static Vector3d slowAcceleration(Vector3dc heading, Vector3dc velocity, double target,
                                              double mass, boolean hasHelm, boolean reverse) {
        if (heading == null || velocity == null || !heading.isFinite() || !velocity.isFinite()
                || heading.lengthSquared() < .5 || !Double.isFinite(target)
                || !Double.isFinite(mass) || mass <= 0) return new Vector3d();
        double forward = velocity.x() * heading.x() + velocity.z() * heading.z();
        double inertia = clamp(Math.sqrt(mass / 20000), .75, 3);
        double drive = reverse ? clamp((target - forward) * 1.5, -1.2 / inertia, 1.2 / inertia)
                : clamp((target - forward) / 4, -.8 / inertia, .6 / inertia);
        Vector3d result = new Vector3d(velocity.x() - heading.x() * forward, 0, velocity.z() - heading.z() * forward);
        result.mul(hasHelm ? -8 : -2); limit(result, 12);
        // VS retains its native buoyancy and vertical/rotational water damping.
        return result.add(heading.x() * drive, 0, heading.z() * drive);
    }

    /** Size affects turning, never bow selection. The floor keeps very large ships manoeuvrable. */
    public static double turnRateDegrees(double length, double width) {
        if (!Double.isFinite(length) || !Double.isFinite(width)) return 10;
        double span = Math.hypot(Math.max(1, length), Math.max(1, width));
        return clamp(32 / Math.sqrt(Math.max(1, span / 16)), 10, 32);
    }

    public static Vector3d angularAcceleration(Vector3dc omega, double turn, double forwardSpeed,
                                               double length, double width, boolean braking) {
        if (omega == null || !omega.isFinite() || !Double.isFinite(turn) || !Double.isFinite(forwardSpeed)) return new Vector3d();
        double rate = braking ? 0 : clamp(turn, -1, 1) * Math.toRadians(turnRateDegrees(length, width))
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
