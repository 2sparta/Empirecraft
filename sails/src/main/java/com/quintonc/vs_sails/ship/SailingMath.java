package com.quintonc.vs_sails.ship;

import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** Pure calculations shared by steering and wind. Inputs use radians and metres/second. */
public final class SailingMath {
    private SailingMath() {}

    public static double angleDistance(double a, double b) {
        return Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));
    }

    /**
     * Horizontal world force along the hull, driven by the signed wind component.
     * Wind uses the particle/flag convention: 0 degrees = +X, 90 degrees = +Z;
     * negative strength reverses that vector. The hull axis has no privileged end.
     * This is the existing simplified sail polar, not a lift/trim simulation.
     */
    public static Vector3d sailForce(Quaterniondc rotation, double axisX, double axisZ,
                                     double directionDegrees, double strength,
                                     int squareSails, int foreAndAftSails, double power,
                                     double noSailZone, boolean forgiving) {
        Vector3d heading = new Vector3d(axisX, 0, axisZ);
        if (rotation == null || !Double.isFinite(axisX) || !Double.isFinite(axisZ)
                || !Double.isFinite(directionDegrees) || !Double.isFinite(strength)
                || !Double.isFinite(power) || power < 0
                || !Double.isFinite(noSailZone) || noSailZone <= 0 || noSailZone >= Math.PI
                || squareSails < 0 || foreAndAftSails < 0) return heading.zero();
        rotation.transform(heading);
        heading.y = 0;
        double length = heading.length();
        if (!heading.isFinite() || length < 1.0e-9 || !Double.isFinite(length)) return heading.zero();
        heading.div(length);
        double radians = Math.toRadians(directionDegrees % 360.0);
        double along = (heading.x * Math.cos(radians) + heading.z * Math.sin(radians)) * Math.signum(strength);
        along = Math.max(-1.0, Math.min(1.0, along));
        // No preferred direction at a beam wind; fade through zero instead of flipping abruptly.
        if (Math.abs(along) < 1.0e-9) return heading.zero();
        double squareAngle = Math.acos(Math.abs(along));
        double foreAndAftAngle = Math.PI / 2.0 - squareAngle;
        double effectiveSails = squareSails * sailEfficiency(squareAngle, Math.PI - noSailZone, forgiving)
                + foreAndAftSails * sailEfficiency(foreAndAftAngle, Math.PI - noSailZone, forgiving);
        double thrust = effectiveSails * power * strength * strength * along;
        return Double.isFinite(thrust) ? heading.mul(thrust) : heading.zero();
    }

    private static double sailEfficiency(double angle, double width, boolean forgiving) {
        double exponent = (forgiving ? angle : angle * angle) / width;
        return 1.0 / (Math.pow(2, exponent) + Math.pow(2, -exponent));
    }

    public static double rudderForce(double angle, double speed, double density,
                                     double mass, double area, boolean realistic) {
        if (!Double.isFinite(angle) || !Double.isFinite(speed) || !Double.isFinite(density)
                || !Double.isFinite(mass) || !Double.isFinite(area)
                || mass <= 0 || density < 0 || area < 0) return 0;
        double flow = realistic ? speed * Math.abs(speed) / 6.0 : 1.0;
        double force = 2 * Math.PI * angle * density * Math.sqrt(mass / 100.0) * area * flow;
        return Double.isFinite(force) ? force : 0;
    }
}
