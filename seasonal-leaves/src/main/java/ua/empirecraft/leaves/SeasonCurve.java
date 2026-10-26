package ua.empirecraft.leaves;

/** Pure season policy: spring 0..3, summer 3..6, autumn 6..9, winter 9..12. */
public final class SeasonCurve {
    private SeasonCurve() {}
    public static double bareFraction(int cycleTicks, int subSeasonTicks) {
        if (subSeasonTicks <= 0) return 0;
        double phase = Math.floorMod((long) cycleTicks, 12L * subSeasonTicks) / (double) subSeasonTicks;
        if (phase < 3) return 1 - phase / 3;
        if (phase < 6) return 0;
        if (phase < 9) return (phase - 6) / 3;
        return 1;
    }
    public static double threshold(long position) {
        long x = position + 0x9e3779b97f4a7c15L;
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        x ^= x >>> 31;
        return ((x >>> 11) + 0.5) * 0x1.0p-53;
    }
    public static boolean bare(long position, double fraction) {
        return fraction >= 1 || (fraction > 0 && threshold(position) < fraction);
    }
}
