package ua.townyrecruits.tactics;

import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Wire data contains intent only. The server selects soldiers and computes every destination. */
public record TacticalOrder(Mode mode, Formation formation, List<UUID> groups, Vec3 anchor,
                           float yaw, boolean tight, double width, Speed speed,
                           double leash, double distance, List<Waypoint> waypoints) {
    public TacticalOrder(Mode mode, Formation formation, List<UUID> groups, Vec3 anchor, float yaw, boolean tight) {
        this(mode, formation, groups, anchor, yaw, tight, 1, Speed.NORMAL, 2, 16, List.of());
    }
    public enum Speed {
        SLOW(0.5), NORMAL(0.85), QUICK(1.1), FAST(1.3);
        public final double multiplier;
        Speed(double value) { multiplier = value; }
    }
    public record Waypoint(Vec3 anchor, float yaw, Formation formation, double width) {
        public boolean sane() { return point(anchor) && Float.isFinite(yaw) && Math.abs(yaw) <= 360
            && formation != null && Double.isFinite(width) && width >= 0.6 && width <= 3; }
    }
    public enum Mode {
        MARCH, MOVE, HOLD, RETURN, ADVANCE, RETREAT, FACE, BOMB_AREA, BOMB_LINE,
        ROUTE, HOLD_FRONT, RELEASE_FRONT, FIRE_HOLD, FREE_FIRE, VOLLEY, RANK_FIRE,
        KEEP_DISTANCE, CLOSE_DISTANCE, HIT_AND_RUN, CAROUSEL, END_MANOEUVRE, SPEED, CANCEL;
        public boolean bomb() { return this == BOMB_AREA || this == BOMB_LINE; }
        public boolean action() { return switch (this) {
            case HOLD_FRONT, RELEASE_FRONT, FIRE_HOLD, FREE_FIRE, VOLLEY, RANK_FIRE,
                 KEEP_DISTANCE, CLOSE_DISTANCE, END_MANOEUVRE, SPEED, CANCEL -> true;
            default -> false;
        }; }
        public boolean fire() { return this == FIRE_HOLD || this == FREE_FIRE || this == VOLLEY || this == RANK_FIRE; }
        public boolean manoeuvre() { return this == HIT_AND_RUN || this == CAROUSEL; }
    }
    public enum Formation {
        KEEP, LINE, BLOCK, WEDGE, RING, SQUARE, VEE, CIRCLE, COLUMN,
        PIKE_WALL, SHIELD_WALL, SKIRMISH, DOUBLE_LINE, CAVALRY_WEDGE, PASSAGE
    }
    public static final int MAX_UNITS = 128, MAX_GROUPS = 16, MAX_WAYPOINTS = 8;
    public static final double COMMAND_RANGE = 96, SCATTER = 2.5;
    public boolean sane() {
        return mode != null && formation != null && groups != null && !groups.isEmpty()
            && groups.size() <= MAX_GROUPS && groups.stream().distinct().count() == groups.size()
            && point(anchor) && Float.isFinite(yaw) && Math.abs(yaw) <= 360
            && Double.isFinite(width) && width >= 0.6 && width <= 3 && speed != null
            && Double.isFinite(leash) && leash >= 1 && leash <= 16
            && Double.isFinite(distance) && distance >= 6 && distance <= 32
            && waypoints != null && waypoints.size() <= MAX_WAYPOINTS && waypoints.stream().allMatch(w -> w != null && w.sane())
            && (mode != Mode.ROUTE || !waypoints.isEmpty());
    }
    static boolean point(Vec3 p) { return p != null && Double.isFinite(p.f_82479_ + p.f_82480_ + p.f_82481_)
        && Math.abs(p.f_82479_) <= 30000000 && Math.abs(p.f_82481_) <= 30000000 && Math.abs(p.f_82480_) <= 4096; }
    public TacticalOrder leg(Waypoint point) {
        return new TacticalOrder(Mode.MOVE, point.formation(), groups, point.anchor(), point.yaw(), tight,
            point.width(), speed, leash, distance, List.of());
    }
    public static Formation nativeFormation(int index) {
        return switch (index) {
            case 1 -> Formation.LINE; case 2 -> Formation.BLOCK; case 3 -> Formation.WEDGE;
            case 4 -> Formation.RING; case 5 -> Formation.SQUARE; case 6 -> Formation.VEE;
            case 7 -> Formation.CIRCLE; case 8 -> Formation.COLUMN; default -> Formation.KEEP;
        };
    }
}
