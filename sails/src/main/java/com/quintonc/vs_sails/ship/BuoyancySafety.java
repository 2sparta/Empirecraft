package com.quintonc.vs_sails.ship;

/** Hydrostatic limits for the pinned VS water density (1000 kg/m3). No game-thread world queries. */
public final class BuoyancySafety {
    private static final double WATER_DENSITY = 1000.0;
    public static final double MAX_RISE_SPEED = 2.0;
    private BuoyancySafety() {}
    public static boolean safeForFluidDrag(double mass,double displacementBlocks) {
        // Old 0.1 kg buoy inertia makes even ordinary water drag numerically explosive.
        return Double.isFinite(mass) && Double.isFinite(displacementBlocks) && displacementBlocks > 0
                && mass >= 99.0*displacementBlocks;
    }

    public static double factor(int buoys,int ballast,double buoyStrength,double ballastStrength,
                                double mass,double displacementBlocks,double liquidOverlap,double riseSpeed) {
        return factor(buoys,ballast,buoyStrength,ballastStrength,mass,displacementBlocks,liquidOverlap,riseSpeed,1.0/60.0);
    }

    public static double factor(int buoys,int ballast,double buoyStrength,double ballastStrength,
                                double mass,double displacementBlocks,double liquidOverlap,double riseSpeed,double physicsStep) {
        if (!Double.isFinite(physicsStep) || physicsStep <= 0 || physicsStep > 1) return 0;
        if (!Double.isFinite(mass) || mass <= 0 || !Double.isFinite(displacementBlocks) || displacementBlocks <= 0
                || !Double.isFinite(liquidOverlap) || liquidOverlap <= 0 || !Double.isFinite(riseSpeed)) return 0;
        double bonus = Math.max(0,buoys)*safeStrength(buoyStrength) + Math.max(0,ballast)*safeStrength(ballastStrength);
        double desired = 1.0 + Math.min(1.0,bonus); // Adding buoys cannot amplify the entire hull without limit.
        // The old 1.5-weight cap at FULL volume forced light ships ~2/3 underwater.
        // Preserve normal Archimedean lift at partial immersion. Bound the worst-case
        // next-step vertical speed, even if the entire counted volume is submerged.
        // Pinned VS uses 10 m/s^2 gravity. No assumption about liquidOverlap units.
        double support = Math.max(0,1.0+(MAX_RISE_SPEED-riseSpeed)/(10.0*physicsStep));
        // Old near-zero buoy inertia still needs the conservative cap until remass.
        if (!safeForFluidDrag(mass,displacementBlocks)) support = Math.min(support,1.5);
        return Math.max(0,Math.min(desired,support*mass/(WATER_DENSITY*displacementBlocks)));
    }
    private static double safeStrength(double value) { return Double.isFinite(value) ? Math.max(0,value) : 0; }
}
