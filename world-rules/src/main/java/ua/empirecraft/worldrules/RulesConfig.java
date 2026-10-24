package ua.empirecraft.worldrules;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class RulesConfig {
    public static final List<String> DEFAULT_PLANTS = List.of(
        "farmersdelight:wild_cabbages|temperate", "farmersdelight:wild_onions|any",
        "farmersdelight:wild_tomatoes|warm", "farmersdelight:wild_carrots|temperate",
        "farmersdelight:wild_potatoes|temperate", "farmersdelight:wild_beetroots|any",
        "farmersdelight:wild_rice|wet", "farmersrespite:wild_tea_bush|warm",
        "farmersrespite:wild_coffee_bush|warm", "rusticdelight:wild_cotton|warm",
        "rusticdelight:wild_coffee|warm", "rusticdelight:wild_bell_peppers|warm",
        "supplementaries:wild_flax|temperate", "nirvana:wild_hemp|temperate",
        "vinery:red_grape_bush|temperate", "vinery:white_grape_bush|temperate",
        "vinery:taiga_grape_bush_red|cold", "vinery:taiga_grape_bush_white|cold",
        "vinery:jungle_grape_bush_red|hot", "vinery:jungle_grape_bush_white|hot",
        "vinery:savanna_grape_bush_red|hot", "vinery:savanna_grape_bush_white|hot",
        "fruitsdelight:wild_pineapple|hot", "fruitsdelight:blueberry_bush|temperate",
        "fruitsdelight:cranberry_bush|wet", "caupona:silphium_block|warm");
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WORLDS, PLANTS;
    public static final ForgeConfigSpec.IntValue INTERVAL, RADIUS, MAX_PLANTS, PLANT_COOLDOWN, ORE_SAMPLES, SALT_PERCENT, ORE_MIN_Y, ORE_MAX_Y;
    static {
        var b = new ForgeConfigSpec.Builder();
        WORLDS = b.comment("Bukkit world names. Towny must be enabled and available; unknown claim state never permits a change.")
            .defineListAllowEmpty("worlds", List.of("world", "world_europe"), v -> v instanceof String s && !s.isBlank());
        INTERVAL = b.defineInRange("intervalTicks", 200, 20, 72000);
        RADIUS = b.comment("At most one already-loaded chunk per world per interval is sampled around an online player.").defineInRange("radiusChunks", 2, 0, 8);
        MAX_PLANTS = b.defineInRange("maxWildPlantsPerChunk", 6, 0, 64);
        PLANT_COOLDOWN = b.defineInRange("plantCooldownTicks", 24000, 200, 2400000);
        ORE_SAMPLES = b.defineInRange("oreSamplesPerInterval", 256, 0, 2048);
        SALT_PERCENT = b.comment("Stable percentage of chunks eligible for ONE lifetime lapis-ore conversion. At least two connected ore blocks must remain.").defineInRange("saltEligibleChunkPercent", 25, 0, 100);
        ORE_MIN_Y = b.defineInRange("oreMinY", -48, -2048, 2048);
        ORE_MAX_Y = b.defineInRange("oreMaxY", 64, -2048, 2048);
        PLANTS = b.defineListAllowEmpty("plants", DEFAULT_PLANTS, v -> v instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\|(any|temperate|warm|hot|cold|wet)"));
        SPEC = b.build();
    }
    private RulesConfig() {}
}
