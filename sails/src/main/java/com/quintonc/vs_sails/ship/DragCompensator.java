package com.quintonc.vs_sails.ship;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Learns longitudinal resistance from the last applied force and the actual next physics velocity. */
public final class DragCompensator {
    private final Vector3d previousHeading = new Vector3d(), previousVelocity = new Vector3d();
    private double previousDrive, previousDt, coefficient;
    private boolean sampled;

    public void observe(Vector3dc velocity) {
        if (sampled && velocity.isFinite() && previousDt > 0) {
            double before = previousVelocity.dot(previousHeading), now = velocity.dot(previousHeading);
            double measured = (now - before) / previousDt;
            if (Math.abs(before) > .5 && before * now > 0 && Math.abs(measured) < 150) {
                double resistance = (previousDrive - measured) * Math.signum(before);
                double estimate = Math.max(0, Math.min(2, resistance / (before * before)));
                if (Double.isFinite(estimate)) {
                    double blend = Math.min(1, previousDt / .06);
                    coefficient += (estimate - coefficient) * blend;
                }
            }
        }
    }

    public double compensation(double speed) {
        if (!Double.isFinite(speed)) return 0;
        return Math.max(-100, Math.min(100, coefficient * speed * Math.abs(speed)));
    }

    public void record(Vector3dc heading, Vector3dc velocity, double drive, double dt) {
        sampled = heading.isFinite() && velocity.isFinite() && Double.isFinite(drive)
                && Double.isFinite(dt) && dt > 0 && dt <= .25;
        if (!sampled) return;
        previousHeading.set(heading); previousVelocity.set(velocity); previousDrive = drive; previousDt = dt;
    }

    public void reset() { sampled = false; coefficient = 0; }
}
