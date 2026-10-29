package ua.empirecraft.worldrules;

/** Weights within the ordinary pool. The entire rare pool has a separate probability. */
public final class TreeRules {
    private TreeRules() {}
    public static boolean useRare(double roll, boolean commonEmpty, boolean rareEmpty, double chance) {
        return !rareEmpty && (commonEmpty || roll < chance);
    }
    public static int weight(String id, String biome, float temperature) {
        if (!id.startsWith("minecraft:")) return 1;
        return switch (id) {
            case "minecraft:birch_sapling" -> biome.contains("birch") ? 8 : (biome.contains("forest") ? 2 : 1);
            case "minecraft:spruce_sapling" -> biome.contains("taiga") || biome.contains("grove") ? 8 : (temperature < .4f ? 3 : 0);
            case "minecraft:acacia_sapling" -> biome.contains("savanna") ? 8 : 0;
            case "minecraft:jungle_sapling" -> biome.contains("jungle") ? 8 : 0;
            case "minecraft:cherry_sapling" -> biome.contains("cherry") ? 8 : 0;
            case "minecraft:oak_sapling" -> biome.contains("birch") ? 1 : 5;
            default -> 1;
        };
    }
}
