package ua.empirecraft.calendar;

/** Native biome spawn tables remain the habitat allowlist; this only changes their existing weights. */
public final class HabitatRules {
    private HabitatRules() {}
    public static double factor(String id, String profile, double seasonal, float temperature, String biome, boolean raining) {
        if (profile.equals("unchanged")) return 1;
        // Tropical habitats have milder winters; polar habitats retain the full calendar cycle.
        boolean warmBloodedSeason = profile.equals("insect") || profile.equals("reptile") || profile.equals("tropical") || profile.equals("wetland");
        if (temperature >= 1.2f && warmBloodedSeason) seasonal = Math.max(.65, seasonal);
        double habitat = 1;
        if (profile.equals("cold")) habitat = temperature < .4f ? 1.25 : .6;
        if (profile.equals("tropical") && temperature < .6f) habitat = .5;
        if (profile.equals("wetland") && (biome.contains("swamp") || biome.contains("marsh") || biome.contains("river")))
            habitat = raining ? 1.5 : 1.2;
        if (profile.equals("temperate") && (biome.contains("forest") || biome.contains("grove"))) habitat = 1.2;
        // More mod fauna during each species' favourable months, without adding foreign biome entries or raising mob caps.
        double modBonus = !id.startsWith("minecraft:") && seasonal > 1 ? 1.75 : 1;
        return Math.min(8, seasonal * habitat * modBonus);
    }
}
