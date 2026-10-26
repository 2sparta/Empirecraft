package com.quintonc.vs_sails.ship;

/** Hydrostatic force with an implicit critically damped surface response. SI units. */
public final class WaterSupport {
    public static final double GRAVITY=10, DENSITY=1000;
    private WaterSupport() {}
    public static double legacyMultiplier(int buoys,int ballast,double buoyStrength,double ballastStrength,
                                          double physicalMass,double scaleVolume) {
        double bonus=Math.max(0,buoys)*strength(buoyStrength)+Math.max(0,ballast)*strength(ballastStrength);
        // Preserve the old 0.1 kg buoy waterline while retaining stable 100 kg inertia.
        double oldMass=Math.max(physicalMass*.001,physicalMass-Math.max(0,buoys)*99.9*scaleVolume);
        return (1+bonus)*physicalMass/oldMass;
    }
    public static double force(double mass,double submergedVolume,double waterplaneArea,double multiplier,
                               double verticalSpeed,double dt) {
        if(!Double.isFinite(mass)||mass<=0||!Double.isFinite(submergedVolume)||submergedVolume<=0
                ||!Double.isFinite(waterplaneArea)||waterplaneArea<=0||!Double.isFinite(multiplier)||multiplier<=0
                ||!Double.isFinite(verticalSpeed)||!Double.isFinite(dt)||dt<=0||dt>1) return 0;
        double stiffness=DENSITY*GRAVITY*multiplier*waterplaneArea/mass;
        double damping=2*Math.sqrt(stiffness);
        // Keep very light buoys 3 cm immersed so discrete fluid/physics frames never
        // alternate between a micron of water contact and free fall. Loaded hulls
        // retain the legacy equilibrium draft and full configured lifting capacity.
        double equilibrium=Math.max(mass/(DENSITY*multiplier),waterplaneArea*.03);
        double hydro=GRAVITY+stiffness*(submergedVolume-equilibrium)/waterplaneArea;
        // Implicit integration remains stable for very light rafts and large buoy counts.
        double acceleration=(hydro-GRAVITY-(damping+stiffness*dt)*verticalSpeed)
                /(1+damping*dt+stiffness*dt*dt);
        // Bound force, not the full-volume coefficient: the equilibrium waterline is unchanged.
        acceleration=Math.max(-20,Math.min(5,acceleration));
        double approach=(submergedVolume-equilibrium)/waterplaneArea/(dt+1/Math.sqrt(stiffness));
        // Braking distance shrinks to zero at the waterline, even after a deep-water rise.
        // This prevents the saturated lift limiter from carrying a raft out of the water.
        acceleration=Math.min(acceleration,(Math.min(1.0,approach)-verticalSpeed)/dt);
        return mass*(GRAVITY+acceleration);
    }
    private static double strength(double value) { return Double.isFinite(value)?Math.max(0,value):0; }
}
