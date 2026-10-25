package ua.empirecraft.forging;

import java.util.List;
import java.util.Locale;

/** Pure rules shared by the server, client and regression tests. */
public final class ArmorRules {
    public static final String PREFIX = "ecf1|";
    private static final List<String> QUALITIES = List.of("poor", "well", "expert", "perfect", "master");
    private ArmorRules() {}

    public static String quality(String value) {
        String normalized = value == null ? "well" : value.toLowerCase(Locale.ROOT);
        return QUALITIES.contains(normalized) ? normalized : "well";
    }

    public static String cap(String value, String ceiling) {
        return QUALITIES.get(Math.min(QUALITIES.indexOf(quality(value)), QUALITIES.indexOf(quality(ceiling))));
    }

    public static int difficulty(int defense, String slot) {
        // Slot-relative levels: a top helmet/boots (+4) or leggings (+7) is level 9.
        // The raw >=7 rule always applies, including unusual modded armor slots.
        int offset = switch (slot) { case "head", "feet" -> 5; case "legs" -> 2; default -> 0; };
        return Math.min(9, Math.max(defense, defense + offset));
    }

    public static float initialSpeed(int tier, boolean smallerPiece) {
        return (tier >= 9 ? 9.0f : tier == 8 ? 7.0f : 5.5f) * (smallerPiece ? 0.85f : 1.0f);
    }

    public static float maximumSpeed(int tier, boolean smallerPiece) {
        return (tier >= 9 ? 20.0f : tier == 8 ? 16.0f : 12.0f) * (smallerPiece ? 0.85f : 1.0f);
    }

    public static float acceleration(int tier, boolean smallerPiece) {
        return (tier >= 9 ? 4.0f : tier == 8 ? 3.2f : 2.4f) * (smallerPiece ? 0.85f : 1.0f);
    }

    public static String result(int tier, String blueprint, int misses) {
        String maximum = quality(blueprint);
        if (misses == 0) return maximum;
        if (tier >= 9) return misses == 1 && maximum.equals("master") ? "well" : "poor";
        if (tier == 8) return misses == 1 ? cap(maximum, "well") : "poor";
        if (misses == 1) return cap(maximum, "expert");
        if (misses == 2) return cap(maximum, "well");
        return "poor";
    }

    public static String accuracy(HitReport hits, double perfect, double expert, double well) {
        double score = hits.total() == 0 ? 0 : (hits.perfect() + hits.good() * 0.6) / hits.total();
        return score > perfect ? "perfect" : score > expert ? "expert" : score > well ? "well" : "poor";
    }

    public record HitReport(int perfect, int good, int missed) {
        public int total() { return perfect + good + missed; }
        public String encode() { return PREFIX + perfect + "|" + good + "|" + missed; }
        public boolean follows(HitReport previous) {
            return total() == previous.total() + 1 && perfect >= previous.perfect && good >= previous.good && missed >= previous.missed;
        }
        public static HitReport decode(String value) {
            if (value == null || !value.startsWith(PREFIX) || value.length() > 40) return null;
            String[] fields = value.substring(PREFIX.length()).split("\\|", -1);
            if (fields.length != 3) return null;
            try {
                int p = Integer.parseInt(fields[0]), g = Integer.parseInt(fields[1]), m = Integer.parseInt(fields[2]);
                if (p < 0 || g < 0 || m < 0 || (long)p + g + m > 1024) return null;
                return new HitReport(p, g, m);
            } catch (NumberFormatException ignored) { return null; }
        }
    }

    public record Game(int tier, boolean smallerPiece, String blueprint) {
        public String encode() { return PREFIX + tier + "|" + (smallerPiece ? "1" : "0") + "|" + quality(blueprint); }
        public static Game decode(String value) {
            if (value == null || !value.startsWith(PREFIX)) return null;
            String[] fields = value.substring(PREFIX.length()).split("\\|", -1);
            if (fields.length != 3 || !fields[0].matches("[789]") || !fields[1].matches("[01]") || !QUALITIES.contains(fields[2])) return null;
            return new Game(Integer.parseInt(fields[0]), fields[1].equals("1"), fields[2]);
        }
    }
}
