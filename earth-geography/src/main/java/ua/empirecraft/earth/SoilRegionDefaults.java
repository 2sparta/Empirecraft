package ua.empirecraft.earth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;

/** Add stock ecology profiles and climate transition bands to an existing configuration without rewriting it. */
final class SoilRegionDefaults {
    private SoilRegionDefaults() {}

    static JsonObject merge(String configured, String defaults) {
        var effective = JsonParser.parseString(configured).getAsJsonObject();
        var features = effective.getAsJsonArray("features");
        var ids = new HashSet<String>();
        for (var row : features) {
            var feature = row.getAsJsonObject();
            if (isMerged(feature)) ids.add(key(feature));
        }
        for (var row : JsonParser.parseString(defaults).getAsJsonObject().getAsJsonArray("features")) {
            var feature = row.getAsJsonObject();
            if (isMerged(feature) && ids.add(key(feature))) features.add(feature);
        }
        return effective;
    }

    private static String key(JsonObject feature) {
        return feature.get("layer").getAsString() + ":" + feature.get("id").getAsString();
    }

    private static boolean isMerged(JsonObject feature) {
        String layer = feature.get("layer").getAsString();
        return layer.equals("ecology")
            || layer.equals("climate") && feature.get("id").getAsString().contains("_transition");
    }
}
