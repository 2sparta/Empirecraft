import ua.empirecraft.tfc.SandPalette;
import ua.empirecraft.tfc.SandPalette.Location;
import ua.empirecraft.tfc.SandPalette.Terrain;

import static ua.empirecraft.tfc.SandPalette.Sediment.*;

public final class SandPaletteTest {
    private static final Terrain SHORE = new Terrain(63, false, true, 1);
    private static final Terrain DRY = new Terrain(78, false, false, 0);
    private static final long SEED = 4411L;
    private static int checked;

    public static void main(String[] args) {
        var papakolea = new Location(-155.6464, 18.936, 63, "");
        check(SandPalette.select(SEED, -31876, -3878, SHORE, papakolea) == GREEN);
        check(SandPalette.select(SEED, -31876, -3878, DRY, papakolea) == ORIGINAL);
        check(SandPalette.select(SEED, -31876, -3878, new Terrain(60, false, true, 7), papakolea) == ORIGINAL);
        check(SandPalette.select(SEED, -31876, -3878, new Terrain(63, true, false, 1), papakolea) != GREEN);
        check(SandPalette.select(SEED, -31876, -3878, SHORE, null) == ORIGINAL);
        check(SandPalette.select(SEED, -31876, -3878, SHORE, new Location(-155.6464, 18.936, 70, "")) == ORIGINAL);
        check(SandPalette.select(SEED, -31809, -3919, SHORE, new Location(-155.5049, 19.136, 63, "")) == BLACK);
        check(SandPalette.select(SEED, -15692, -5223, SHORE, new Location(-76.625, 25.502, 63, "")) == PINK);
        check(SandPalette.select(SEED, -31876, -3878, SHORE, new Location(25, 25, 63, "green")) == ORIGINAL);

        int red = 0;
        int redAcrossChunkEdges = 0;
        for (int x = -128; x < 128; x++) for (int z = -128; z < 128; z++) {
            var sahara = new Location(25, 25, 63, "");
            check(SandPalette.select(SEED, x, z, DRY, sahara) == ORIGINAL);
            var namib = new Location(15.5, -25.5, 63, "");
            var result = SandPalette.select(SEED, x, z, DRY, namib);
            if (result == RED) {
                red++;
                if (x % 16 == 0 && SandPalette.select(SEED, x - 1, z, DRY, namib) == RED) redAcrossChunkEdges++;
            }
            check(result == RED || result == ORIGINAL);
            check(SandPalette.depth(SEED, x, z) >= 1 && SandPalette.depth(SEED, x, z) <= 2);
        }
        check(red > 3000 && red < 40_000);
        check(redAcrossChunkEdges > 300);
        System.out.println("SAND_PALETTE_PASS assertions=" + checked + " red_border_columns=" + redAcrossChunkEdges);
    }

    private static void check(boolean passed) {
        checked++;
        if (!passed) throw new AssertionError("Sand palette check #" + checked);
    }
}
