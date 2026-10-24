package ua.empirecraft.worldrules;

import java.util.Set;

/** World-independent habitat and placement rules. */
public final class RenewalPolicy {
    private static final Set<String> SOILS = Set.of("minecraft:grass_block", "minecraft:dirt",
        "minecraft:coarse_dirt", "minecraft:podzol", "minecraft:mud", "minecraft:rooted_dirt",
        "minecraft:moss_block", "minecraft:mycelium", "minecraft:sand", "minecraft:red_sand");
    public static boolean naturalSoil(String id) { return SOILS.contains(id); }
    public static boolean plantSubstrate(String id) { return naturalSoil(id) || id.equals("minecraft:cactus"); }
    public static boolean climate(String climate, float temperature) {
        return switch (climate) {
            case "cold" -> temperature < .4f;
            case "hot" -> temperature >= .9f;
            case "warm" -> temperature >= .6f;
            case "temperate" -> temperature >= .2f && temperature < 1;
            default -> true;
        };
    }
    public static boolean space(boolean air, boolean sourceWater, boolean upperAir, boolean aquatic, boolean tall) {
        return (aquatic ? sourceWater : air) && (!tall || upperAir);
    }
    private RenewalPolicy() {}
}
