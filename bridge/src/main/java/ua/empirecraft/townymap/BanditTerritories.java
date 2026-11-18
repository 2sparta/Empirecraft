package ua.empirecraft.townymap;

import java.util.*;

/** Live, server-only territory. Discovery is spatially indexed and never saved to player history. */
final class BanditTerritories {
    static final BanditTerritories EMPTY = new BanditTerritories(Map.of());
    private record Band(String dimension, int size) {}
    private final Map<Band, Map<ClaimGeometry.Cell, Set<String>>> land = new HashMap<>();
    private final Map<MapMode, List<Claims>> views = new EnumMap<>(MapMode.class);
    private final Set<String> hiddenHistoryIds = new HashSet<>();

    BanditTerritories(Map<MapMode, Map<String, Claims>> source) {
        source.forEach((mode, rows) -> {
            var temporary = rows.values().stream().filter(c -> !c.banditNation().isEmpty()).toList();
            views.put(mode, temporary);
            temporary.forEach(c -> {
                hiddenHistoryIds.add(c.id());
                // Before 1.12.0, shared regional overlays did not include a bandit suffix.
                if (c.id().contains("/bandit/")) hiddenHistoryIds.add(c.id().substring(0, c.id().indexOf("/bandit/")));
            });
            // Only actual land triggers proximity; a capital icon can extend outside the border.
            if (mode != MapMode.KINGDOMS) return;
            temporary.stream().filter(c -> c.layer() == 0).forEach(c -> {
                if (c.cellSize() < 1 || c.cellSize() > 1024) throw new IllegalArgumentException("Invalid Towny cell size");
                var cells = land.computeIfAbsent(new Band(c.dimension(), c.cellSize()), ignored -> new HashMap<>());
                c.cells().forEach(cell -> cells.computeIfAbsent(cell, ignored -> new HashSet<>()).add(c.banditNation()));
            });
        });
    }

    Set<String> nearby(String dimension, double x, double z) {
        var visible = new HashSet<String>();
        land.forEach((band, cells) -> {
            if (!band.dimension().equals(dimension)) return;
            int size = band.size();
            // Include a cell whose far edge is exactly 16 blocks from the player.
            for (int cx = (int)Math.floor((x - 16) / size) - 1; cx <= (int)Math.floor((x + 16) / size); cx++)
                for (int cz = (int)Math.floor((z - 16) / size) - 1; cz <= (int)Math.floor((z + 16) / size); cz++) {
                    var owners = cells.get(new ClaimGeometry.Cell(cx, cz));
                    if (owners == null) continue;
                    double dx = Math.max(Math.max((double)cx * size - x, x - ((double)cx * size + size)), 0);
                    double dz = Math.max(Math.max((double)cz * size - z, z - ((double)cz * size + size)), 0);
                    if (dx * dx + dz * dz <= 16 * 16) visible.addAll(owners);
                }
        });
        return Set.copyOf(visible);
    }

    List<Claims> visible(MapMode mode, String dimension, Set<String> nearby) {
        return views.getOrDefault(mode, List.of()).stream()
                .filter(c -> c.dimension().equals(dimension) && nearby.contains(c.banditNation()))
                .map(c -> new Claims("temporary/" + c.id(), c.name(), c.dimension(), c.color(), c.cellSize(),
                        c.cells(), c.detail(), c.layer(), c.banditNation(), c.profile())).sorted(Comparator.comparing(Claims::id)).toList();
    }

    /** Also masks old ordinary-country memories when that land now belongs to bandits. */
    ClaimKnowledge.Snapshot history(ClaimKnowledge.Snapshot snapshot) {
        return new ClaimKnowledge.Snapshot(snapshot.format(), snapshot.observations().stream().map(original -> {
            var o = removeLegacy(original);
            var fragments = new ArrayList<ClaimKnowledge.Fragment>();
            for (var f : o.fragments()) {
                if (environment(f.label())) { fragments.add(f); continue; }
                if (hiddenHistoryIds.contains(f.label().id())) continue;
                var cells = new HashSet<ClaimGeometry.Cell>();
                for (var cell : f.cells()) if (!overlaps(f.label().dimension(), cell, f.label().cellSize())) cells.add(cell);
                if (!cells.isEmpty()) fragments.add(new ClaimKnowledge.Fragment(f.label(), cells));
            }
            return new ClaimKnowledge.Observation(o.chunk(), o.observedAt(), fragments);
        }).toList());
    }

    private static boolean legacyBandits(ClaimKnowledge.Label label) {
        // All modes were recorded together, so the original kingdom label identifies its towns and markers too.
        return label.mode() == MapMode.KINGDOMS && label.layer() == 0
                && (label.id().startsWith("nation/") || label.id().contains("/nation/")) && BanditNames.matches(label.name());
    }

    private static boolean environment(ClaimKnowledge.Label label) {
        String id=label.id();
        return switch(label.mode()) {
            case CLIMATE -> id.startsWith("climate/") || id.startsWith("earth/") || id.startsWith("sea/");
            case RAINFALL -> id.startsWith("rainfall/") || id.startsWith("earth/rain/") || id.startsWith("sea/");
            case SEASONS -> id.startsWith("terrain/") || id.startsWith("tfc/");
            default -> false;
        };
    }

    static ClaimKnowledge.Observation removeLegacy(ClaimKnowledge.Observation observation) {
        var hidden = observation.fragments().stream().filter(f -> legacyBandits(f.label())).toList();
        if (hidden.isEmpty()) return observation;
        var fragments = new ArrayList<ClaimKnowledge.Fragment>();
        for (var f : observation.fragments()) {
            if (environment(f.label())) { fragments.add(f); continue; }
            var cells = new HashSet<ClaimGeometry.Cell>();
            for (var cell : f.cells()) {
                int size = f.label().cellSize();
                long x = (long)cell.x() * size, z = (long)cell.z() * size;
                boolean overlap = hidden.stream().anyMatch(h -> h.cells().stream().anyMatch(other -> {
                    int step = h.label().cellSize();
                    long ox = (long)other.x() * step, oz = (long)other.z() * step;
                    return x < ox + step && x + size > ox && z < oz + step && z + size > oz;
                }));
                if (!overlap) cells.add(cell);
            }
            if (!cells.isEmpty()) fragments.add(new ClaimKnowledge.Fragment(f.label(), cells));
        }
        return new ClaimKnowledge.Observation(observation.chunk(), observation.observedAt(), fragments);
    }

    private boolean overlaps(String dimension, ClaimGeometry.Cell cell, int size) {
        int x = cell.x() * size, z = cell.z() * size;
        for (var entry : land.entrySet()) {
            Band band = entry.getKey();
            if (!dimension.equals(band.dimension())) continue;
            for (int cx = Math.floorDiv(x, band.size()); cx <= Math.floorDiv(x + size - 1, band.size()); cx++)
                for (int cz = Math.floorDiv(z, band.size()); cz <= Math.floorDiv(z + size - 1, band.size()); cz++)
                    if (entry.getValue().containsKey(new ClaimGeometry.Cell(cx, cz))) return true;
        }
        return false;
    }

}
