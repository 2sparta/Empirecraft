package ua.empirecraft.townymap;

import java.util.*;

/** Server-only spatial index. Only clipped observations may leave this index. */
final class ClaimIndex {
    private record Band(String dimension, int size) {}
    private final Map<Band, Map<ClaimGeometry.Cell, List<ClaimKnowledge.Label>>> bands = new HashMap<>();

    ClaimIndex(Map<MapMode, Map<String, Claims>> views) {
        views.forEach((mode, claims) -> claims.values().forEach(c -> {
            // Temporary bandit territory must never become a permanent observation.
            if (!c.banditNation().isEmpty()) return;
            int size = c.cellSize();
            if (size < 1 || size > 1024) throw new IllegalArgumentException("Invalid Towny cell size");
            int observedSize = 16 % size == 0 ? size : size % 16 == 0 ? 16 : 1;
            var label = new ClaimKnowledge.Label(mode, c.id(), c.name(), c.dimension(), c.color(), observedSize, c.detail(), c.layer(), c.profile());
            var cells = bands.computeIfAbsent(new Band(c.dimension(), size), ignored -> new HashMap<>());
            c.cells().forEach(cell -> cells.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(label));
        }));
    }

    List<ClaimKnowledge.Fragment> observe(ClaimKnowledge.Chunk chunk) {
        Map<ClaimKnowledge.Label, Set<ClaimGeometry.Cell>> fragments = new HashMap<>();
        int minX = chunk.x() * 16, minZ = chunk.z() * 16, maxX = minX + 16, maxZ = minZ + 16;
        bands.forEach((band, cells) -> {
            if (!band.dimension().equals(chunk.dimension())) return;
            int size = band.size();
            for (int cx = Math.floorDiv(minX, size); cx <= Math.floorDiv(maxX - 1, size); cx++)
                for (int cz = Math.floorDiv(minZ, size); cz <= Math.floorDiv(maxZ - 1, size); cz++) {
                    var labels = cells.get(new ClaimGeometry.Cell(cx, cz));
                    if (labels == null) continue;
                    int left = (int)Math.max(minX, (long)cx * size), top = (int)Math.max(minZ, (long)cz * size);
                    int right = (int)Math.min(maxX, (long)cx * size + size), bottom = (int)Math.min(maxZ, (long)cz * size + size);
                    for (var label : labels) {
                        var observed = fragments.computeIfAbsent(label, ignored -> new HashSet<>());
                        int step = label.cellSize();
                        for (int x = left; x < right; x += step) for (int z = top; z < bottom; z += step)
                            observed.add(new ClaimGeometry.Cell(Math.floorDiv(x, step), Math.floorDiv(z, step)));
                    }
                }
        });
        return fragments.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().overlayId()))
                .map(e -> new ClaimKnowledge.Fragment(e.getKey(), e.getValue())).toList();
    }
}
