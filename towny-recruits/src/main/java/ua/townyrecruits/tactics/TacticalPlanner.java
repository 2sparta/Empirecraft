package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import ua.townyrecruits.tactics.TacticalOrder.*;

/** The same authoritative planner serves previews and actual orders. */
public final class TacticalPlanner {
    private TacticalPlanner() {}
    public enum Failure { NONE, NO_UNITS, RANGE, GROUND, PATH, FORMATION, PIKE, SHIELD, RANGED, NO_BOMB, COOLDOWN, ARC, BUSY, FRONT, POSITION }
    public record Marker(int entity, Vec3 from, Vec3 target, float yaw, Failure failure, List<Vec3> route) {
        public boolean valid() { return failure == Failure.NONE; }
    }
    public record Preview(int sequence, Mode mode, Formation formation, Vec3 anchor, float yaw,
                          boolean hasBombs, List<Marker> markers) {
        public long validCount() { return markers.stream().filter(Marker::valid).count(); }
    }
    public record Assignment(AbstractRecruitEntity recruit, Marker marker, RecruitBombs.Shot shot) {}
    public record Plan(TacticalOrder order, List<Assignment> units, boolean hasBombs) {
        public Preview preview(int sequence) {
            return new Preview(sequence, order.mode(), order.formation(), order.anchor(), order.yaw(), hasBombs,
                units.stream().map(a -> {
                    Marker m = a.marker(); List<Vec3> path = m.route();
                    if (path.size() > 32) {
                        List<Vec3> shortPath = new ArrayList<>();
                        for (int i = 0; i < 32; i++) shortPath.add(path.get(i * (path.size() - 1) / 31));
                        path = shortPath;
                    }
                    return new Marker(m.entity(), m.from(), m.target(), m.yaw(), m.failure(), path);
                }).toList());
        }
    }
    public static List<AbstractRecruitEntity> select(ServerPlayer p, List<UUID> groups) {
        return p.m_20193_().m_6443_(AbstractRecruitEntity.class,
            p.m_20191_().m_82400_(TacticalOrder.COMMAND_RANGE), r ->
                r.isEffectedByCommand(p.m_20148_()) && groups.contains(r.getGroup())
                    && r.m_20280_(p) <= TacticalOrder.COMMAND_RANGE * TacticalOrder.COMMAND_RANGE)
            .stream().sorted(Comparator.comparing(AbstractRecruitEntity::m_20148_))
            .limit(TacticalOrder.MAX_UNITS).toList();
    }
    public static Vec3 center(List<AbstractRecruitEntity> recruits) {
        return new Vec3(recruits.stream().mapToDouble(r -> r.m_20185_()).average().orElse(0),
            recruits.stream().mapToDouble(r -> r.m_20186_()).average().orElse(0),
            recruits.stream().mapToDouble(r -> r.m_20189_()).average().orElse(0));
    }
    public static Plan plan(ServerPlayer player, TacticalOrder order) {
        if (!order.sane() || player.m_5833_()) return new Plan(order, List.of(), false);
        List<AbstractRecruitEntity> recruits = select(player, order.groups());
        if (recruits.isEmpty()) return new Plan(order, List.of(), false);
        if (player.m_20238_(order.anchor()) > TacticalOrder.COMMAND_RANGE * TacticalOrder.COMMAND_RANGE)
            return new Plan(order, recruits.stream().map(r -> new Assignment(r,
                new Marker(r.m_19879_(), r.m_20182_(), order.anchor(), order.yaw(), Failure.RANGE, List.of()), null)).toList(), false);
        Vec3 center = center(recruits);
        boolean hasBombs = recruits.stream().anyMatch(r -> RecruitBombs.count(r.getInventory(), -1) > 0);
        boolean wall = order.formation() == Formation.PIKE_WALL || order.formation() == Formation.SHIELD_WALL;
        double spacing = Math.max(order.tight() || wall ? 0.85 : 1.35,
            recruits.stream().mapToDouble(r -> r.m_20205_() + 0.12).max().orElse(0.85));
        if (order.formation() == Formation.SKIRMISH) spacing = Math.max(3.0, spacing);
        List<TacticalMath.Offset> layout = TacticalMath.layout(order.formation(), recruits.size(), spacing);
        float originalYaw = recruits.get(0).getPersistentData().m_128441_("TRFormationYaw")
            ? recruits.get(0).getPersistentData().m_128457_("TRFormationYaw") : recruits.get(0).m_146908_();
        List<Vec3> commonRoute = List.of();
        if (order.mode() == Mode.MARCH) {
            AbstractRecruitEntity reference = recruits.stream().min(Comparator.comparingDouble(r -> r.m_20238_(center))).orElseThrow();
            Vec3 delta = reference.m_20182_().m_82546_(center);
            List<Vec3> base = TacticalTerrain.route(reference, order.anchor().m_82549_(delta));
            commonRoute = base.stream().map(v -> v.m_82546_(delta)).toList();
        }
        List<Assignment> assignments = new ArrayList<>();
        for (int i = 0; i < recruits.size(); i++) {
            AbstractRecruitEntity r = recruits.get(i);
            Failure failure = Failure.NONE;
            if (player.m_20238_(order.anchor()) > TacticalOrder.COMMAND_RANGE * TacticalOrder.COMMAND_RANGE
                || r.m_20238_(order.anchor()) > 128 * 128) failure = Failure.RANGE;
            if (r.getShouldMount() || r.m_20202_() != null || r.getFleeing() || r.needsToGetFood()
                || (order.mode().bomb() && TacticalMovement.moving(r))) failure = Failure.BUSY;
            TacticalMath.Offset offset = layout.get(i);
            double[] rotated;
            if (order.formation() == Formation.KEEP || order.mode() == Mode.MARCH) {
                double dx = r.m_20185_() - center.f_82479_, dz = r.m_20189_() - center.f_82481_;
                rotated = TacticalMath.rotate(dx, dz, TacticalMath.wrap(order.yaw() - originalYaw));
            } else rotated = TacticalMath.rotate(offset.right(), offset.forward(), order.yaw());
            Vec3 dest = order.anchor().m_82520_(rotated[0], 0, rotated[1]);
            float savedYaw = r.getPersistentData().m_128441_("TRFormationYaw") ? r.getPersistentData().m_128457_("TRFormationYaw") : r.m_146908_();
            float yaw = TacticalMath.wrap(order.yaw() + ((order.formation() == Formation.KEEP || order.mode() == Mode.MARCH) ?
                TacticalMath.wrap(savedYaw - originalYaw) : offset.facing()));
            if (order.mode() == Mode.FACE) dest = r.m_20182_();
            if (order.mode() == Mode.RETURN && r.getHoldPos() != null) dest = r.getHoldPos();
            if (order.mode() == Mode.RETURN && r.getHoldPos() == null) failure = Failure.POSITION;
            if (order.mode() == Mode.MARCH && !r.isInFormation) failure = Failure.FORMATION;
            if (!order.mode().bomb() && order.mode() != Mode.MARCH) {
                if (order.formation() == Formation.PIKE_WALL && !TacticalEquipment.pike(r)) failure = Failure.PIKE;
                if (order.formation() == Formation.SHIELD_WALL && !TacticalEquipment.shield(r)) failure = Failure.SHIELD;
                if (order.formation() == Formation.SKIRMISH && !TacticalEquipment.ranged(r)) failure = Failure.RANGED;
            }
            List<Vec3> route = List.of(); RecruitBombs.Shot shot = null;
            if (order.mode().bomb()) {
                dest = order.anchor();
                if (order.mode() == Mode.BOMB_LINE) {
                    // Preserve each thrower's lateral position; the selected point sets forward distance.
                    double[] right = TacticalMath.rotate(1, 0, order.yaw());
                    double side = (r.m_20185_() - center.f_82479_) * right[0] + (r.m_20189_() - center.f_82481_) * right[1];
                    dest = dest.m_82520_(right[0] * side, 0, right[1] * side);
                    double[] forward = TacticalMath.rotate(0, 1, order.yaw());
                    if ((dest.f_82479_ - center.f_82479_) * forward[0] + (dest.f_82481_ - center.f_82481_) * forward[1] < 4)
                        failure = Failure.FRONT;
                }
                Vec3 ground = TacticalTerrain.surface(r.m_20193_(), dest.f_82479_, dest.f_82481_, dest.f_82480_, r);
                if (ground == null) failure = Failure.GROUND; else dest = ground;
                if (RecruitBombs.count(r.getInventory(), -1) == 0) failure = Failure.NO_BOMB;
                else if (!RecruitBombs.ready(r)) failure = Failure.COOLDOWN;
                if (failure == Failure.NONE) {
                    shot = RecruitBombs.shot(r, dest);
                    if (shot == null) failure = Failure.ARC;
                    // The entire displayed scatter circle must be reachable, not just its center.
                    for (int edge = 0; edge < 8 && failure == Failure.NONE; edge++) {
                        double[] d = TacticalMath.disk(edge / 8.0, 1, TacticalOrder.SCATTER);
                        Vec3 point = TacticalTerrain.surface(r.m_20193_(), dest.f_82479_ + d[0], dest.f_82481_ + d[1], dest.f_82480_, r);
                        if (point == null || RecruitBombs.shot(r, point) == null) failure = Failure.ARC;
                    }
                }
            } else if (failure == Failure.NONE) {
                Vec3 ground = TacticalTerrain.stand(r, dest);
                if (ground == null) failure = Failure.GROUND;
                else {
                    dest = ground;
                    if (order.mode() == Mode.MARCH) {
                        List<Vec3> shifted = new ArrayList<>(); Vec3 prev = r.m_20182_();
                        for (Vec3 step : commonRoute) {
                            Vec3 next = step.m_82520_(r.m_20185_() - center.f_82479_, 0, r.m_20189_() - center.f_82481_);
                            List<Vec3> part = TacticalTerrain.segment(r, prev, next);
                            if (part.isEmpty()) { shifted.clear(); break; }
                            shifted.add(part.get(part.size() - 1)); prev = shifted.get(shifted.size() - 1);
                        }
                        if (!shifted.isEmpty()) {
                            List<Vec3> finish = TacticalTerrain.segment(r, prev, dest);
                            if (finish.isEmpty()) shifted.clear(); else shifted.add(dest);
                        }
                        route = shifted;
                    } else route = TacticalTerrain.route(r, dest);
                    if (route.isEmpty()) failure = Failure.PATH;
                }
            }
            assignments.add(new Assignment(r, new Marker(r.m_19879_(), r.m_20182_(), dest, yaw, failure, route), shot));
        }
        return new Plan(order, assignments, hasBombs);
    }
}
