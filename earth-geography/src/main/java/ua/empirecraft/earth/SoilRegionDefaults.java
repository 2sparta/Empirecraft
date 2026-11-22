package ua.empirecraft.earth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;

/** Add stock ecology profiles to an existing configuration without rewriting it. */
final class SoilRegionDefaults {
    private SoilRegionDefaults() {}

    static JsonObject merge(String configured, String defaults) {
        var effective = JsonParser.parseString(configured).getAsJsonObject();
        var features = effective.getAsJsonArray("features");
        var ecologyIds = new HashSet<String>();
        for (var row : features) {
            var feature = row.getAsJsonObject();
            if (isEcology(feature)) ecologyIds.add(feature.get("id").getAsString());
        }
        for (var row : JsonParser.parseString(defaults).getAsJsonObject().getAsJsonArray("features")) {
            var feature = row.getAsJsonObject();
            if (isEcology(feature) && ecologyIds.add(feature.get("id").getAsString())) features.add(feature);
        }
        return effective;
    }

    private static boolean isEcology(JsonObject feature) {
        return feature.get("layer").getAsString().equals("ecology");
    }
}
