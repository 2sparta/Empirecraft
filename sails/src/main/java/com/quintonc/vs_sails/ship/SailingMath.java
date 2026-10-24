package com.quintonc.vs_sails.ship;

/** Pure calculations shared by steering and wind. Inputs use radians and metres/second. */
public final class SailingMath {
    private SailingMath() {}

    public static double angleDistance(double a, double b) {
        return Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));
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
