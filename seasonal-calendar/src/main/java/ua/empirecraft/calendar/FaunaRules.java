package ua.empirecraft.calendar;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Profile arrays use civil month order, January through December. */
public final class FaunaRules {
    private record Rules(Map<String, double[]> profiles, Map<String, String> entities) {}
    private static final Rules RULES = load();
    private FaunaRules() {}
    private static Rules load() {
        try (var in = FaunaRules.class.getResourceAsStream("/empirecraft-fauna.json")) {
            if (in == null) throw new IllegalStateException("Missing fauna rules");
            var rules = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Rules.class);
            for (var entry : rules.profiles.entrySet()) {
                if (entry.getValue().length != 12) throw new IllegalStateException("Expected 12 months: " + entry.getKey());
                for (double value : entry.getValue()) if (!Double.isFinite(value) || value < 0 || value > 8) throw new IllegalStateException("Invalid spawn factor");
            }
            for (String profile : rules.entities.values()) if (!rules.profiles.containsKey(profile)) throw new IllegalStateException("Unknown fauna profile: " + profile);
            return rules;
        } catch (Exception error) { throw new IllegalStateException("Cannot load seasonal fauna", error); }
    }
    public static String profile(String id, String category) {
        if (category.equals("MONSTER")) return "unchanged";
        String explicit = RULES.entities.get(id);
        if (explicit != null) return explicit;
        return switch (category) {
            case "CREATURE" -> "temperate";
            case "AMBIENT" -> "night_flyer";
            case "WATER_CREATURE", "WATER_AMBIENT", "UNDERGROUND_WATER_CREATURE", "AXOLOTLS" -> "fish";
            default -> "unchanged";
        };
    }
    public static double factor(String id, String category, int month) {
        return RULES.profiles.get(profile(id, category))[Math.floorMod(month, 12)];
    }
}
