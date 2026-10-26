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
        if (!Double.isFinite(mass) || mass <= 0 || !Double.isFinite(displacementBlocks) || displacementBlocks <= 0
                || !Double.isFinite(liquidOverlap) || liquidOverlap <= 0 || !Double.isFinite(riseSpeed)) return 0;
        double bonus = Math.max(0,buoys)*safeStrength(buoyStrength) + Math.max(0,ballast)*safeStrength(ballastStrength);
        double desired = 1.0 + Math.min(1.0,bonus); // Adding buoys cannot amplify the entire hull without limit.
        // At full immersion lift is at most 1.5 times weight; it tapers as the hull rises toward 2 m/s.
        double support = 1.0 + Math.max(-1.0,Math.min(0.5,(MAX_RISE_SPEED-riseSpeed)*0.5));
        return Math.max(0,Math.min(desired,support*mass/(WATER_DENSITY*displacementBlocks)));
    }
    private static double safeStrength(double value) { return Double.isFinite(value) ? Math.max(0,value) : 0; }
}
