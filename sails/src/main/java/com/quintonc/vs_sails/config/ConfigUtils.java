package com.quintonc.vs_sails.config;

import dev.architectury.platform.Platform;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigUtils {
    public static Map<String,String> config = new HashMap<>();


    public static Map<String,String> loadConfigs() {
        File file = new File(Platform.getConfigFolder().toString(), "vs_sails/config.cfg");
        Map<String, String> loaded = new HashMap<>();
        try {
            int lineNumber = 0;
            for (String raw : FileUtils.readLines(file, "utf-8")) {
                lineNumber++;
                String line = raw.replaceFirst("#.*", "").trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("=", 2);
                if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty())
                    throw new IllegalArgumentException("vs_sails/config.cfg: expected key = value on line " + lineNumber);
                loaded.put(parts[0].trim(), parts[1].trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
        config = loaded;
        validateConfig();
        return config;
    }

    public static void generateConfigs(List<String> input) {
        File file = new File(Platform.getConfigFolder().toString(), "vs_sails/config.cfg");
        try { FileUtils.writeLines(file, "utf-8", input); }
        catch (IOException e) { throw new IllegalStateException("Cannot write " + file, e); }
    }

    public static Map<String,String> checkConfigs() {
        File file = new File(Platform.getConfigFolder().toString(), "vs_sails/config.cfg");
        if (!file.exists()) generateConfigs(makeDefaults());
        return loadConfigs();
    }

    static void validateConfig() {
        bounded("wheel-interval", 6, 1, 360, true);
        bounded("max-assemble-blocks", 100000, 1, 1000000, true);
        bounded("sail-power", 25000, 0, 1000000, true);
        bounded("no-sail-zone", 90, 1, 359, true);
        bounded("rudder-power", 1, 0, 100, false);
        bounded("keel-power", 4, 0, 100, false);
        bounded("air-density", 124, 0, 2000, false);
        bounded("magic-ballast-righting-force", .25, 0, 10, false);
        bounded("ballast-float-strength", 0, 0, 10, false);
        bounded("buoy-float-strength", .125, 0, 10, false);
        bounded("water-stability-strength", 8, 0, 20, false);
        bounded("wave-strength", 1, 0, 2, false);
        bounded("minimum-hull-immersion", .35, .03, .8, false);
        bounded("puddle-dry-delay-seconds",60,1,3600,true);
        bounded("puddle-dry-interval-seconds",10,1,600,true);
        for (String key : List.of("puddle-drying", "ship-sounds", "enable-wind", "enable-aerodynamic-wind", "forgiving-sails", "realistic-rudder", "blow-vanilla-particles")) {
            if (config.containsKey(key) && !config.get(key).equalsIgnoreCase("true") && !config.get(key).equalsIgnoreCase("false"))
                throw new IllegalArgumentException("vs_sails/config.cfg: " + key + " must be true or false");
        }
    }

    private static void bounded(String key, double fallback, double min, double max, boolean integer) {
        String value = config.getOrDefault(key, Double.toString(fallback));
        try {
            double n = Double.parseDouble(value);
            if (!Double.isFinite(n) || n < min || n > max || (integer && n != Math.rint(n))) throw new NumberFormatException();
            // Preserve the integer syntax expected by the original consumers.
            if (integer) config.put(key, Integer.toString((int)n));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("vs_sails/config.cfg: " + key + " must be " + (integer ? "an integer " : "a finite number ") + "in [" + min + ", " + max + "] (got " + value + ")");
        }
    }

    private static List<String> makeDefaults()
    {
        List<String> defaults = new ArrayList<>();


        defaults.add("#Valkyrien Sails Configuration\n");
        defaults.add("#World Options");
        defaults.add("enable-wind = true #[Default: true]");
        defaults.add("enable-aerodynamic-wind = true #[Default: true] Enables wind to impart forces on ships without sails");
        //defaults.add("wind-shows-no-sails = true #[Default: true] Whether wind particles show when there are no set sails");
        defaults.add("no-sail-zone = 90 #[Default: 90] Angular size in degrees of zone where sails lose most of their effectiveness");
        //defaults.add("min-wind-speed = 0.2");
        defaults.add("blow-vanilla-particles = false #[Default: false] Should the wind blow vanilla particles");
        defaults.add("\n#Sail Options");
        defaults.add("sail-power = 25000 #[Default: 25000]");
        defaults.add("forgiving-sails = false #[Default: false] Enable this if you want the wind direction to matter less to the sails");
        defaults.add("\n#Helm Options");
        defaults.add("rudder-power = 1.0 #[Default: 1.0]");
        defaults.add("realistic-rudder = true #[Default: true] Is the rudder turning force dependent on the ship's speed");
        defaults.add("keel-power = 4.0 #[Default: 4.0] How strongly ships resist drifting");
        defaults.add("wheel-interval = 6 #[Default: 6] Degrees per tick, from 1 to 360; end stops are clamped.");
        defaults.add("air-density = 124.0 #[Default: 124.0] How dense the helm's rudder thinks air is");
        defaults.add("");
        defaults.add("magic-ballast-righting-force = 0.25 #[Default: 0.25]");
        defaults.add("");
        defaults.add("ballast-float-strength = 0.0 #[Default: 0.0]");
        defaults.add("buoy-float-strength = 0.125 #[Default: 0.125]");
        defaults.add("water-stability-strength = 8.0 #[Default: 8.0] Roll/pitch assistance in water; 0 disables");
        defaults.add("wave-strength = 1.0 #[Default: 1.0] Weather-driven rocking; 0 disables, maximum 2");
        defaults.add("minimum-hull-immersion = 0.35 #[Default: 0.35] Minimum displaced hull fraction for buoy-supported ships (0.03..0.8)");
        defaults.add("ship-sounds = true #[Default: true] Positional hull creaks and water sounds");
        defaults.add("puddle-drying = true # Shallow residual puddles, up to two buckets total");
        defaults.add("puddle-dry-delay-seconds = 60 # Quiet dry period before the first eighth evaporates");
        defaults.add("puddle-dry-interval-seconds = 10 # Seconds between evaporated eighths");
        defaults.add("");
        defaults.add("max-assemble-blocks = 100000 #[Default: 100000] The max number of blocks the dedication bottle can assemble");

        return defaults;
    }
}
