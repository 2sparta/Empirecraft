package ua.empirecraft.earth;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import com.google.gson.JsonParser;
import ua.empirecraft.tfc.RegionalSoilPalette;
import ua.empirecraft.tfc.RegionalSoilPalette.Soil;

public final class RegionalSoilTest {
    public static void main(String[] args) throws Exception {
        String defaults = Files.readString(Path.of(args[0]));
        var old = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonObject();
        // Retain an operator's existing profile and custom coordinate settings.
        var priorFeatures = old.getAsJsonArray("features");
        for (var entry : priorFeatures) {
            var feature = entry.getAsJsonObject();
            if (feature.get("id").getAsString().equals("ukraine_steppe")) feature.addProperty("label", "Local steppe");
        }
        old.addProperty("void_missing_chunks", false);
        var effective = SoilRegionDefaults.merge(old.toString(), defaults);
        var geography = new Geography(new StringReader(effective.toString()));
        require(!geography.voidMissingChunks, "local terrain option");
        require(geography.at("ecology", new Geography.Point(35, 48.5)).label().equals("Local steppe"), "local profile");
        var fresh = new Geography(new StringReader(defaults));
        require(fresh.features.size() == geography.features.size(), "all new profiles available in a legacy config");
        require(effective.equals(SoilRegionDefaults.merge(effective.toString(), defaults)), "repeatable config merge");

        verify(geography, 263, -9536, "western_europe_lowlands", 0, 0);
        verify(geography, 13627, -7646, "amudarya_alluvium", 0, 0);
        verify(geography, 1871, -11089, "jutland_sandy_plains", 0, 0);
        verify(geography, 7078, -9344, "crimean_steppe", 5, 24);
        verify(geography, (int) geography.x(35), (int) geography.z(48.5), "ukraine_steppe", 52, 76);
    }

    private static void verify(Geography geo, int x, int z, String id, double minBlack, double maxBlack) {
        var center = geo.coordinates(x, z);
        require(geo.at("ecology", center).id().equals(id), "landscape at " + x + ", " + z);
        var climate = geo.climate(center);
        var counts = new EnumMap<Soil, Integer>(Soil.class);
        for (long seed : new long[]{5, 9054, 364008502}) {
            for (int dx = -64; dx < 64; dx++) for (int dz = -64; dz < 64; dz++) {
                var point = geo.coordinates(x + dx, z + dz);
                Soil soil = RegionalSoilPalette.select(seed, x + dx, z + dz, geo.soilClass(point),
                    geo.zone(point), climate.mean(), climate.rain(), false, false);
                counts.merge(soil, 1, Integer::sum);
            }
        }
        int total = 3 * 128 * 128;
        double black = counts.getOrDefault(Soil.CHERNOZEM, 0) * 100.0 / total;
        require(black >= minBlack && black <= maxBlack, id + " black soil share " + black);
        for (Soil soil : new Soil[]{Soil.LOAM, Soil.SILTY_LOAM, Soil.SANDY_LOAM, Soil.SILT}) {
            require(counts.getOrDefault(soil, 0) > total * 0.01, id + " has " + soil);
        }
        System.out.printf(java.util.Locale.ROOT, "%s chernozem=%.1f%% %s%n", id, black, counts);
    }

    private static void require(boolean value, String condition) {
        if (!value) throw new AssertionError(condition);
    }
}
