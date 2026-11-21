package ua.empirecraft.tfc;

/** Weighted soil textures for climate and regional soil groups. */
public final class RegionalSoilPalette {
    public enum Soil {
        CHERNOZEM, LOAM, SILTY_LOAM, SANDY_LOAM, SILT, PEAT, CLAY, SAND, WOOD, OTHER;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private record Mix(Soil first, Soil second, Soil third, int primary, int secondary) {
        Soil pick(int roll) {
            if (roll < primary) return first;
            if (roll < primary + secondary) return second;
            return third;
        }
    }

    private static final Mix STEPPE = new Mix(Soil.CHERNOZEM, Soil.SILTY_LOAM, Soil.LOAM, 80, 12);
    private static final Mix FOREST_STEPPE = new Mix(Soil.CHERNOZEM, Soil.SILTY_LOAM, Soil.LOAM, 63, 27);
    private static final Mix DRY_STEPPE = new Mix(Soil.SANDY_LOAM, Soil.CHERNOZEM, Soil.LOAM, 48, 34);
    private static final Mix PRAIRIE = new Mix(Soil.CHERNOZEM, Soil.LOAM, Soil.SILTY_LOAM, 55, 28);
    private static final Mix PODZOL = new Mix(Soil.SANDY_LOAM, Soil.SILT, Soil.LOAM, 76, 16);
    private static final Mix MOUNTAIN = new Mix(Soil.SILTY_LOAM, Soil.LOAM, Soil.SANDY_LOAM, 50, 34);
    private static final Mix WETLAND = new Mix(Soil.SILT, Soil.SILTY_LOAM, Soil.LOAM, 62, 25);
    private static final Mix ARID = new Mix(Soil.SANDY_LOAM, Soil.LOAM, Soil.SILT, 88, 8);
    private static final Mix TROPICAL = new Mix(Soil.SILTY_LOAM, Soil.LOAM, Soil.SANDY_LOAM, 53, 29);
    private static final Mix LUVISOL = new Mix(Soil.SILTY_LOAM, Soil.SILT, Soil.LOAM, 66, 22);
    private static final Mix ACIDIC = new Mix(Soil.SANDY_LOAM, Soil.LOAM, Soil.SILTY_LOAM, 58, 30);
    private static final Mix CALCAREOUS = new Mix(Soil.LOAM, Soil.SILTY_LOAM, Soil.SILT, 72, 18);
    private static final Mix TEMPERATE = new Mix(Soil.LOAM, Soil.SILTY_LOAM, Soil.SANDY_LOAM, 54, 28);

    private RegionalSoilPalette() {}

    public static Soil select(long seed, int x, int z, String soilClass, String zone, double mean, double rain,
                              boolean saturated, boolean upland) {
        Mix mix = switch (soilClass) {
            case "chernozem" -> STEPPE;
            case "chernozem_and_grey_forest" -> FOREST_STEPPE;
            case "southern_chernozem_and_kastanozem", "chernozem_and_kastanozem", "kastanozem" -> DRY_STEPPE;
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
        if (upland && mix != PODZOL && mix != ARID && mix != ACIDIC && mix != CALCAREOUS) mix = MOUNTAIN;
        if (saturated && rain >= 350 && mix != ARID) mix = WETLAND;
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
        // Jitter the edges of six-block patches. Use floor division on the western and northern halves of the map.
        long local = MaterialPalette.mix(seed ^ (long)x * 341873128712L ^ (long)z * 132897987541L);
        int cellX = Math.floorDiv(x + (int)(local & 3) - 1, 6);
        int cellZ = Math.floorDiv(z + (int)(local >>> 2 & 3) - 1, 6);
        return Math.floorMod(MaterialPalette.mix(seed ^ (long)cellX * 341873128712L ^ (long)cellZ * 132897987541L), 100);
    }
}
