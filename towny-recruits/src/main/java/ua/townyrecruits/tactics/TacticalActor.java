package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/** Orders drive the mount's navigation and reserve its movement goal, without teleporting riders. */
public final class TacticalActor {
    private static final Set<Mob> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private TacticalActor() {}
    public static boolean mounted(AbstractRecruitEntity r) {
        return r.m_20202_() instanceof AbstractHorse horse && horse.m_6688_() == r && horse.m_6084_();
    }
    public static Mob body(AbstractRecruitEntity r) { return mounted(r) ? (Mob)r.m_20202_() : r; }
    public static Vec3 position(AbstractRecruitEntity r) { return body(r).m_20182_(); }
    public static double width(AbstractRecruitEntity r) { return body(r).m_20205_(); }
    public static double height(AbstractRecruitEntity r) {
        Mob actor = body(r);
        return Math.max(actor.m_20206_(), r.m_20186_() + r.m_20206_() - actor.m_20186_());
    }
    public static boolean available(AbstractRecruitEntity r) { return r.m_20202_() == null || mounted(r); }
    public static AbstractRecruitEntity rider(Entity entity) {
        if (entity instanceof AbstractRecruitEntity r) return r;
        return entity instanceof AbstractHorse horse && horse.m_6688_() instanceof AbstractRecruitEntity r ? r : null;
    }
    public static void install(AbstractRecruitEntity r) {
        Mob actor = body(r);
        if (actor != r && INSTALLED.add(actor)) actor.f_21345_.m_25352_(-5, new RideGoal(actor));
    }
    public static void clear() { INSTALLED.clear(); }
    public static void stop(AbstractRecruitEntity r) {
        Mob actor = body(r); actor.m_21573_().m_26573_();
        if (actor != r) r.m_21573_().m_26573_();
        Vec3 p = actor.m_20182_(); actor.m_21566_().m_6849_(p.f_82479_, p.f_82480_, p.f_82481_, 0);
    }
    public static void navigate(AbstractRecruitEntity r, Vec3 target, double speed) {
        install(r); Mob actor = body(r);
        if (actor != r) r.m_21573_().m_26573_();
        actor.m_21573_().m_26519_(target.f_82479_, target.f_82480_, target.f_82481_, speed);
    }
    public static void precision(AbstractRecruitEntity r, Vec3 target, double speed) {
        Mob actor = body(r);
        if (TacticalTerrain.near(actor.m_20182_(), target, 1.5))
            actor.m_21566_().m_6849_(target.f_82479_, target.f_82480_, target.f_82481_, speed);
    }
    public static double horizontal(Vec3 a, Vec3 b) { return Math.hypot(a.f_82479_ - b.f_82479_, a.f_82481_ - b.f_82481_); }
    private static final class RideGoal extends Goal {
        final Mob mount;
        RideGoal(Mob mount) { this.mount = mount; m_7021_(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean m_8036_() {
            var r = rider(mount);
            return r != null && r.getIsOwned() && (TacticalMovement.moving(r) || TacticalCombat.controlsMovement(r));
        }
        @Override public boolean m_8045_() { return m_8036_(); }
        @Override public void m_8041_() { mount.m_21573_().m_26573_(); }
    }
}
