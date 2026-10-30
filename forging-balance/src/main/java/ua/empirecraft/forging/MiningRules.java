package ua.empirecraft.forging;

/** Vanilla Efficiency is an additive level^2+1, enabled only above hand speed. */
public final class MiningRules {
    private MiningRules() {}
    public static float speed(String quality,float base) {
        if (!(base>1.0f)) return base;
        return switch(quality) {
            case "poor" -> base*0.55f;
            case "expert" -> base*1.55f;
            case "perfect" -> base+10.0f;
            case "master" -> base+26.0f;
            default -> base;
        };
    }
}
