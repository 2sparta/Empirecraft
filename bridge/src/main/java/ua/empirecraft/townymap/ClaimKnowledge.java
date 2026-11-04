package ua.empirecraft.townymap;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** A player's observations, not a reference to today's Towny estates. No Minecraft dependencies. */
final class ClaimKnowledge {
    static final int FORMAT = 1;
    record Chunk(String dimension, int x, int z) {
        Chunk {
            if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                    || dimension.length() > 256 || Math.abs((long)x) > 1_875_000 || Math.abs((long)z) > 1_875_000)
                throw new IllegalArgumentException("Invalid observation chunk");
        }
        static Chunk at(String dimension, int blockX, int blockZ) {
            return new Chunk(dimension, Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
        }
    }
    record Label(MapMode mode, String id, String name, String dimension, int color,
                 int cellSize, String detail, int layer) {
        Label {
            Objects.requireNonNull(mode); Objects.requireNonNull(id);
            Objects.requireNonNull(name); Objects.requireNonNull(dimension); Objects.requireNonNull(detail);
            if (cellSize < 1 || cellSize > 16 || 16 % cellSize != 0 || layer < 0 || layer > 4
                    || color < 0 || color > 0xffffff || id.length() > 2048 || name.length() > 16384 || detail.length() > 131072)
                throw new IllegalArgumentException("Invalid observation label");
        }
        String overlayId() {
            // Length prefixes avoid collisions between arbitrary player-authored names and delimiters.
            try {
                var bytes = new ByteArrayOutputStream();
                var out = new DataOutputStream(bytes);
                for (String value : List.of(mode.name(), id, name, dimension, detail)) {
                    byte[] raw = value.getBytes(StandardCharsets.UTF_8); out.writeInt(raw.length); out.write(raw);
                }
                out.writeInt(color); out.writeInt(cellSize); out.writeInt(layer);
                return mode.name() + "/memory/" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
            } catch (IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
    }
    record Fragment(Label label, Set<ClaimGeometry.Cell> cells) {
        Fragment { Objects.requireNonNull(label); cells = Set.copyOf(cells); }
    }
    record Observation(Chunk chunk, long observedAt, List<Fragment> fragments) {
        Observation {
            Objects.requireNonNull(chunk); fragments = List.copyOf(fragments);
            if (observedAt < 0) throw new IllegalArgumentException("Invalid observation time");
            for (Fragment fragment : fragments) {
                Label label = fragment.label();
                if (!chunk.dimension().equals(label.dimension()) || fragment.cells().isEmpty())
                    throw new IllegalArgumentException("Invalid observation fragment");
                for (var cell : fragment.cells()) {
                    long x = (long)cell.x() * label.cellSize(), z = (long)cell.z() * label.cellSize();
                    if (x < (long)chunk.x() * 16 || x + label.cellSize() > (long)chunk.x() * 16 + 16
                            || z < (long)chunk.z() * 16 || z + label.cellSize() > (long)chunk.z() * 16 + 16)
                        throw new IllegalArgumentException("Observation extends outside visited chunk");
                }
            }
        }
    }
    record Snapshot(int format, List<Observation> observations) {
        Snapshot { if (format != FORMAT) throw new IllegalArgumentException("Unsupported claim knowledge format"); observations = List.copyOf(observations); }
    }

    private final Map<Chunk, Observation> observations = new HashMap<>();
    private long revision;
    private boolean dirty;

    static ClaimKnowledge restore(Snapshot snapshot) {
        var knowledge = new ClaimKnowledge();
        for (Observation observation : snapshot.observations())
            if (knowledge.observations.putIfAbsent(observation.chunk(), observation) != null)
                throw new IllegalArgumentException("Duplicate observation chunk");
        return knowledge;
    }
    boolean observe(Chunk chunk, List<Fragment> fragments, long now) {
        Observation previous = observations.get(chunk);
        if (previous != null && previous.fragments().equals(fragments)) return false;
        observations.put(chunk, new Observation(chunk, now, fragments));
        revision++; dirty = true; return true;
    }
    Observation at(Chunk chunk) { return observations.get(chunk); }
    List<Label> labelsAt(Chunk chunk, MapMode mode, int blockX, int blockZ) {
        if (!Chunk.at(chunk.dimension(), blockX, blockZ).equals(chunk)) return List.of();
        Observation observation = observations.get(chunk);
        if (observation == null) return List.of();
        return observation.fragments().stream().filter(f -> f.label().mode() == mode)
                .filter(f -> f.cells().contains(new ClaimGeometry.Cell(Math.floorDiv(blockX, f.label().cellSize()), Math.floorDiv(blockZ, f.label().cellSize()))))
                .map(Fragment::label).distinct().toList();
    }
    long revision() { return revision; }
    boolean dirty() { return dirty; }
    void saved() { dirty = false; }
    Snapshot snapshot() {
        return new Snapshot(FORMAT, observations.values().stream().sorted(Comparator
                .comparing((Observation o) -> o.chunk().dimension()).thenComparingInt(o -> o.chunk().x())
                .thenComparingInt(o -> o.chunk().z())).toList());
    }
    static List<Claims> claims(Snapshot snapshot, MapMode mode) {
        Map<Label, Set<ClaimGeometry.Cell>> groups = new HashMap<>();
        for (Observation observation : snapshot.observations()) for (Fragment fragment : observation.fragments())
            if (fragment.label().mode() == mode)
                groups.computeIfAbsent(fragment.label(), ignored -> new HashSet<>()).addAll(fragment.cells());
        return groups.entrySet().stream().map(entry -> {
            Label l = entry.getKey();
            return new Claims(l.overlayId(), l.name(), l.dimension(), l.color(), l.cellSize(), entry.getValue(), l.detail(), l.layer());
        }).sorted(Comparator.comparing(Claims::id)).toList();
    }
}
