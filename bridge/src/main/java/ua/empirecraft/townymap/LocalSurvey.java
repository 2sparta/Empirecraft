package ua.empirecraft.townymap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** A per-player scan of nearby loaded terrain, nearest first and bounded per server tick. */
final class LocalSurvey {
    static final int RADIUS = 4;
    static final int PER_TICK = 12;
    private static final int RETRY_TICKS = 40;
    private static final int RENEW_TICKS = 1200;
    private record Offset(int x, int z) {}
    private static final List<Offset> OFFSETS = offsets();

    private final Set<ClaimKnowledge.Chunk> surveyed = new HashSet<>();
    private ClaimKnowledge.Chunk center;
    private Iterator<Offset> remaining = List.<Offset>of().iterator();
    private long revision, retryAt, renewAt;

    void visit(ClaimKnowledge.Chunk origin, long sourceRevision, long tick, Predicate<ClaimKnowledge.Chunk> sampleIfLoaded) {
        boolean moved = !origin.equals(center);
        boolean renew = center == null || revision != sourceRevision || tick >= renewAt;
        if (renew) {
            surveyed.clear();
            renewAt = tick + RENEW_TICKS;
        } else if (moved) {
            surveyed.removeIf(chunk -> !chunk.dimension().equals(origin.dimension())
                || Math.abs(chunk.x() - origin.x()) > RADIUS || Math.abs(chunk.z() - origin.z()) > RADIUS);
        }
        if (moved || renew || !remaining.hasNext() && tick >= retryAt) {
            center = origin;
            revision = sourceRevision;
            retryAt = tick + RETRY_TICKS;
            remaining = OFFSETS.iterator();
        }

        for (int attempts = 0; attempts < PER_TICK && remaining.hasNext();) {
            Offset offset = remaining.next();
            // Validate the projected world bounds even at the edge of Minecraft's coordinate range.
            var chunk = ClaimKnowledge.Chunk.tryAt(origin.dimension(), (origin.x() + offset.x()) * 16, (origin.z() + offset.z()) * 16);
            if (chunk == null || surveyed.contains(chunk)) continue;
            attempts++;
            if (sampleIfLoaded.test(chunk)) surveyed.add(chunk);
        }
    }

    private static List<Offset> offsets() {
        var result = new ArrayList<Offset>();
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) result.add(new Offset(x, z));
        result.sort(Comparator.comparingInt(w -> w.x() * w.x() + w.z() * w.z()));
        return List.copyOf(result);
    }
}
