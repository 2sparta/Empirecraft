package ua.townyrecruits;

/** Battle morale and straggler policy, independent of entity AI and wall-clock time. */
public final class MoralePolicy {
    public static final long BATTLE_WINDOW = 6000, CALM = 1200, STRAGGLER_WAIT = 2400;
    public static final double ALLY_RADIUS = 32, STRAGGLER_DISTANCE = 64, ESCAPE_DISTANCE = 64;
    private MoralePolicy() {}
    public static double ownLoss(double morale, int initial, boolean stableAllies) {
        return clamp(morale - 140.0 / Math.max(1, initial) * (stableAllies ? 0.6 : 1));
    }
    public static double alliedLoss(double morale, int initial) {
        return clamp(morale - Math.min(8, 35.0 / Math.max(4, initial)));
    }
    public static double breakChance(double morale, int lost, int initial, boolean support) {
        double ratio = lost / (double)Math.max(1, initial);
        if (lost < 1 || initial < 2 || (ratio < 0.35 && morale > 20) || (morale > 35 && ratio < 0.65)) return 0;
        return Math.min(0.95, 0.15 + Math.max(0, 35 - morale) / 50 + Math.max(0, ratio - 0.65) * 1.3) * (support ? 0.65 : 1);
    }
    public static double clamp(double value) { return Math.max(0, Math.min(100, Double.isFinite(value) ? value : 100)); }
    public static boolean straggler(int size, int nearbyMajority, double distance, long stalledTicks, double movement, boolean expected) {
        return expected && size >= 3 && nearbyMajority > size / 2 && distance > STRAGGLER_DISTANCE
            && stalledTicks >= STRAGGLER_WAIT && movement < 2;
    }
    public static boolean escaped(double fromStart, double fromThreat, boolean enemyNear) {
        return fromStart >= ESCAPE_DISTANCE && fromThreat >= 48 && !enemyNear;
    }
}
