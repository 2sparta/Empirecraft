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
        "fruitsdelight:cranberry_bush|wet", "fruitsdelight:lemon_tree|warm", "fruitsdelight:hamimelon|hot",
        "farmersdelight:brown_mushroom_colony|any", "farmersdelight:red_mushroom_colony|any",
        "ecologics:prickly_pear|hot", "spawn:sunflower|temperate", "spawn:tropical_wildflowers|warm");
    public static final List<String> DEFAULT_TREES = List.of(
        "minecraft:oak_sapling|temperate", "minecraft:birch_sapling|temperate",
        "minecraft:spruce_sapling|cold", "minecraft:acacia_sapling|hot",
        "minecraft:jungle_sapling|hot", "minecraft:cherry_sapling|temperate",
        "fruitsdelight:apple_sapling|temperate", "fruitsdelight:bayberry_sapling|warm",
        "fruitsdelight:durian_sapling|hot", "fruitsdelight:fig_sapling|warm",
        "fruitsdelight:hawberry_sapling|temperate", "fruitsdelight:kiwi_sapling|warm",
        "fruitsdelight:lychee_sapling|hot", "fruitsdelight:mango_sapling|hot",
        "fruitsdelight:mangosteen_sapling|hot", "fruitsdelight:orange_sapling|warm",
        "fruitsdelight:peach_sapling|temperate", "fruitsdelight:pear_sapling|temperate",
        "fruitsdelight:persimmon_sapling|warm", "vinery:apple_tree_sapling|temperate",
        "vinery:dark_cherry_sapling|temperate", "ecologics:walnut_sapling|temperate", "ecologics:coconut_seedling|hot",
        "spawn:date_sapling|hot");
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue RARE_TREE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WORLDS, PLANTS, TREES;
    public static final ForgeConfigSpec.IntValue INTERVAL, RADIUS, CHUNK_SAMPLES, MAX_PLANTS, PLANT_COOLDOWN, PLANT_BATCH, MAX_TREES, TREE_COOLDOWN;
    static {
        var b = new ForgeConfigSpec.Builder();
        WORLDS = b.comment("Bukkit world names. Towny must be enabled and available; unknown claim state never permits a change.")
            .defineListAllowEmpty("worlds", List.of("world", "world_europe"), v -> v instanceof String s && !s.isBlank());
        INTERVAL = b.defineInRange("intervalTicks", 100, 20, 72000);
        RADIUS = b.comment("Only already-loaded chunks around online players are sampled; no chunk generation.").defineInRange("radiusChunks", 2, 0, 8);
        CHUNK_SAMPLES = b.defineInRange("chunksPerInterval", 4, 1, 16);
        MAX_PLANTS = b.defineInRange("maxWildPlantsPerChunk", 20, 0, 64);
        PLANT_BATCH = b.defineInRange("plantsPerPass", 3, 1, 8);
        PLANT_COOLDOWN = b.defineInRange("plantCooldownTicks", 1200, 200, 2400000);
        MAX_TREES = b.comment("Maximum rooted trees/saplings per chunk, including existing vanilla trees.")
            .defineInRange("maxTreesPerChunk", 3, 0, 16);
        TREE_COOLDOWN = b.comment("Saplings start at their final native growth stage; native light/space/random-tick rules still apply.")
            .defineInRange("treeCooldownTicks", 1200, 200, 2400000);
        PLANTS = b.defineListAllowEmpty("plants", DEFAULT_PLANTS, RulesConfig::validPlant);
        RARE_TREE_CHANCE = b.comment("Combined chance for ALL fruit/mod trees; independent of how many species are installed.")
            .defineInRange("rareTreeChance", 0.10, 0.0, 1.0);
        TREES = b.defineListAllowEmpty("trees", DEFAULT_TREES, RulesConfig::validPlant);
        SPEC = b.build();
    }
    private static boolean validPlant(Object value) {
        return value instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\|(any|temperate|warm|hot|cold|wet)");
    }
    private RulesConfig() {}
}

