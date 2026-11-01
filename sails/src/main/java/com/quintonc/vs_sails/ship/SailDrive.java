package com.quintonc.vs_sails.ship;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Physics-thread longitudinal controller, including measured air and water resistance. */
public final class SailDrive {
    private final SailResponse response = new SailResponse();
    private final DragCompensator resistance = new DragCompensator();
    private double target, drive, assistance;

    public Vector3d acceleration(Vector3dc heading, Vector3dc velocity, double requestedTarget,
                                 double mass, boolean hasHelm, boolean braking,
                                 boolean reversing, boolean coasting, double dt) {
        if (heading == null || velocity == null || !heading.isFinite() || !velocity.isFinite()
                || heading.lengthSquared() < .5 || !Double.isFinite(requestedTarget)
                || !Double.isFinite(mass) || mass <= 0 || !Double.isFinite(dt) || dt <= 0 || dt > .25) {
            reset();
            return new Vector3d();
        }
        if (braking) {
            reset();
            return ArcadeSailing.anchorBrake(velocity);
        }
        resistance.observe(velocity);
        target = requestedTarget;
        if (!reversing) {
            // Filling may build slowly; lost wind must never leave a higher stale speed budget.
            target = Math.min(requestedTarget,response.target(requestedTarget, dt));
            if (coasting) {
                target = requestedTarget;
                response.drive(0, dt);
            }
        } else response.reset();
        Vector3d acceleration = reversing ? ArcadeSailing.reverseAcceleration(heading, velocity, mass, hasHelm)
                : coasting ? ArcadeSailing.coastingAcceleration(heading, velocity, target, mass, hasHelm)
                : ArcadeSailing.acceleration(heading, velocity, target, hasHelm);
        if (!reversing && !coasting) {
            double raw = acceleration.dot(heading);
            double inertia = raw>0 ? Math.max(1, Math.sqrt(mass / 20000)) : 1;
            double smooth = response.drive(raw / inertia, dt);
            if(raw<=0)smooth=Math.min(0,smooth);
            acceleration.add(heading.x() * (smooth - raw), 0, heading.z() * (smooth - raw));
        }
        double forward = velocity.x() * heading.x() + velocity.z() * heading.z();
        if(!reversing && requestedTarget==0) {
            // Passive stopping in a headwind/calm also applies to furled cloth.
            // This is acceleration, so a million-kg hull cannot coast under power indefinitely.
            double stop=Math.max(-3,Math.min(3,-forward/1.5));
            acceleration.fma(stop-acceleration.dot(heading),heading);
            response.reset();
        }
        drive = acceleration.dot(heading);
        // Native aerodynamic drag remains active even when liquidOverlap == 0.
        // Observe real velocity changes instead of gating assistance on that flag.
        // Only offset drag at the permitted speed. Compensating actual overspeed
        // cancelled the water resistance needed to stop after turning upwind.
        double permitted=reversing?1.5:target;
        double assistedSpeed=Math.copySign(Math.min(Math.abs(forward),permitted),forward);
        assistance = resistance.compensation(assistedSpeed);
        acceleration.add(heading.x() * assistance, 0, heading.z() * assistance);
        resistance.record(heading, velocity, acceleration.dot(heading), dt);
        return acceleration;
    }

    public double target() { return target; }
    public double drive() { return drive; }
    public double assistance() { return assistance; }
    public void reset() {
        response.reset(); resistance.reset(); target = drive = assistance = 0;
    }
}
