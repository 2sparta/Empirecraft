package ua.townyrecruits.tactics;

import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Wire data contains intent only. The server selects soldiers and computes every destination. */
public record TacticalOrder(Mode mode, Formation formation, List<UUID> groups, Vec3 anchor,
                           float yaw, boolean tight) {
    public enum Mode {
        MARCH, MOVE, HOLD, RETURN, ADVANCE, RETREAT, FACE, BOMB_AREA, BOMB_LINE;
        public boolean bomb() { return this == BOMB_AREA || this == BOMB_LINE; }
    }
    public enum Formation {
        KEEP, LINE, BLOCK, WEDGE, RING, SQUARE, VEE, CIRCLE, COLUMN,
        PIKE_WALL, SHIELD_WALL, SKIRMISH
    }
    public static final int MAX_UNITS = 128, MAX_GROUPS = 16;
    public static final double COMMAND_RANGE = 96, SCATTER = 2.5;
    public boolean sane() {
        return mode != null && formation != null && groups != null && !groups.isEmpty()
            && groups.size() <= MAX_GROUPS && groups.stream().distinct().count() == groups.size()
            && anchor != null && Double.isFinite(anchor.f_82479_) && Double.isFinite(anchor.f_82480_)
            && Double.isFinite(anchor.f_82481_) && Float.isFinite(yaw) && Math.abs(yaw) <= 360;
    }
    public static Formation nativeFormation(int index) {
        return switch (index) {
            case 1 -> Formation.LINE; case 2 -> Formation.BLOCK; case 3 -> Formation.WEDGE;
            case 4 -> Formation.RING; case 5 -> Formation.SQUARE; case 6 -> Formation.VEE;
            case 7 -> Formation.CIRCLE; case 8 -> Formation.COLUMN; default -> Formation.KEEP;
        };
    }
}
