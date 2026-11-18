package ua.empirecraft.tfc;

/** Select sediment from world coordinates so a deposit can cross chunk boundaries. */
public final class SandPalette {
    public enum Sediment { ORIGINAL, RED, YELLOW, BROWN, BLACK, PINK, GREEN }

    public record Location(double lon, double lat, double seaLevel, String geologySand) {}
    public record Terrain(int y, boolean river, boolean beachOrOcean, int waterDepth) {}

    private SandPalette() {}

    public static Sediment select(long seed, int x, int z, Terrain terrain, Location earth) {
        double bands = noise(seed, x, z, 92, 53);
        double detail = noise(seed ^ 0x534544494d454e54L, x, z, 24, 17);
        double sediment = 0.72 * bands + 0.28 * detail;

        if (terrain.river()) {
            return terrain.waterDepth() <= 3 && sediment > 0.64 ? Sediment.BROWN : Sediment.ORIGINAL;
        }
        if (earth == null || Math.abs(earth.lon()) > 180 || Math.abs(earth.lat()) > 90) {
            return Sediment.ORIGINAL;
        }

        double elevation = terrain.y() - earth.seaLevel();
        if (elevation >= -4 && elevation <= 5 && terrain.waterDepth() <= 4) {
            // A few blocks of tolerance for the resolution of the imported coastline.
            if (site(earth, -155.6464, 18.936, 0.035, 0.024, detail)) return Sediment.GREEN; // Papakōlea
            if (site(earth, -155.5049, 19.136, 0.075, 0.050, detail)) return Sediment.BLACK; // Punaluʻu
            if (site(earth, -76.625, 25.502, 0.055, 0.040, detail)   // Harbour Island
                || site(earth, -64.819, 32.251, 0.043, 0.036, detail)) return Sediment.PINK; // Bermuda
            if (terrain.beachOrOcean() && sediment > 0.48
                && ("black".equals(earth.geologySand()) || in(earth, -18.5, 27.5, -13.3, 29.5))) {
                return Sediment.BLACK; // Icelandic geology and the Canary Islands.
            }
        }

        if (terrain.waterDepth() > 0 || terrain.beachOrOcean()) return Sediment.ORIGINAL;
        // Iron-rich dune fields. Unmapped deserts keep their original sand.
        if (sediment > 0.56 && (site(earth, 15.5, -25.5, 1.5, 2.5, detail)
            || site(earth, 129, -24.5, 10, 5.5, detail) || site(earth, 51, 20, 5, 3, detail))) return Sediment.RED;
        if ("yellow".equals(earth.geologySand()) && sediment > 0.68) return Sediment.YELLOW;
        return Sediment.ORIGINAL;
    }

    public static int depth(long seed, int x, int z) {
        return noise(seed ^ 0x4445505448L, x, z, 36, 25) > 0.48 ? 2 : 1;
    }

    private static boolean site(Location p, double lon, double lat, double width, double height, double detail) {
        double dx = (p.lon() - lon) / width;
        double dz = (p.lat() - lat) / height;
        return dx * dx + dz * dz <= 0.72 + 0.28 * detail;
    }

    private static boolean in(Location p, double west, double south, double east, double north) {
        return p.lon() >= west && p.lon() <= east && p.lat() >= south && p.lat() <= north;
    }

    private static double noise(long seed, int x, int z, int width, int length) {
        int cellX = Math.floorDiv(x, width);
        int cellZ = Math.floorDiv(z, length);
        double t = smooth(Math.floorMod(x, width) / (double) width);
        double u = smooth(Math.floorMod(z, length) / (double) length);
        double a = lerp(t, sample(seed, cellX, cellZ), sample(seed, cellX + 1, cellZ));
        double b = lerp(t, sample(seed, cellX, cellZ + 1), sample(seed, cellX + 1, cellZ + 1));
        return lerp(u, a, b);
    }

    private static double sample(long seed, int x, int z) {
        return (MaterialPalette.mix(seed ^ (341873128712L * x) ^ (132897987541L * z)) >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }
}
