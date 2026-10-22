package ua.empirecraft.townymap;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.*;

/** Exact union of Towny cells. Adjacent claims have no internal borders; wilderness remains a hole. */
final class ClaimGeometry {
    record Cell(int x, int z) {}
    record Point(int x, int z) {}
    record Polygon(List<Point> outer, List<List<Point>> holes) {}

    // JourneyMap 6.0.7 accepts at most 65,536 points per ring and 1,024 holes.
    // Stay comfortably below its decoder and Minecraft's custom payload size limits.
    static List<Polygon> buildBounded(Set<Cell> cells, int size) {
        List<Polygon> polygons = build(cells, size);
        boolean bounded = polygons.stream().allMatch(p -> p.holes.size() <= 512
                && p.outer.size() + p.holes.stream().mapToInt(List::size).sum() <= 8192);
        if (bounded) return polygons;
        int minX = cells.stream().mapToInt(Cell::x).min().orElseThrow();
        int maxX = cells.stream().mapToInt(Cell::x).max().orElseThrow();
        int minZ = cells.stream().mapToInt(Cell::z).min().orElseThrow();
        int maxZ = cells.stream().mapToInt(Cell::z).max().orElseThrow();
        boolean splitX = (long) maxX - minX >= (long) maxZ - minZ;
        long middle = Math.floorDiv(splitX ? (long) minX + maxX : (long) minZ + maxZ, 2L);
        Set<Cell> left = new HashSet<>(), right = new HashSet<>();
        cells.forEach(c -> ((splitX ? c.x : c.z) <= middle ? left : right).add(c));
        if (left.isEmpty() || right.isEmpty()) throw new IllegalStateException("Cannot split oversized territory");
        List<Polygon> result = new ArrayList<>(buildBounded(left, size));
        result.addAll(buildBounded(right, size));
        return List.copyOf(result);
    }

    static List<Polygon> build(Set<Cell> cells, int size) {
        if (cells.isEmpty()) return List.of();
        Map<Integer, SortedSet<Integer>> rows = new TreeMap<>();
        cells.forEach(c -> rows.computeIfAbsent(c.z, k -> new TreeSet<>()).add(c.x));
        // Union the entire path once instead of Area.add for every individual claim.
        Path2D.Double runs = new Path2D.Double(Path2D.WIND_NON_ZERO);
        rows.forEach((z, xs) -> {
            Integer start = null, last = null;
            for (int x : xs) {
                if (start != null && (long) x != (long) last + 1) {
                    appendRun(runs, start, last, z, size);
                    start = null;
                }
                if (start == null) start = x;
                last = x;
            }
            appendRun(runs, start, last, z, size);
        });
        List<List<Point>> rings = new ArrayList<>();
        List<Point> ring = null;
        double[] p = new double[6];
        for (PathIterator it = new Area(runs).getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(p)) {
                case PathIterator.SEG_MOVETO -> { ring = new ArrayList<>(); ring.add(point(p)); }
                case PathIterator.SEG_LINETO -> ring.add(point(p));
                case PathIterator.SEG_CLOSE -> {
                    List<Point> simplified = simplify(ring);
                    if (simplified.size() >= 3) rings.add(simplified);
                }
                default -> throw new IllegalStateException("A claim boundary cannot contain curves");
            }
        }
        // Area normalises ring winding; the largest ring must be an exterior.
        double outerSign = Math.signum(signedArea(rings.stream()
                .max(Comparator.comparingDouble(r -> Math.abs(signedArea(r)))).orElseThrow()));
        List<List<Point>> outers = new ArrayList<>(), holes = new ArrayList<>();
        for (List<Point> r : rings) (Math.signum(signedArea(r)) == outerSign ? outers : holes).add(r);
        List<Polygon> result = new ArrayList<>();
        for (List<Point> outer : outers) result.add(new Polygon(outer, new ArrayList<>()));
        for (List<Point> hole : holes) {
            Point p0 = hole.get(0);
            Polygon owner = result.stream().filter(poly -> path(poly.outer).contains(p0.x, p0.z))
                    .min(Comparator.comparingDouble(poly -> Math.abs(signedArea(poly.outer))))
                    .orElseThrow(() -> new IllegalStateException("Hole without an enclosing claim boundary"));
            owner.holes.add(hole);
        }
        result.sort(Comparator.comparingInt((Polygon poly) -> poly.outer.get(0).z)
                .thenComparingInt(poly -> poly.outer.get(0).x));
        return result.stream().map(poly -> new Polygon(poly.outer, List.copyOf(poly.holes))).toList();
    }

    private static void appendRun(Path2D.Double path, int first, int last, int z, int size) {
        int x1 = Math.multiplyExact(first, size), x2 = Math.multiplyExact(Math.addExact(last, 1), size);
        int z1 = Math.multiplyExact(z, size), z2 = Math.multiplyExact(Math.addExact(z, 1), size);
        path.moveTo(x1, z1); path.lineTo(x2, z1); path.lineTo(x2, z2); path.lineTo(x1, z2); path.closePath();
    }

    private static Point point(double[] p) { return new Point((int) Math.round(p[0]), (int) Math.round(p[1])); }

    private static List<Point> simplify(List<Point> ring) {
        if (ring.size() > 1 && ring.get(0).equals(ring.get(ring.size() - 1))) ring.remove(ring.size() - 1);
        List<Point> result = new ArrayList<>();
        for (int i = 0; i < ring.size(); i++) {
            Point a = ring.get((i + ring.size() - 1) % ring.size()), b = ring.get(i), c = ring.get((i + 1) % ring.size());
            if ((a.x == b.x && b.x == c.x) || (a.z == b.z && b.z == c.z)) continue;
            result.add(b);
        }
        return List.copyOf(result);
    }

    static double signedArea(List<Point> ring) {
        double area = 0;
        for (int i = 0; i < ring.size(); i++) {
            Point a = ring.get(i), b = ring.get((i + 1) % ring.size());
            area += (double) a.x * b.z - (double) b.x * a.z;
        }
        return area / 2;
    }

    static Path2D.Double path(List<Point> ring) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(ring.get(0).x, ring.get(0).z);
        for (int i = 1; i < ring.size(); i++) p.lineTo(ring.get(i).x, ring.get(i).z);
        p.closePath();
        return p;
    }
}
