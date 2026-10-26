package ua.empirecraft.forging;

import java.util.*;
import com.google.gson.*;

/** Exact retired IDs from the installed Epic Knights release. No shield or armor matching. */
public final class KnightItems {
    public static final String PLATE = "magistuarmory:steel_plate";
    public static final String CANONICAL_PLATE = "overgeared:steel_plate";
    public static final Map<String, String> REPLACEMENTS;
    static {
        Map<String, String> items = new LinkedHashMap<>();
        items.put(PLATE, CANONICAL_PLATE);
        for (String family : ("stylet katzbalger pike ranseur ahlspiess chivalrylance bastardsword estoc claymore zweihander "
            + "flamebladedsword lochaberaxe concavehalberd heavymace heavywarhammer lucernhammer morgenstern shortsword chainmorgenstern guisarme").split(" "))
            for (String tier : List.of("diamond", "netherite"))
                items.put("magistuarmory:" + tier + "_" + family, "magistuarmory:steel_" + family);
        REPLACEMENTS = Map.copyOf(items);
    }
    private KnightItems() {}
    public static boolean retired(String id) { return REPLACEMENTS.containsKey(id); }
    /** Normalize inputs only after the original output has been checked, so the old plate craft cannot become a second steel source. */
    public static JsonElement canonicalPlates(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().equals(PLATE))
            return new JsonPrimitive(CANONICAL_PLATE);
        if (value.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement child : value.getAsJsonArray()) copy.add(canonicalPlates(child));
            return copy;
        }
        if (value.isJsonObject()) {
            JsonObject copy = new JsonObject();
            for (var child : value.getAsJsonObject().entrySet()) copy.add(child.getKey(), canonicalPlates(child.getValue()));
            return copy;
        }
        return value.deepCopy();
    }
    public static boolean retiredOutput(JsonObject recipe) {
        for (String key : List.of("result", "results", "output"))
            if (recipe.has(key) && output(recipe.get(key))) return true;
        return false;
    }
    private static boolean output(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return retired(value.getAsString());
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (output(child)) return true;
        }
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            return object.has("item") && output(object.get("item"));
        }
        return false;
    }
    public static int damage(int oldDamage, int oldMaximum, int newMaximum) {
        if (oldMaximum <= 0 || newMaximum <= 0) return 0;
        return (int)Math.min(newMaximum - 1, Math.max(0, (long)oldDamage * newMaximum / oldMaximum));
    }
}
