package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import ua.townyrecruits.tactics.TacticalOrder.*;

/** Intent-to-plan conversion: previews never change targets, inventories or navigation. */
public final class TacticalPlanner {
    private TacticalPlanner() {}
    public enum Failure { NONE, NO_UNITS, RANGE, GROUND, PATH, FORMATION, PIKE, SHIELD, RANGED,
        NO_BOMB, COOLDOWN, ARC, BUSY, FRONT, POSITION, MOUNT, TARGET, AMMO, FRONTLINE, MELEE }
    public record Marker(int entity, Vec3 from, Vec3 target, float yaw, Failure failure, List<Vec3> route, double footprint) {
        public Marker(int entity, Vec3 from, Vec3 target, float yaw, Failure failure, List<Vec3> route) { this(entity, from, target, yaw, failure, route, 0.75); }
        public boolean valid() { return failure == Failure.NONE; }
    }
    public record Preview(int sequence, Mode mode, Formation formation, Vec3 anchor, float yaw,
                          boolean hasBombs, List<Marker> markers, double width, Speed speed, int points) {
        public Preview(int sequence, Mode mode, Formation formation, Vec3 anchor, float yaw, boolean hasBombs, List<Marker> markers) {
            this(sequence, mode, formation, anchor, yaw, hasBombs, markers, 1, Speed.NORMAL, 0);
        }
        public long validCount() { return markers.stream().filter(Marker::valid).count(); }
    }
    public record Assignment(AbstractRecruitEntity recruit, Marker marker, RecruitBombs.Shot shot, int rank) {
        public Assignment(AbstractRecruitEntity recruit, Marker marker, RecruitBombs.Shot shot) { this(recruit, marker, shot, 0); }
    }
    public record Leg(TacticalOrder order, List<Assignment> units) {}
    public record Plan(TacticalOrder order, List<Assignment> units, boolean hasBombs, List<Leg> legs) {
        public Plan(TacticalOrder order, List<Assignment> units, boolean hasBombs) { this(order, units, hasBombs, List.of()); }
        public Preview preview(int sequence) {
            return new Preview(sequence, order.mode(), order.formation(), order.anchor(), order.yaw(), hasBombs,
                units.stream().map(a -> {
                    Marker m = a.marker(); List<Vec3> path = m.route();
                    if (path.size() > 64) {
                        List<Vec3> shortPath = new ArrayList<>();
                        for (int i = 0; i < 64; i++) shortPath.add(path.get(i * (path.size() - 1) / 63));
                        path = shortPath;
                    }
                    return new Marker(m.entity(), m.from(), m.target(), m.yaw(), m.failure(), path, m.footprint());
                }).toList(), order.width(), order.speed(), order.waypoints().size());
        }
    }
    public static List<AbstractRecruitEntity> select(ServerPlayer p, List<UUID> groups) {
        return p.m_20193_().m_6443_(AbstractRecruitEntity.class, p.m_20191_().m_82400_(TacticalOrder.COMMAND_RANGE), r ->
                r.isEffectedByCommand(p.m_20148_()) && groups.contains(r.getGroup())
                    && r.m_20280_(p) <= TacticalOrder.COMMAND_RANGE * TacticalOrder.COMMAND_RANGE)
            .stream().sorted(Comparator.comparing(AbstractRecruitEntity::m_20148_)).limit(TacticalOrder.MAX_UNITS).toList();
    }
    static Vec3 average(List<Vec3> positions) {
        return new Vec3(positions.stream().mapToDouble(p -> p.f_82479_).average().orElse(0),
            positions.stream().mapToDouble(p -> p.f_82480_).average().orElse(0), positions.stream().mapToDouble(p -> p.f_82481_).average().orElse(0));
    }
    public static Vec3 center(List<AbstractRecruitEntity> recruits) { return average(recruits.stream().map(TacticalActor::position).toList()); }
    static float heading(AbstractRecruitEntity r) { return r.getPersistentData().m_128441_("TRFormationYaw") ? r.getPersistentData().m_128457_("TRFormationYaw") : r.m_146908_(); }
    public static Plan plan(ServerPlayer player, TacticalOrder order) {
        if (!order.sane() || player.m_5833_()) return new Plan(order, List.of(), false);
        List<AbstractRecruitEntity> recruits = select(player, order.groups());
        boolean bombs = recruits.stream().anyMatch(r -> RecruitBombs.count(r.getInventory(), -1) > 0);
        if (recruits.isEmpty()) return new Plan(order, List.of(), bombs);
        if (!order.mode().action() && (player.m_20238_(order.anchor()) > TacticalOrder.COMMAND_RANGE * TacticalOrder.COMMAND_RANGE
            || order.waypoints().stream().anyMatch(p -> player.m_20238_(p.anchor()) > TacticalOrder.COMMAND_RANGE * TacticalOrder.COMMAND_RANGE))) {
            return new Plan(order, recruits.stream().map(r -> new Assignment(r, marker(r, TacticalActor.position(r), order.anchor(), order.yaw(), Failure.RANGE, List.of()), null)).toList(), bombs);
        }
        List<Vec3> from = new ArrayList<>(recruits.stream().map(TacticalActor::position).toList());
        List<Float> headings = new ArrayList<>(recruits.stream().map(TacticalPlanner::heading).toList());
        if (order.mode() != Mode.ROUTE) return new Plan(order, single(recruits, order, from, headings, null), bombs);
        List<Leg> legs = new ArrayList<>(); Failure[] failed = new Failure[recruits.size()]; Arrays.fill(failed, Failure.NONE);
        List<List<Vec3>> combined = new ArrayList<>(); for (int i = 0; i < recruits.size(); i++) combined.add(new ArrayList<>());
        List<Assignment> last = List.of();
        for (Waypoint point : order.waypoints()) {
            TacticalOrder leg = order.leg(point); last = single(recruits, leg, from, headings, failed); legs.add(new Leg(leg, last));
            for (int i = 0; i < recruits.size(); i++) {
                Marker m = last.get(i).marker();
                if (!m.valid()) failed[i] = m.failure();
                else { combined.get(i).addAll(m.route()); from.set(i, m.target()); headings.set(i, m.yaw()); }
            }
        }
        List<Assignment> result = new ArrayList<>();
        for (int i = 0; i < recruits.size(); i++) {
            Assignment a = last.get(i); Marker m = a.marker();
            result.add(new Assignment(a.recruit(), marker(a.recruit(), TacticalActor.position(a.recruit()), m.target(), m.yaw(), failed[i], combined.get(i)), null, a.rank()));
        }
        return new Plan(order, result, bombs, legs);
    }
    /** Revalidate an authorized leg without selecting a different group or requiring its owner to stand still. */
    public static Plan refresh(Leg leg, List<AbstractRecruitEntity> recruits) {
        List<Assignment> result = new ArrayList<>();
        // Keep the exact slots approved in the preview. Removing an obstructed recruit or
        // casualty must not recenter the remaining members into different destinations.
        for (Assignment original : leg.units()) {
            AbstractRecruitEntity r = original.recruit(); if (!recruits.contains(r) || !original.marker().valid()) continue;
            Vec3 from = TacticalActor.position(r), dest = original.marker().target(); List<Vec3> route = List.of();
            Failure failure = equipment(r, leg.order().formation());
            if (!TacticalActor.available(r) || r.getFleeing() || r.needsToGetFood() || (r.getShouldMount() && !TacticalActor.mounted(r))) failure = Failure.BUSY;
            if (failure == Failure.NONE) {
                Vec3 ground = TacticalTerrain.stand(r, dest);
                if (ground == null) failure = Failure.GROUND;
                else { dest = ground; route = TacticalTerrain.route(r, dest); if (route.isEmpty()) failure = Failure.PATH; }
            }
            result.add(new Assignment(r, marker(r, from, dest, original.marker().yaw(), failure, route), null, original.rank()));
        }
        return new Plan(leg.order(), List.copyOf(result), false);
    }
    private static Failure equipment(AbstractRecruitEntity r, Formation shape) {
        if (shape == Formation.PIKE_WALL && !TacticalEquipment.pike(r)) return Failure.PIKE;
        if (shape == Formation.SHIELD_WALL && !TacticalEquipment.shield(r)) return Failure.SHIELD;
        if (shape == Formation.SKIRMISH && !TacticalEquipment.ranged(r)) return Failure.RANGED;
        if (shape == Formation.CAVALRY_WEDGE && !TacticalActor.mounted(r)) return Failure.MOUNT;
        return Failure.NONE;
    }
    static Marker marker(AbstractRecruitEntity r, Vec3 from, Vec3 dest, float yaw, Failure fail, List<Vec3> path) {
        return new Marker(r.m_19879_(), from, dest, yaw, fail, List.copyOf(path), Math.max(0.75, TacticalActor.width(r)));
    }
    static List<Assignment> single(List<AbstractRecruitEntity> recruits, TacticalOrder order, List<Vec3> from,
                                   List<Float> headings, Failure[] inherited) {
        if (recruits.isEmpty()) return List.of();
        Vec3 center = average(from); float originalYaw = headings.get(0);
        Formation shape = order.mode() == Mode.RETREAT && order.formation() == Formation.KEEP ? Formation.PASSAGE : order.formation();
        boolean wall = shape == Formation.PIKE_WALL || shape == Formation.SHIELD_WALL || shape == Formation.PASSAGE;
        double bodyWidth = recruits.stream().mapToDouble(TacticalActor::width).max().orElse(0.6);
        double spacing = Math.max(order.tight() || wall ? 0.85 : 1.35, bodyWidth + 0.2);
        if (shape == Formation.CAVALRY_WEDGE) spacing = Math.max(2.2, spacing);
        if (shape == Formation.SKIRMISH) spacing = Math.max(3, spacing);
        var layout = TacticalMath.layout(shape, recruits.size(), spacing, order.width(), bodyWidth);
        List<Vec3> common = List.of();
        if (order.mode() == Mode.MARCH) {
            int reference = 0;
            for (int i = 1; i < from.size(); i++) if (from.get(i).m_82554_(center) < from.get(reference).m_82554_(center)) reference = i;
            Vec3 delta = from.get(reference).m_82546_(center);
            common = TacticalTerrain.routeFrom(recruits.get(reference), from.get(reference), order.anchor().m_82549_(delta)).stream().map(v -> v.m_82546_(delta)).toList();
        }
        List<Assignment> result = new ArrayList<>();
        for (int i = 0; i < recruits.size(); i++) {
            AbstractRecruitEntity r = recruits.get(i); Vec3 origin = from.get(i); var offset = layout.get(i);
            Failure failure = inherited == null ? Failure.NONE : inherited[i];
            boolean preserve = shape == Formation.KEEP || order.mode() == Mode.MARCH;
            double[] rotated;
            if (preserve) {
                double[] local = TacticalMath.rotate(origin.f_82479_ - center.f_82479_, origin.f_82481_ - center.f_82481_, -originalYaw);
                rotated = TacticalMath.rotate(local[0] * order.width(), local[1], order.yaw());
            } else rotated = TacticalMath.rotate(offset.right(), offset.forward(), order.yaw());
            Vec3 dest = order.anchor().m_82520_(rotated[0], 0, rotated[1]);
            float yaw = TacticalMath.wrap(order.yaw() + (preserve ? TacticalMath.wrap(headings.get(i) - originalYaw) : offset.facing()));
            List<Vec3> route = List.of(); RecruitBombs.Shot shot = null;
            if (order.mode().action()) {
                dest = origin;
                if (order.mode().fire() && !TacticalEquipment.ranged(r)) failure = Failure.RANGED;
                if (order.mode() == Mode.VOLLEY || order.mode() == Mode.RANK_FIRE) {
                    if (!TacticalEquipment.ammunition(r)) failure = Failure.AMMO;
                    if (!TacticalCombat.enemy(r, r.m_5448_()) && TacticalCombat.nearest(r, origin, 48) == null) failure = Failure.TARGET;
                }
            } else {
                if (!TacticalActor.available(r) || r.getFleeing() || r.needsToGetFood() || (r.getShouldMount() && !TacticalActor.mounted(r))) failure = Failure.BUSY;
                if (order.mode() == Mode.FACE) dest = origin;
                if (order.mode() == Mode.RETURN) { if (r.getHoldPos() == null) failure = Failure.POSITION; else dest = r.getHoldPos(); }
                if (order.mode() == Mode.MARCH && !r.isInFormation) failure = Failure.FORMATION;
                if (!order.mode().bomb() && !order.mode().manoeuvre() && order.mode() != Mode.MARCH) {
                    Failure weapon = equipment(r, shape); if (weapon != Failure.NONE) failure = weapon;
                }
                if (order.mode() == Mode.RETREAT && !TacticalEquipment.shield(r) && !TacticalEquipment.pike(r)) failure = Failure.FRONTLINE;
                if (order.mode().manoeuvre()) {
                    if (!TacticalActor.mounted(r)) failure = Failure.MOUNT;
                    if (order.mode() == Mode.CAROUSEL && !TacticalEquipment.ranged(r)) failure = Failure.RANGED;
                    if (order.mode() == Mode.HIT_AND_RUN && TacticalEquipment.ranged(r)) failure = Failure.MELEE;
                    if (TacticalCombat.nearest(r, order.anchor(), 8) == null && !TacticalCombat.enemy(r, r.m_5448_())) failure = Failure.TARGET;
                }
                if (order.mode().bomb()) {
                    dest = order.anchor();
                    if (order.mode() == Mode.BOMB_LINE) {
                        double[] right = TacticalMath.rotate(1, 0, order.yaw()), forward = TacticalMath.rotate(0, 1, order.yaw());
                        double side = (origin.f_82479_ - center.f_82479_) * right[0] + (origin.f_82481_ - center.f_82481_) * right[1];
                        dest = dest.m_82520_(right[0] * side, 0, right[1] * side);
                        if ((dest.f_82479_ - center.f_82479_) * forward[0] + (dest.f_82481_ - center.f_82481_) * forward[1] < 4) failure = Failure.FRONT;
                    }
                    Vec3 ground = TacticalTerrain.surface(r.m_20193_(), dest.f_82479_, dest.f_82481_, dest.f_82480_, r);
                    if (ground == null) failure = Failure.GROUND; else dest = ground;
                    if (TacticalMovement.moving(r)) failure = Failure.BUSY;
                    if (RecruitBombs.count(r.getInventory(), -1) == 0) failure = Failure.NO_BOMB;
                    else if (!RecruitBombs.ready(r)) failure = Failure.COOLDOWN;
                    if (failure == Failure.NONE) {
                        shot = RecruitBombs.shot(r, dest); if (shot == null) failure = Failure.ARC;
                        for (int edge = 0; edge < 8 && failure == Failure.NONE; edge++) {
                            double[] d = TacticalMath.disk(edge / 8.0, 1, TacticalOrder.SCATTER);
                            Vec3 point = TacticalTerrain.surface(r.m_20193_(), dest.f_82479_ + d[0], dest.f_82481_ + d[1], dest.f_82480_, r);
                            if (point == null || RecruitBombs.shot(r, point) == null) failure = Failure.ARC;
                        }
                    }
                } else if (failure == Failure.NONE && order.mode().manoeuvre()) {
                    var points = TacticalCombat.manoeuvreGeometry(r, order, origin);
                    List<Vec3> planned = new ArrayList<>(); Vec3 previous = origin;
                    for (Vec3 point : points) {
                        Vec3 ground = TacticalTerrain.stand(r, point);
                        if (ground == null) { failure = Failure.GROUND; break; }
                        var part = TacticalTerrain.routeFrom(r, previous, ground);
                        if (part.isEmpty()) { failure = Failure.PATH; break; }
                        planned.addAll(part); previous = ground;
                    }
                    dest = points.isEmpty() ? origin : previous; route = planned;
                } else if (failure == Failure.NONE) {
                    if (origin.m_82554_(dest) > 128) failure = Failure.RANGE;
                    else {
                        Vec3 ground = TacticalTerrain.stand(r, dest);
                        if (ground == null) failure = Failure.GROUND;
                        else {
                            dest = ground;
                            if (order.mode() == Mode.MARCH) {
                                List<Vec3> shifted = new ArrayList<>(); Vec3 prev = origin;
                                for (Vec3 step : common) {
                                    Vec3 next = step.m_82520_(origin.f_82479_ - center.f_82479_, 0, origin.f_82481_ - center.f_82481_);
                                    var part = TacticalTerrain.segment(r, prev, next);
                                    if (part.isEmpty()) { shifted.clear(); break; }
                                    shifted.add(part.get(part.size() - 1)); prev = shifted.get(shifted.size() - 1);
                                }
                                if (!shifted.isEmpty()) { if (TacticalTerrain.segment(r, prev, dest).isEmpty()) shifted.clear(); else shifted.add(dest); }
                                route = shifted;
                            } else route = TacticalTerrain.routeFrom(r, origin, dest);
                            if (route.isEmpty()) failure = Failure.PATH;
                        }
                    }
                }
            }
            if (inherited != null && inherited[i] != Failure.NONE) failure = inherited[i];
            result.add(new Assignment(r, marker(r, origin, dest, yaw, failure, route), shot, BattleMath.rank(i, recruits.size(), shape)));
        }
        return List.copyOf(result);
    }
}
