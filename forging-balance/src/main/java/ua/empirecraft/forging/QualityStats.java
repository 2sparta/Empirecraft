package ua.empirecraft.forging;

/** Absolute additions, never percentages; unknown/unforged qualities keep base stats. */
public final class QualityStats {
    private QualityStats() {}
    public static double bonus(String quality, boolean weapon) {
        return switch (quality) {
            case "poor" -> -1.5;
            case "well" -> -0.5;
            case "perfect" -> 0.5;
            case "master" -> weapon ? 2.0 : 1.0;
            default -> 0.0;
        };
    }
    public static float durability(String quality) {
        return switch (quality) {
            case "poor" -> 0.25f;
            case "well" -> 1.0f / 1.5f;
            case "perfect" -> 2.0f;
            case "master" -> 4.0f;
            default -> 1.0f;
        };
    }
}
