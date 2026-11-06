package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import ua.townyrecruits.Bridge;

public final class TacticalAim {
    private static final Map<LivingEntity, Track> TRACKS = new WeakHashMap<>();
    private static final class Track { Vec3 position, velocity = Vec3.f_82478_; long tick; }
    private TacticalAim() {}
    public static Vec3 origin(AbstractRecruitEntity r) { return new Vec3(r.m_20185_(), r.m_20188_() - 0.1, r.m_20189_()); }
    public static Vec3 center(LivingEntity target) { return new Vec3(target.m_20185_(), target.m_20227_(0.55), target.m_20189_()); }
    public static Vec3 predict(AbstractRecruitEntity r, LivingEntity target, double projectileSpeed) {
        long now = r.m_20193_().m_46467_(); Vec3 point = center(target), origin = origin(r);
        Track track = TRACKS.computeIfAbsent(target, e -> new Track());
        if (track.position == null) { track.position = target.m_20182_(); track.tick = now; track.velocity = target.m_20184_(); }
        else if (now > track.tick) {
            Vec3 delta = target.m_20182_().m_82546_(track.position).m_82490_(1.0 / (now - track.tick));
            if (delta.m_82553_() > 1.5) delta = Vec3.f_82478_; // Teleports are not movement to extrapolate.
            track.velocity = track.velocity.m_82490_(0.65).m_82549_(delta.m_82490_(0.35));
            track.position = target.m_20182_(); track.tick = now;
        }
        Vec3 v = track.velocity, d = point.m_82546_(origin);
        double time = BattleMath.intercept(d.f_82479_, d.f_82480_, d.f_82481_, v.f_82479_, v.f_82480_, v.f_82481_, projectileSpeed);
        double fraction = 0.72 + r.m_217043_().m_188500_() * 0.18;
        double error = 0.12 + Math.min(0.5, d.m_82553_() * 0.006);
        if (TacticalActor.mounted(r)) error *= 1.5;
        return point.m_82549_(v.m_82490_(time * fraction)).m_82520_(
            r.m_217043_().m_188583_() * error, r.m_217043_().m_188583_() * error * 0.35,
            r.m_217043_().m_188583_() * error);
    }
    public static boolean clear(AbstractRecruitEntity r, LivingEntity target, Vec3 aim, double speed, boolean arrow) {
        Vec3 origin = origin(r);
        var flight = arrow ? arrow(r, aim, speed) : null;
        if (arrow && flight == null) return false;
        double lift = arrow ? Math.max(0, flight.y() * flight.y() / 0.1) : 0;
        AABB region = new AABB(origin, aim).m_82400_(lift + 1);
        var friends = r.m_20193_().m_6443_(LivingEntity.class, region, e -> {
            if (e == r || e == target || e == TacticalActor.body(r) || !e.m_6084_()) return false;
            AbstractRecruitEntity rider = TacticalActor.rider(e);
            return Boolean.FALSE.equals(Bridge.combat(r, rider == null ? e : rider));
        });
        Vec3 previous = origin;
        Vec3 velocity = arrow ? new Vec3(flight.x(), flight.y(), flight.z()) : aim.m_82546_(origin).m_82490_(1.0 / 12);
        for (int i = 1; i <= (arrow ? flight.ticks() : 12); i++) {
            Vec3 next = previous.m_82549_(velocity);
            var hit = r.m_20193_().m_45547_(new net.minecraft.world.level.ClipContext(previous, next,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, r));
            if (hit.m_6662_() == HitResult.Type.BLOCK && hit.m_82450_().m_82554_(aim) > 0.65) return false;
            for (LivingEntity friend : friends) {
                AABB box = friend.m_20191_().m_82400_(0.25);
                if (box.m_82371_(previous, next).isPresent()) return false;
            }
            previous = next;
            if (arrow) velocity = velocity.m_82490_(0.99).m_82520_(0, -0.05, 0);
        }
        return true;
    }
    public static TacticalMath.Flight arrow(AbstractRecruitEntity r, Vec3 aim, double speed) {
        Vec3 d = aim.m_82546_(origin(r));
        return BattleMath.arrow(d.f_82479_, d.f_82480_, d.f_82481_, speed);
    }
    public static void clear() { TRACKS.clear(); }
}
