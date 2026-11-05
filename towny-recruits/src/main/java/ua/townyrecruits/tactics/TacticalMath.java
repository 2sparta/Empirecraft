package ua.townyrecruits.tactics;

import java.util.ArrayList;
import java.util.List;
import ua.townyrecruits.tactics.TacticalOrder.Formation;

/** Geometry and discrete projectile physics, independent of a running Minecraft world. */
public final class TacticalMath {
    private TacticalMath() {}
    public record Offset(double right, double forward, float facing) {}
    public record Flight(double x, double y, double z, int ticks) {}
    public static float wrap(float yaw) {
        return (float) (((yaw + 180) % 360 + 360) % 360 - 180);
    }
    public static float turn(float from, float to, float speed) {
        return from + Math.max(-speed, Math.min(speed, wrap(to - from)));
    }
    public static double[] rotate(double right, double forward, float yaw) {
        double a = Math.toRadians(yaw);
        return new double[]{Math.cos(a) * right - Math.sin(a) * forward,
                            Math.sin(a) * right + Math.cos(a) * forward};
    }
    public static List<Offset> layout(Formation shape, int count, double spacing) {
        if (count < 0 || count > TacticalOrder.MAX_UNITS || !Double.isFinite(spacing) || spacing <= 0)
            throw new IllegalArgumentException("Invalid formation dimensions");
        List<Offset> result = new ArrayList<>();
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
        if (shape == Formation.LINE || shape == Formation.SKIRMISH) cols = Math.max(1, count);
        if (shape == Formation.PIKE_WALL || shape == Formation.SHIELD_WALL) cols = Math.max(1, (count + 1) / 2);
        if (shape == Formation.COLUMN) cols = Math.min(2, Math.max(1, count));
        for (int i = 0; i < count; i++) {
            double x, z; float facing = 0;
            if (shape == Formation.SQUARE) {
                // Distribute around four full sides; the rear and flanks face outwards.
                int side = i % 4, row = i / 4, size = (count + 3 - side) / 4;
                double half = Math.max(1, Math.ceil(count / 4.0)) * spacing / 2;
                double along = (row - (size - 1) / 2.0) * spacing;
                double[] p = rotate(along, half, side * 90); x = p[0]; z = p[1]; facing = side * 90;
            } else if (shape == Formation.RING || shape == Formation.CIRCLE) {
                int ringCount = shape == Formation.CIRCLE ? Math.min(count, 8) : count;
                int ring = shape == Formation.CIRCLE ? i / 8 : 0;
                int inRing = Math.min(ringCount, count - ring * 8);
                double a = 2 * Math.PI * (i % ringCount) / Math.max(1, inRing);
                double radius = Math.max(spacing, ringCount * spacing / (2 * Math.PI)) * (ring + 1);
                x = Math.sin(a) * radius; z = Math.cos(a) * radius;
                facing = (float) -Math.toDegrees(a);
            } else if (shape == Formation.WEDGE) {
                int rank = (int)Math.floor(Math.sqrt(i)), first = rank * rank;
                int inRank = Math.min(2 * rank + 1, count - first);
                x = (i - first - (inRank - 1) / 2.0) * spacing; z = -rank * spacing;
            } else if (shape == Formation.VEE) {
                int rank = (i + 1) / 2;
                x = rank * (i % 2 == 0 ? -1 : 1) * spacing;
                z = -rank * spacing;
            } else {
                int row = i / cols, col = i % cols, rowSize = Math.min(cols, count - row * cols);
                x = (col - (rowSize - 1) / 2.0) * spacing;
                z = -row * spacing;
            }
            result.add(new Offset(x, z, facing));
        }
        if (result.isEmpty()) return result;
        double cx = result.stream().mapToDouble(Offset::right).average().orElse(0);
        double cz = result.stream().mapToDouble(Offset::forward).average().orElse(0);
        return result.stream().map(p -> new Offset(p.right - cx, p.forward - cz, p.facing)).toList();
    }
    /** Moonlight moves, multiplies by inertia, then subtracts gravity each tick. */
    public static Flight flight(double dx, double dy, double dz, double maxSpeed, double gravity,
                                double drag, int maxTicks) {
        if (!Double.isFinite(dx + dy + dz + maxSpeed + gravity + drag) || maxSpeed <= 0
            || gravity <= 0 || drag <= 0 || drag >= 1) return null;
        for (int ticks = 6; ticks <= maxTicks; ticks++) {
            double sum = (1 - Math.pow(drag, ticks)) / (1 - drag);
            double fall = gravity * (ticks - sum) / (1 - drag);
            double vx = dx / sum, vy = (dy + fall) / sum, vz = dz / sum;
            if (vx * vx + vy * vy + vz * vz <= maxSpeed * maxSpeed)
                return new Flight(vx, vy, vz, ticks);
        }
        return null;
    }
    public static double[] disk(double angleSample, double radiusSample, double radius) {
        double a = angleSample * 2 * Math.PI, r = Math.sqrt(radiusSample) * radius;
        return new double[]{Math.cos(a) * r, Math.sin(a) * r};
    }
    public static boolean cooling(long now, long readyAt) { return readyAt > now; }
    public static int bombRoom(int elsewhere) { return Math.max(0, 1 - elsewhere); }
}
