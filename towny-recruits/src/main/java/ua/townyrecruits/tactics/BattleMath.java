package ua.townyrecruits.tactics;

/** Pure combat geometry and timing shared by planning, AI and regression tests. */
public final class BattleMath {
    private BattleMath() {}
    public static double intercept(double dx, double dy, double dz, double vx, double vy, double vz, double speed) {
        double a = vx * vx + vy * vy + vz * vz - speed * speed;
        double b = 2 * (dx * vx + dy * vy + dz * vz), c = dx * dx + dy * dy + dz * dz;
        if (c < 1e-8 || speed <= 0 || !Double.isFinite(a + b + c)) return 0;
        double time = Math.sqrt(c) / speed;
        if (Math.abs(a) < 1e-8) { if (b < -1e-8) time = -c / b; }
        else {
            double disc = b * b - 4 * a * c;
            if (disc >= 0) {
                double first = (-b - Math.sqrt(disc)) / (2 * a), second = (-b + Math.sqrt(disc)) / (2 * a);
                if (first > 0 && second > 0) time = Math.min(first, second);
                else if (first > 0) time = first; else if (second > 0) time = second;
            }
        }
        return Math.max(0, Math.min(35, time));
    }
    public static double[] clamp(double x, double z, double radius) {
        double len = Math.hypot(x, z);
        return len > radius && len > 0 ? new double[]{x * radius / len, z * radius / len} : new double[]{x, z};
    }
    /** Minecraft arrows move, apply 0.99 drag and then 0.05 gravity each tick. */
    public static TacticalMath.Flight arrow(double dx, double dy, double dz, double speed) {
        for (int ticks = 1; ticks <= 60; ticks++) {
            double sum = (1 - Math.pow(0.99, ticks)) / 0.01;
            double fall = 0.05 * (ticks - sum) / 0.01;
            double x = dx / sum, y = (dy + fall) / sum, z = dz / sum;
            if (x * x + y * y + z * z <= speed * speed) return new TacticalMath.Flight(x, y, z, ticks);
        }
        return null;
    }
    public static double[] orbit(double angle, double radius, int direction, int index, int steps) {
        double a = angle + direction * Math.PI * index / steps;
        return new double[]{Math.cos(a) * radius, Math.sin(a) * radius};
    }
    public static int rank(int index, int count, TacticalOrder.Formation shape) {
        int cols = switch (shape) {
            case DOUBLE_LINE, PIKE_WALL, SHIELD_WALL, PASSAGE -> Math.max(1, (count + 1) / 2);
            case COLUMN -> Math.min(2, Math.max(1, count));
            case LINE, SKIRMISH -> Math.max(1, count);
            case WEDGE, CAVALRY_WEDGE -> 0;
            default -> Math.max(1, (int)Math.ceil(Math.sqrt(count)));
        };
        return cols == 0 ? (int)Math.floor(Math.sqrt(index)) : index / cols;
    }
    public static int[] ranks(double[] forward) {
        Integer[] order = new Integer[forward.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, java.util.Comparator.comparingDouble((Integer i) -> forward[i]).reversed());
        int[] ranks = new int[forward.length]; int rank = -1; double front = Double.POSITIVE_INFINITY;
        for (int i : order) { if (front - forward[i] > 0.4) { rank++; front = forward[i]; } ranks[i] = rank; }
        return ranks;
    }
    public static double speed(TacticalOrder.Speed speed, boolean retreat) {
        return retreat ? Math.min(0.5, speed.multiplier) : speed.multiplier;
    }
}
