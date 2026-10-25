package com.quintonc.vs_sails.ship;

/** Visits a closed block box one chunk section at a time, without allocating positions. */
final class SectionScanCursor {
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final int minSectionX, minSectionZ, maxSectionX, maxSectionY, maxSectionZ;
    int sectionX, sectionY, sectionZ;
    int x, y, z;
    private int startX, startY, startZ, endX, endY, endZ;
    private boolean done;

    SectionScanCursor(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Empty scan bounds");
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        minSectionX = minX >> 4; minSectionZ = minZ >> 4;
        maxSectionX = maxX >> 4; maxSectionY = maxY >> 4; maxSectionZ = maxZ >> 4;
        sectionX = minSectionX; sectionY = minY >> 4; sectionZ = minSectionZ;
        startSection();
    }

    boolean isDone() { return done; }

    void advance() {
        if (done) return;
        if (x < endX) { x++; return; }
        x = startX;
        if (z < endZ) { z++; return; }
        z = startZ;
        if (y < endY) { y++; return; }
        skipSection();
    }

    void skipSection() {
        if (done) return;
        if (sectionX < maxSectionX) sectionX++;
        else {
            sectionX = minSectionX;
            if (sectionZ < maxSectionZ) sectionZ++;
            else {
                sectionZ = minSectionZ;
                if (sectionY < maxSectionY) sectionY++;
                else { done = true; return; }
            }
        }
        startSection();
    }

    private void startSection() {
        startX = Math.max(minX, sectionX << 4); endX = Math.min(maxX, (sectionX << 4) + 15);
        startY = Math.max(minY, sectionY << 4); endY = Math.min(maxY, (sectionY << 4) + 15);
        startZ = Math.max(minZ, sectionZ << 4); endZ = Math.min(maxZ, (sectionZ << 4) + 15);
        x = startX; y = startY; z = startZ;
    }
}
