package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** A route advances only after its surviving members have finished the current formation. */
public final class TacticalRoutes {
    private static final List<Session> ACTIVE = new ArrayList<>();
    private static final class Session {
        final UUID owner;
        final List<TacticalPlanner.Leg> legs;
        final List<AbstractRecruitEntity> members;
        final Map<AbstractRecruitEntity,Vec3> destinations = new IdentityHashMap<>();
        int leg;
        Session(TacticalPlanner.Plan plan, UUID owner) {
            this.owner = owner; legs = plan.legs();
            members = new ArrayList<>(plan.units().stream().filter(a -> a.marker().valid()).map(TacticalPlanner.Assignment::recruit).toList());
        }
    }
    private TacticalRoutes() {}
    public static void start(TacticalPlanner.Plan plan, UUID owner) {
        List<AbstractRecruitEntity> units = plan.units().stream().filter(a -> a.marker().valid()).map(TacticalPlanner.Assignment::recruit).toList();
        units.forEach(TacticalRoutes::forget);
        Session session = new Session(plan, owner);
        if (session.members.isEmpty() || session.legs.isEmpty()) return;
        ACTIVE.add(session); enter(session);
    }
    private static void enter(Session session) {
        session.destinations.clear();
        TacticalPlanner.Plan leg = TacticalPlanner.refresh(session.legs.get(session.leg), session.members);
        session.members.removeIf(r -> leg.units().stream().noneMatch(a -> a.recruit() == r && a.marker().valid()));
        for (var a : leg.units()) if (a.marker().valid()) session.destinations.put(a.recruit(), a.marker().target());
        TacticalMovement.execute(leg, session.owner);
    }
    public static void tick() {
        for (Session session : List.copyOf(ACTIVE)) {
            session.members.removeIf(r -> !r.m_6084_() || !r.getIsOwned() || !r.getListen() || !Objects.equals(session.owner, r.getOwnerUUID())
                || (!TacticalMovement.moving(r) && (session.destinations.get(r) == null || !TacticalTerrain.near(TacticalActor.position(r), session.destinations.get(r), 1.1))));
            if (session.members.isEmpty()) { ACTIVE.remove(session); continue; }
            if (session.members.stream().anyMatch(TacticalMovement::moving)) continue;
            if (++session.leg >= session.legs.size()) ACTIVE.remove(session); else enter(session);
        }
    }
    public static void forget(AbstractRecruitEntity r) { for (Session session : ACTIVE) session.members.remove(r); }
    public static void clear() { ACTIVE.clear(); }
}
