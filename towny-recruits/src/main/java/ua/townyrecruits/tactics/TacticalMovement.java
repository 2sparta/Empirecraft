package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import ua.townyrecruits.Bridge;
import ua.townyrecruits.tactics.TacticalOrder.*;

/** Goals use normal navigation and movement; they never teleport recruits. */
public final class TacticalMovement {
    private static final Map<AbstractRecruitEntity, Unit> ACTIVE = new IdentityHashMap<>();
    private static final Set<AbstractRecruitEntity> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private TacticalMovement() {}
    static final class Cohort {
        final List<Unit> units = new ArrayList<>();
        int step; long lastTick = -1, started;
        final float travelYaw;
        boolean turning = true, finished;
        Cohort(long now, float travelYaw) { started = now; this.travelYaw = travelYaw; }
        void tick(long now) {
            if (lastTick == now || finished) return;
            lastTick = now;
            units.removeIf(u -> !u.r.m_6084_() || ACTIVE.get(u.r) != u);
            if (units.isEmpty()) { finished = true; return; }
            if (turning) {
                if (units.stream().allMatch(u -> Math.abs(TacticalMath.wrap(u.travelYaw - u.r.m_146908_())) < 6)) {
                    turning = false; started = now;
                }
                return;
            }
            if (units.stream().allMatch(u -> TacticalTerrain.near(u.r.m_20182_(), u.target(step), 0.38))) {
                step++; started = now;
                finished = units.stream().allMatch(u -> step >= u.route.size());
            } else if (now - started > 240) {
                // An obstruction appearing after the preview cannot stall the entire army forever.
                for (Unit u : List.copyOf(units)) if (!TacticalTerrain.near(u.r.m_20182_(), u.target(step), 0.6))
                    cancel(u.r);
                started = now;
            }
        }
    }
    static final class Unit {
        final AbstractRecruitEntity r;
        final List<Vec3> route;
        final float yaw, travelYaw;
        final Formation shape;
        final UUID commander;
        final Cohort cohort;
        int step; long lastProgress, repath;
        Vec3 lastPosition;
        Unit(TacticalPlanner.Assignment a, TacticalOrder order, UUID commander, Cohort cohort) {
            r = a.recruit(); route = a.marker().route(); yaw = a.marker().yaw();
            Formation saved = Formation.KEEP;
            try { saved = Formation.valueOf(r.getPersistentData().m_128461_("TRFormation")); } catch (IllegalArgumentException ignored) {}
            shape = order.formation() == Formation.KEEP || order.mode() == Mode.MARCH ? saved : order.formation();
            Vec3 delta = order.anchor().m_82546_(r.m_20182_());
            travelYaw = cohort == null ? (float)Math.toDegrees(Math.atan2(-delta.f_82479_, delta.f_82481_)) : cohort.travelYaw;
            this.commander = commander; this.cohort = cohort;
            lastPosition = r.m_20182_(); lastProgress = r.m_20193_().m_46467_();
        }
        Vec3 target(int index) { return route.get(Math.min(index, route.size() - 1)); }
    }
    public static void install(AbstractRecruitEntity r) {
        if (!INSTALLED.add(r)) return;
        r.f_21345_.m_25352_(-2, new MoveGoal(r));
        r.f_21345_.m_25352_(-1, new BraceGoal(r));
    }
    public static void execute(TacticalPlanner.Plan plan, UUID commander) {
        long now = plan.units().isEmpty() ? 0 : plan.units().get(0).recruit().m_20193_().m_46467_();
        Vec3 delta = plan.order().anchor().m_82546_(TacticalPlanner.center(plan.units().stream().map(TacticalPlanner.Assignment::recruit).toList()));
        Cohort cohort = plan.order().mode() == Mode.MARCH ? new Cohort(now, (float)Math.toDegrees(Math.atan2(-delta.f_82479_, delta.f_82481_))) : null;
        for (var assignment : plan.units()) {
            if (!assignment.marker().valid() || assignment.marker().route().isEmpty()) continue;
            AbstractRecruitEntity r = assignment.recruit(); cancel(r);
            r.setFollowState(0); r.setShouldBlock(false); r.setShouldRest(false);
            r.m_6710_(null); r.m_21573_().m_26573_();
            r.isInFormation = true; r.holdFormation = true;
            Unit unit = new Unit(assignment, plan.order(), commander, cohort);
            ACTIVE.put(r, unit); if (cohort != null) cohort.units.add(unit);
        }
    }
    public static void cancel(AbstractRecruitEntity r) {
        if (ACTIVE.remove(r) != null) { r.m_21573_().m_26573_(); r.setFollowState(2); }
        r.getPersistentData().m_128473_("TRBraced");
        if (r.getShouldBlock()) r.setShouldBlock(false);
    }
    public static void clear() { for (var r : List.copyOf(ACTIVE.keySet())) cancel(r); INSTALLED.clear(); }
    public static boolean moving(AbstractRecruitEntity r) { return ACTIVE.containsKey(r); }
    public static void cancelAll(List<AbstractRecruitEntity> recruits) { recruits.forEach(TacticalMovement::cancel); }
    static void face(AbstractRecruitEntity r, float wanted) {
        float yaw = TacticalMath.turn(r.m_146908_(), wanted, 8);
        r.m_146922_(yaw); r.m_5616_(yaw); r.m_5618_(yaw); r.ownerRot = yaw; r.rotateTicks = 0;
    }
    private static final class MoveGoal extends Goal {
        final AbstractRecruitEntity r; Unit unit;
        MoveGoal(AbstractRecruitEntity r) { this.r = r; m_7021_(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean m_8036_() { return ACTIVE.containsKey(r); }
        @Override public boolean m_8045_() { return ACTIVE.containsKey(r); }
        @Override public boolean m_183429_() { return true; }
        @Override public void m_8056_() { unit = ACTIVE.get(r); }
        @Override public void m_8041_() { r.m_21573_().m_26573_(); unit = null; }
        @Override public void m_8037_() {
            Unit u = ACTIVE.get(r); if (u == null) return;
            long now = r.m_20193_().m_46467_();
            if (!r.getIsOwned() || !u.commander.equals(r.getOwnerUUID()) || r.getFleeing()
                || r.needsToGetFood() || r.getShouldMount() || r.m_20202_() != null) { cancel(r); return; }
            if (u.cohort != null) {
                u.cohort.tick(now);
                if (u.cohort.turning) { r.m_21573_().m_26573_(); face(r, u.travelYaw); return; }
                u.step = u.cohort.step;
            } else if (u.step < u.route.size() && TacticalTerrain.near(r.m_20182_(), u.target(u.step), 0.3)) u.step++;
            boolean reached = u.cohort != null ? u.cohort.finished : u.step >= u.route.size();
            if (reached) {
                r.m_21573_().m_26573_(); face(r, u.yaw);
                if (Math.abs(TacticalMath.wrap(u.yaw - r.m_146908_())) < 4) {
                    ACTIVE.remove(r); r.setFollowState(3); r.setHoldPos(u.target(u.route.size() - 1));
                    r.ownerRot = u.yaw; r.rotateTicks = 200;
                    if (u.shape != Formation.KEEP) r.getPersistentData().m_128359_("TRFormation", u.shape.name());
                    r.getPersistentData().m_128350_("TRFormationYaw", u.yaw);
                    r.getPersistentData().m_128379_("TRBraced", u.shape == Formation.PIKE_WALL || u.shape == Formation.SHIELD_WALL);
                }
                return;
            }
            Vec3 target = u.target(u.step);
            // Recheck terrain as doors, blocks and fluids can change after confirmation.
            if (now >= u.repath) {
                u.repath = now + 10;
                if (TacticalTerrain.stand(r, target) == null) { cancel(r); return; }
                r.m_21573_().m_26519_(target.f_82479_, target.f_82480_, target.f_82481_, 0.85);
            }
            if (TacticalTerrain.near(r.m_20182_(), target, 1.2))
                r.m_21566_().m_6849_(target.f_82479_, target.f_82480_, target.f_82481_, 0.85);
            if (u.cohort != null) face(r, u.travelYaw);
            if (r.m_20182_().m_82554_(u.lastPosition) > 0.25) { u.lastPosition = r.m_20182_(); u.lastProgress = now; }
            if (now - u.lastProgress > 240) cancel(r);
        }
    }
    /** Two ranks of lowered pikes hold their slots and strike only the forward cone. */
    private static final class BraceGoal extends Goal {
        final AbstractRecruitEntity r; long nextAttack;
        BraceGoal(AbstractRecruitEntity r) { this.r = r; m_7021_(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean m_8036_() {
            return !moving(r) && r.getIsOwned() && r.getShouldHoldPos() && r.getHoldPos() != null
                && r.getPersistentData().m_128471_("TRBraced") && r.isInFormation && !r.needsToGetFood()
                && !r.getFleeing() && TacticalTerrain.near(r.m_20182_(), r.getHoldPos(), 0.7)
                && (TacticalEquipment.pike(r) || TacticalEquipment.shield(r));
        }
        @Override public boolean m_8045_() { return m_8036_(); }
        @Override public boolean m_183429_() { return true; }
        @Override public void m_8041_() { r.setShouldBlock(false); r.m_5810_(); }
        @Override public void m_8037_() {
            face(r, r.getPersistentData().m_128457_("TRFormationYaw")); r.m_21573_().m_26573_();
            r.setShouldBlock(true);
            if (TacticalEquipment.shield(r)) r.m_6672_(InteractionHand.OFF_HAND);
            long now = r.m_20193_().m_46467_(); if (now < nextAttack || r.getState() == 3) return;
            boolean pike = TacticalEquipment.pike(r); double reach = pike ? 3.6 : 1.8;
            double[] forward = TacticalMath.rotate(0, 1, r.m_146908_());
            LivingEntity target = r.m_20193_().m_6443_(LivingEntity.class, r.m_20191_().m_82400_(reach), e -> {
                double dx = e.m_20185_() - r.m_20185_(), dz = e.m_20189_() - r.m_20189_();
                double d = Math.sqrt(dx * dx + dz * dz);
                return e != r && e.m_6084_() && d <= reach && d > 0.05
                    && (dx * forward[0] + dz * forward[1]) / d > 0.65
                    && Math.abs(e.m_20186_() - r.m_20186_()) < 2 && Boolean.TRUE.equals(Bridge.combat(r, e))
                    && r.m_21574_().m_148306_(e);
            }).stream().min(Comparator.comparingDouble(r::m_20280_)).orElse(null);
            if (target != null) {
                r.m_6674_(InteractionHand.MAIN_HAND); r.m_7327_(target); nextAttack = now + (pike ? 30 : 20);
            }
        }
    }
}
