package com.quintonc.vs_sails.ship;

/** Hydrostatic limits for the pinned VS water density (1000 kg/m3). No game-thread world queries. */
public final class BuoyancySafety {
    private BuoyancySafety() {}
    public static boolean safeForFluidDrag(double mass,double displacementBlocks) {
        // Old 0.1 kg buoy inertia makes even ordinary water drag numerically explosive.
        return Double.isFinite(mass) && Double.isFinite(displacementBlocks) && displacementBlocks > 0
                && mass >= 99.0*displacementBlocks;
    }

}
