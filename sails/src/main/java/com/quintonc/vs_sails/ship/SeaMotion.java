package com.quintonc.vs_sails.ship;

import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** Small, continuous wave torques. No world reads, position changes or yaw commands. */
public final class SeaMotion {
    private SeaMotion() {}

    public static double severity(double rain, double thunder) {
        if (!Double.isFinite(rain) || !Double.isFinite(thunder)) return 0;
        return Math.max(0, Math.min(1, rain)) + .35 * Math.max(0, Math.min(1, thunder));
    }

    public static Vector3d acceleration(Quaterniondc rotation, double immersion, double seconds,
                                         double x, double z, double span, double rain, double thunder,
                                         double strength) {
        Vector3d result = new Vector3d();
        if (rotation == null || !rotation.isFinite() || !Double.isFinite(immersion) || immersion <= 0
                || !Double.isFinite(seconds) || !Double.isFinite(x) || !Double.isFinite(z)
                || !Double.isFinite(span) || span <= 0 || !Double.isFinite(strength) || strength <= 0) return result;
        Vector3d up = rotation.transform(new Vector3d(0, 1, 0));
        // Waves must not right an overturned hull, or fight an ongoing capsize.
        if (up.y <= .5) return result;
        double wet = Math.min(1, immersion / .08) * Math.min(1, (up.y - .5) / .3);
        double size = Math.min(1, Math.sqrt(16 / Math.max(4, span)));
        double amplitude = (.075 + .65 * severity(rain, thunder)) * Math.min(2, strength) * wet * size;
        double phase = seconds * .83 + x * .031 + z * .019;
        result.set(amplitude * (.75 * Math.sin(phase) + .25 * Math.sin(phase * 1.71 + 1.2)), 0,
                amplitude * (.65 * Math.cos(phase * .79 + .8) + .2 * Math.sin(phase * 1.37)));
        if (result.lengthSquared() > 1) result.normalize();
        return result;
    }
}
