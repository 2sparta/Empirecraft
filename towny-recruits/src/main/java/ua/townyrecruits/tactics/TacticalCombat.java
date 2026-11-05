package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import ua.townyrecruits.GunGoal;
import ua.townyrecruits.tactics.TacticalOrder.*;

/** Owns combat movement independently of the weapon goal, so mounted archers can fire while moving. */
public final class TacticalCombat {
    private static final Map<AbstractRecruitEntity, Manoeuvre> MANOEUVRES = new IdentityHashMap<>();
    private static final Set<AbstractRecruitEntity> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final class Manoeuvre {
        final Mode mode; final UUID owner; final Vec3 rally;
        LivingEntity target; Vec3 center, exit; double angle; int phase, index = 1, sign;
        long phaseAt; boolean shot;
        Manoeuvre(AbstractRecruitEntity r, Mode mode, LivingEntity target) {
            this.mode = mode; owner = r.getOwnerUUID(); rally = TacticalActor.position(r); this.target = target;
            sign = direction(r);
            reset(r, target);
        }
        void reset(AbstractRecruitEntity r, LivingEntity target) {
            this.target = target; center = target.m_20182_(); Vec3 p = TacticalActor.position(r);
            angle = Math.atan2(p.f_82481_ - center.f_82481_, p.f_82479_ - center.f_82479_);
            phase = 0; index = 1; shot = false; phaseAt = r.m_20193_().m_46467_();
        }
    }
    private TacticalCombat() {}
    public static void install(AbstractRecruitEntity r) { if (INSTALLED.add(r)) r.f_21345_.m_25352_(-1, new CombatGoal(r)); }
    public static boolean manoeuvring(AbstractRecruitEntity r) { return MANOEUVRES.containsKey(r); }
    public static boolean guard(AbstractRecruitEntity r) { return r.getPersistentData().m_128471_("TRGuard"); }
    public static double leash(AbstractRecruitEntity r) { return Math.max(1, Math.min(16, r.getPersistentData().m_128459_("TRLeash"))); }
    public static double distance(AbstractRecruitEntity r) { double d = r.getPersistentData().m_128459_("TRDistance"); return d < 6 ? 16 : Math.min(32, d); }
    public static Speed speed(AbstractRecruitEntity r) {
        int index = r.getPersistentData().m_128441_("TRSpeed") ? r.getPersistentData().m_128451_("TRSpeed") : Speed.NORMAL.ordinal();
        return Speed.values()[Math.max(0, Math.min(Speed.values().length - 1, index))];
    }
    public static void speed(AbstractRecruitEntity r, Speed speed) { r.getPersistentData().m_128405_("TRSpeed", speed.ordinal()); }
    public static Vec3 anchor(AbstractRecruitEntity r) {
        var data = r.getPersistentData();
        if (data.m_128441_("TRGuardX")) return new Vec3(data.m_128459_("TRGuardX"), data.m_128459_("TRGuardY"), data.m_128459_("TRGuardZ"));
        return r.getHoldPos() == null ? TacticalActor.position(r) : r.getHoldPos();
    }
    public static void anchor(AbstractRecruitEntity r, Vec3 point) {
        var d = r.getPersistentData(); d.m_128347_("TRGuardX", point.f_82479_); d.m_128347_("TRGuardY", point.f_82480_); d.m_128347_("TRGuardZ", point.f_82481_);
    }
    public static boolean controlsMovement(AbstractRecruitEntity r) {
        return guard(r) || r.getPersistentData().m_128471_("TRKeepDistance") || manoeuvring(r)
            || (TacticalEquipment.ranged(r) && r.getShouldRanged() && r.m_5448_() != null)
            || (TacticalActor.mounted(r) && r.getShouldHoldPos());
    }
    public static boolean firingPhase(AbstractRecruitEntity r) {
        Manoeuvre m = MANOEUVRES.get(r);
        return m == null || (m.mode == Mode.CAROUSEL && m.phase == 1);
    }
    public static void shot(AbstractRecruitEntity r) { Manoeuvre m = MANOEUVRES.get(r); if (m != null) m.shot = true; }
    public static boolean allowedTarget(AbstractRecruitEntity r, LivingEntity target) {
        return !guard(r) || TacticalEquipment.ranged(r)
            || TacticalActor.horizontal(anchor(r), target.m_20182_()) <= leash(r) + reach(r);
    }
    public static boolean enemy(AbstractRecruitEntity r, LivingEntity target) {
        return target != null && target != r && target != TacticalActor.body(r) && target.m_6084_()
            && r.getState() != 3 && r.shouldAttack(target) && allowedTarget(r, target);
    }
    public static LivingEntity nearest(AbstractRecruitEntity r, Vec3 point, double radius) {
        return r.m_20193_().m_6443_(LivingEntity.class, new net.minecraft.world.phys.AABB(point, point).m_82400_(radius), e -> enemy(r, e))
            .stream().min(Comparator.comparingDouble(e -> e.m_20238_(point))).orElse(null);
    }
    static int direction(AbstractRecruitEntity r) { return (r.getGroup().getLeastSignificantBits() & 1) == 0 ? 1 : -1; }
    /** First attack and withdrawal cycle, shared by the ground preview and movement AI. */
    public static List<Vec3> manoeuvreGeometry(AbstractRecruitEntity r, TacticalOrder order, Vec3 from) {
        LivingEntity target = nearest(r, order.anchor(), 8);
        if (target == null && enemy(r, r.m_5448_())) target = r.m_5448_();
        if (target == null) return List.of();
        Vec3 point = target.m_20182_(); int sign = direction(r);
        if (order.mode() == Mode.HIT_AND_RUN) {
            Vec3 delta = point.m_82546_(from); double length = Math.max(1, Math.hypot(delta.f_82479_, delta.f_82481_));
            return List.of(point.m_82520_(delta.f_82479_ / length * 6 - delta.f_82481_ / length * 3 * sign, 0,
                delta.f_82481_ / length * 6 + delta.f_82479_ / length * 3 * sign), from);
        }
        double angle = Math.atan2(from.f_82481_ - point.f_82481_, from.f_82479_ - point.f_82479_);
        double radius = Math.max(10, Math.min(24, order.distance())); List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= 8; i++) { double[] p = BattleMath.orbit(angle, radius, sign, i, 8); points.add(point.m_82520_(p[0], 0, p[1])); }
        for (int i = 0; i <= 8; i++) { double[] p = BattleMath.orbit(angle + sign * Math.PI, Math.min(32, radius + 8), sign, i, 8); points.add(point.m_82520_(p[0], 0, p[1])); }
        return points;
    }
    public static void action(AbstractRecruitEntity r, TacticalOrder order) {
        Mode mode = order.mode(); var d = r.getPersistentData();
        if (mode == Mode.SPEED) { speed(r, order.speed()); return; }
        if (mode == Mode.HOLD_FRONT) {
            d.m_128379_("TRGuard", true); d.m_128347_("TRLeash", order.leash()); anchor(r, TacticalActor.position(r));
            r.setFollowState(3); r.setHoldPos(TacticalActor.position(r)); r.holdFormation = true;
        } else if (mode == Mode.RELEASE_FRONT) { d.m_128379_("TRGuard", false); r.holdFormation = false; }
        else if (mode == Mode.KEEP_DISTANCE) {
            d.m_128379_("TRKeepDistance", true); d.m_128347_("TRDistance", order.distance());
            d.m_128379_("TRBraced", false); r.setShouldBlock(false); r.setFollowState(3); r.setHoldPos(TacticalActor.position(r));
        } else if (mode == Mode.CLOSE_DISTANCE) d.m_128379_("TRKeepDistance", false);
        else if (mode == Mode.END_MANOEUVRE) { MANOEUVRES.remove(r); TacticalActor.stop(r); r.setFollowState(3); r.setHoldPos(TacticalActor.position(r)); }
        TacticalActor.install(r);
    }
    public static void begin(AbstractRecruitEntity r, TacticalOrder order) {
        Mode mode = order.mode(); Vec3 targetPoint = order.anchor();
        LivingEntity target = nearest(r, targetPoint, 8);
        if (target == null && enemy(r, r.m_5448_())) target = r.m_5448_();
        if (target == null) return;
        cancel(r); TacticalMovement.cancel(r); r.setFollowState(3); r.setHoldPos(TacticalActor.position(r));
        r.getPersistentData().m_128347_("TRDistance", order.distance());
        r.getPersistentData().m_128379_("TRBraced", false); r.setShouldBlock(false); r.m_6710_(target);
        if (mode == Mode.CAROUSEL) { r.setShouldRanged(true); TacticalFire.hold(r, false); }
        MANOEUVRES.put(r, new Manoeuvre(r, mode, target)); TacticalActor.install(r);
    }
    public static void cancel(AbstractRecruitEntity r) {
        MANOEUVRES.remove(r); r.getPersistentData().m_128379_("TRGuard", false); r.getPersistentData().m_128379_("TRKeepDistance", false);
    }
    public static void stopManoeuvre(AbstractRecruitEntity r) { MANOEUVRES.remove(r); TacticalActor.stop(r); }
    public static void clear() { MANOEUVRES.clear(); INSTALLED.clear(); }
    public static void postTick(AbstractRecruitEntity r) {
        Float yaw = TacticalMovement.backpedal(r);
        if (yaw != null) {
            TacticalMovement.face(r, yaw);
            if (TacticalEquipment.shield(r)) r.m_6672_(InteractionHand.OFF_HAND);
            r.setShouldBlock(TacticalEquipment.shield(r) || TacticalEquipment.pike(r));
        }
    }
    static double reach(AbstractRecruitEntity r) { return TacticalEquipment.pike(r) ? 3.6 : TacticalActor.mounted(r) ? 3 : 1.9; }
    private static final class CombatGoal extends Goal {
        final AbstractRecruitEntity r; long nextMove, nextAttack; Vec3 last; long progress;
        CombatGoal(AbstractRecruitEntity r) { this.r = r; m_7021_(EnumSet.of(Flag.MOVE)); }
        @Override public boolean m_8036_() { return r.getIsOwned() && r.getListen() && !r.needsToGetFood() && !r.getFleeing()
            && TacticalActor.available(r) && !TacticalMovement.moving(r) && controlsMovement(r); }
        @Override public boolean m_8045_() { return m_8036_(); }
        @Override public boolean m_183429_() { return true; }
        @Override public void m_8056_() { last = TacticalActor.position(r); progress = r.m_20193_().m_46467_(); }
        @Override public void m_8041_() { TacticalActor.stop(r); }
        void move(Vec3 wanted, double speed) {
            Vec3 current = TacticalActor.position(r); long now = r.m_20193_().m_46467_();
            if (guard(r)) { Vec3 a = anchor(r); double[] p = BattleMath.clamp(wanted.f_82479_ - a.f_82479_, wanted.f_82481_ - a.f_82481_, leash(r)); wanted = new Vec3(a.f_82479_ + p[0], wanted.f_82480_, a.f_82481_ + p[1]); }
            Vec3 delta = wanted.m_82546_(current); double horizontal = Math.hypot(delta.f_82479_, delta.f_82481_);
            if (horizontal < 0.35) { TacticalActor.stop(r); return; }
            Vec3 step = current.m_82549_(delta.m_82490_(Math.min(1, 3 / horizontal)));
            Vec3 ground = TacticalTerrain.stand(r, step);
            if (ground == null || TacticalTerrain.segment(r, current, ground).isEmpty()) { TacticalActor.stop(r); return; }
            if (now >= nextMove) { nextMove = now + 8; TacticalActor.navigate(r, ground, speed); }
            TacticalActor.precision(r, ground, speed);
        }
        @Override public void m_8037_() {
            long now = r.m_20193_().m_46467_(); TacticalActor.install(r);
            Manoeuvre manoeuvre = MANOEUVRES.get(r);
            if (manoeuvre != null) {
                if (!Objects.equals(manoeuvre.owner, r.getOwnerUUID()) || !TacticalActor.mounted(r)) { finish(); return; }
                manoeuvre(manoeuvre, now); return;
            }
            LivingEntity target = r.m_5448_(); Vec3 here = TacticalActor.position(r);
            if (!enemy(r, target)) { if (target != null) r.m_6710_(null); if (guard(r)) move(anchor(r), speed(r).multiplier); else TacticalActor.stop(r); return; }
            if (guard(r) && TacticalActor.horizontal(here, anchor(r)) > leash(r) + 0.5) { move(anchor(r), speed(r).multiplier); return; }
            boolean ranged = TacticalEquipment.ranged(r) && r.getShouldRanged();
            if (!ranged) {
                double d = TacticalActor.horizontal(here, target.m_20182_());
                if (d <= reach(r) && now >= nextAttack && r.m_21574_().m_148306_(target)) {
                    r.m_6674_(InteractionHand.MAIN_HAND); r.m_7327_(target); nextAttack = now + 30;
                }
                if (r.getPersistentData().m_128471_("TRKeepDistance")) standoff(target, distance(r));
                else if (d > reach(r) - 0.3 && guard(r)) move(target.m_20182_(), speed(r).multiplier);
                else TacticalActor.stop(r);
                return;
            }
            if (r.getShouldFollow() && r.getOwner() != null && TacticalActor.horizontal(here, r.getOwner().m_20182_()) > 8) {
                move(r.getOwner().m_20182_(), speed(r).multiplier); return;
            }
            if (r.getPersistentData().m_128471_("TRKeepDistance")) standoff(target, distance(r));
            else if (guard(r) || r.getShouldHoldPos()) {
                Vec3 hold = guard(r) ? anchor(r) : r.getHoldPos();
                if (hold != null && TacticalActor.horizontal(here, hold) > 0.7) move(hold, speed(r).multiplier); else TacticalActor.stop(r);
            } else if (r.m_20280_(target) > 44 * 44) move(target.m_20182_(), speed(r).multiplier);
            else TacticalActor.stop(r);
        }
        void standoff(LivingEntity target, double wanted) {
            Vec3 here = TacticalActor.position(r), point = target.m_20182_();
            double dx = here.f_82479_ - point.f_82479_, dz = here.f_82481_ - point.f_82481_, distance = Math.hypot(dx, dz);
            if (Math.abs(distance - wanted) <= 1.5) { TacticalActor.stop(r); return; }
            if (distance < 0.01) { dx = 1; dz = 0; distance = 1; }
            move(new Vec3(point.f_82479_ + dx / distance * wanted, here.f_82480_, point.f_82481_ + dz / distance * wanted), speed(r).multiplier);
        }
        void finish() { MANOEUVRES.remove(r); TacticalActor.stop(r); r.m_6710_(null); r.setFollowState(3); r.setHoldPos(TacticalActor.position(r)); }
        void manoeuvre(Manoeuvre m, long now) {
            Vec3 here = TacticalActor.position(r);
            if (here.m_82554_(last) > 0.4) { last = here; progress = now; }
            if (now - progress > 240) { finish(); return; }
            if (!enemy(r, m.target)) {
                if (m.mode == Mode.HIT_AND_RUN) { move(m.rally, speed(r).multiplier); if (TacticalActor.horizontal(here, m.rally) < 1.5) finish(); return; }
                LivingEntity replacement = nearest(r, m.center, 24); if (replacement == null) { finish(); return; } m.reset(r, replacement);
            }
            r.m_6710_(m.target);
            if (m.mode == Mode.HIT_AND_RUN) {
                if (m.phase == 0) {
                    Vec3 target = m.target.m_20182_(), delta = target.m_82546_(m.rally); double len = Math.max(1, Math.hypot(delta.f_82479_, delta.f_82481_));
                    m.exit = target.m_82520_(delta.f_82479_ / len * 6 - delta.f_82481_ / len * 3 * m.sign, 0, delta.f_82481_ / len * 6 + delta.f_82479_ / len * 3 * m.sign);
                    move(m.exit, speed(r).multiplier);
                    if (TacticalActor.horizontal(here, target) <= reach(r) && r.m_21574_().m_148306_(m.target) && now >= nextAttack) {
                        r.m_6674_(InteractionHand.MAIN_HAND); r.m_7327_(m.target); nextAttack = now + 30; m.phase = 1; m.phaseAt = now;
                    } else if (TacticalActor.horizontal(here, m.exit) < 2 || now - m.phaseAt > 1200) { m.phase = 1; m.phaseAt = now; }
                } else if (m.phase == 1) {
                    move(m.exit, speed(r).multiplier);
                    if (TacticalActor.horizontal(here, m.exit) < 2 || now - m.phaseAt > 240) m.phase = 2;
                } else { move(m.rally, speed(r).multiplier); if (TacticalActor.horizontal(here, m.rally) < 1.5) finish(); }
                return;
            }
            double radius = Math.max(10, Math.min(24, distance(r)));
            if (m.target.m_20182_().m_82554_(m.center) > 8 && m.phase < 2) m.reset(r, m.target);
            double usedRadius = m.phase >= 2 ? Math.min(32, radius + 8) : radius;
            double[] offset = BattleMath.orbit(m.angle, usedRadius, m.sign, m.phase == 0 ? 0 : m.index, 8);
            Vec3 destination = m.center.m_82520_(offset[0], 0, offset[1]);
            move(destination, speed(r).multiplier);
            boolean arrived = TacticalActor.horizontal(here, destination) < 1.8;
            if (m.phase == 0 && arrived) { m.phase = 1; m.index = 1; m.shot = false; m.phaseAt = now; }
            else if (m.phase == 1) {
                if (arrived) m.index++;
                if (m.index > 8 || (m.shot && m.index >= 4) || now - m.phaseAt > 240) {
                    m.phase = 2; m.index = 1; m.angle = Math.atan2(here.f_82481_ - m.center.f_82481_, here.f_82479_ - m.center.f_82479_); m.phaseAt = now;
                }
            } else if (m.phase >= 2 && arrived) {
                m.index++;
                if (m.index > 8) { m.phase = 3; m.angle += m.sign * Math.PI; m.index = 1; }
                if (m.phase == 3 && TacticalFire.loaded(r)) m.reset(r, m.target);
            }
        }
    }
}
