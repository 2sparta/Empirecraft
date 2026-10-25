package com.quintonc.vs_sails.ship;

import java.util.BitSet;
import java.util.Random;

public final class SectionScanCursorTest {
    private static int assertions;

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void box(int minX, int minY, int minZ, int width, int height, int depth) {
        var cursor = new SectionScanCursor(minX, minY, minZ,
                minX + width - 1, minY + height - 1, minZ + depth - 1);
        BitSet visited = new BitSet(width * height * depth);
        int ticks = 0;
        while (!cursor.isDone()) {
            // Deliberately split in the middle of sections to exercise continuation.
            int budget = 1 + (ticks++ % 97);
            while (budget-- > 0 && !cursor.isDone()) {
                int x = cursor.x - minX, y = cursor.y - minY, z = cursor.z - minZ;
                if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth)
                    throw new AssertionError("position outside bounds");
                if ((cursor.x >> 4) != cursor.sectionX || (cursor.y >> 4) != cursor.sectionY
                        || (cursor.z >> 4) != cursor.sectionZ) throw new AssertionError("wrong section");
                int index = (y * depth + z) * width + x;
                if (visited.get(index)) throw new AssertionError("position visited twice");
                visited.set(index);
                cursor.advance();
            }
        }
        check(visited.cardinality() == width * height * depth, "complete box traversal");
        cursor.advance(); cursor.skipSection();
        check(cursor.isDone(), "finished cursor stays finished");
    }

    public static void main(String[] args) {
        box(0, 0, 0, 1, 1, 1);
        box(-17, -65, -1, 35, 34, 33);
        box(12000015, -64, -12000017, 33, 35, 34);
        Random random = new Random(1891);
        for (int i = 0; i < 100; i++)
            box(random.nextInt(129) - 64, random.nextInt(129) - 64, random.nextInt(129) - 64,
                    1 + random.nextInt(36), 1 + random.nextInt(36), 1 + random.nextInt(36));

        var sparse = new SectionScanCursor(0, 0, 0, 63, 63, 63);
        int blockReads = 0, emptySections = 0;
        while (!sparse.isDone()) {
            if (sparse.sectionX == 1 && sparse.sectionY == 2 && sparse.sectionZ == 3) {
                blockReads++; sparse.advance();
            } else { emptySections++; sparse.skipSection(); }
        }
        check(blockReads == 4096, "one occupied section needs exactly 4096 block reads");
        check(emptySections == 63, "empty sections are skipped once each");

        var allAir = new SectionScanCursor(-32, -32, -32, 31, 31, 31);
        int skipped = 0;
        while (!allAir.isDone()) { allAir.skipSection(); skipped++; }
        check(skipped == 64, "empty negative-coordinate sections");
        boolean rejected = false;
        try { new SectionScanCursor(1, 0, 0, 0, 0, 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid bounds rejected");
        System.out.println("PASS: " + assertions + " section traversal cases; sparse block reads="
                + blockReads + " versus 262144, skipped sections=" + emptySections);
    }
}
