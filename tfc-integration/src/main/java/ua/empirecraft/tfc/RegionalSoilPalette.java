package ua.empirecraft.tfc;

/** Weighted soil textures for climate and regional soil groups. */
public final class RegionalSoilPalette {
    public enum Soil {
        CHERNOZEM, LOAM, SILTY_LOAM, SANDY_LOAM, SILT, PEAT, CLAY, SAND, WOOD, OTHER;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private record Mix(int black, int loam, int siltyLoam, int sandyLoam) {
        Soil pick(int roll) {
            if ((roll -= black) < 0) return Soil.CHERNOZEM;
            if ((roll -= loam) < 0) return Soil.LOAM;
            if ((roll -= siltyLoam) < 0) return Soil.SILTY_LOAM;
            if (roll < sandyLoam) return Soil.SANDY_LOAM;
            return Soil.SILT;
        }
    }

    // The remainder of each mix is silt.
    private static final Mix STEPPE = new Mix(64, 15, 12, 6);
    private static final Mix FOREST_STEPPE = new Mix(42, 17, 27, 10);
    private static final Mix DRY_STEPPE = new Mix(20, 25, 13, 37);
    private static final Mix CRIMEAN_STEPPE = new Mix(14, 41, 18, 24);
    private static final Mix PRAIRIE = new Mix(52, 24, 16, 6);
    private static final Mix PODZOL = new Mix(0, 17, 12, 51);
    private static final Mix MOUNTAIN = new Mix(0, 33, 43, 18);
    private static final Mix WETLAND = new Mix(0, 20, 25, 8);
    private static final Mix DRY_ALLUVIUM = new Mix(0, 26, 16, 36);
    private static final Mix ARID = new Mix(0, 26, 9, 56);
    private static final Mix TROPICAL = new Mix(0, 30, 48, 16);
    private static final Mix LUVISOL = new Mix(0, 26, 43, 14);
    private static final Mix ACIDIC = new Mix(0, 26, 20, 44);
    private static final Mix CALCAREOUS = new Mix(0, 47, 25, 17);
    private static final Mix TEMPERATE = new Mix(0, 38, 29, 19);

    private RegionalSoilPalette() {}

    public static Soil select(long seed, int x, int z, String soilClass, String zone, double mean, double rain,
                              boolean saturated, boolean upland) {
        Mix mix = switch (soilClass) {
            case "chernozem" -> STEPPE;
            case "chernozem_and_grey_forest" -> FOREST_STEPPE;
            case "southern_chernozem_and_kastanozem", "chernozem_and_kastanozem", "kastanozem" -> DRY_STEPPE;
            case "crimean_steppe" -> CRIMEAN_STEPPE;
            case "dry_fluvisol" -> DRY_ALLUVIUM;
            case "mollisol", "phaeozem" -> PRAIRIE;
            case "sod_podzolic", "podzol" -> PODZOL;
            case "brown_mountain_forest" -> MOUNTAIN;
            case "arid", "arenosol", "calcisol" -> ARID;
            case "ferralsol" -> TROPICAL;
            case "fluvisol", "gleysol" -> WETLAND;
            case "cambisol" -> TEMPERATE;
            case "luvisol" -> LUVISOL;
            case "acidic_cambisol" -> ACIDIC;
            case "rendzina" -> CALCAREOUS;
            default -> forClimate(zone, mean, rain);
        };
        if (upland && mix != PODZOL && mix != ARID && mix != ACIDIC && mix != CALCAREOUS && mix != CRIMEAN_STEPPE) mix = MOUNTAIN;
        if (saturated && mix != ARID) mix = rain >= 350 ? WETLAND : DRY_ALLUVIUM;
        return mix.pick(roll(seed, x, z));
    }

    private static Mix forClimate(String zone, double mean, double rain) {
        if (rain < 300 || zone.equals("desert")) return ARID;
        return switch (zone) {
            case "wetland" -> WETLAND;
            case "rainforest" -> TROPICAL;
            case "taiga", "tundra", "polar_desert" -> PODZOL;
            case "montane_forest" -> MOUNTAIN;
            case "dry_steppe" -> ARID;
            default -> mean >= 20 && rain >= 1200 ? TROPICAL : TEMPERATE;
        };
    }

    private static int roll(long seed, int x, int z) {
        // Warp patch edges in world coordinates and mix broad fields with smaller pockets.
        double warpX = 5 * Math.sin(z / 19.0) + 2 * Math.sin((x + (double) z) / 9);
        double warpZ = 5 * Math.sin(x / 17.0) + 2 * Math.sin((x - (double) z) / 11);
        int px = (int) Math.floor(x + warpX);
        int pz = (int) Math.floor(z + warpZ);
        long pocket = hash(seed ^ 0x1e34b51bL, Math.floorDiv(px, 6), Math.floorDiv(pz, 6));
        int scale = Math.floorMod(pocket, 5) < 2 ? 6 : 17;
        return Math.floorMod(hash(seed ^ scale, Math.floorDiv(px, scale), Math.floorDiv(pz, scale)), 100);
    }

    private static long hash(long seed, int x, int z) {
        return MaterialPalette.mix(seed ^ (long) x * 341873128712L ^ (long) z * 132897987541L);
    }
}
