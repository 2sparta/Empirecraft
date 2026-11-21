package ua.townyrecruits;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import ua.townyrecruits.MilitaryData.Squad;
import ua.townyrecruits.tactics.*;

/** Casualties affect the whole company; nearby allied companies share shock, not casualty counts. */
public final class MoraleSystem {
    final MilitarySystem army;
    private final Map<UUID,Long> checks = new HashMap<>();
    private final Set<AbstractRecruitEntity> installed = Collections.newSetFromMap(new WeakHashMap<>());
    MoraleSystem(MilitarySystem army) { this.army = army; }
    public static boolean routing(AbstractRecruitEntity r) { return r.getIsOwned() && r.getPersistentData().m_128471_("TRRouting"); }
    public void install(AbstractRecruitEntity r) { if (installed.add(r)) r.f_21345_.m_25352_(-10, new RoutGoal(r)); }
    long now() { return army.bridge.server.m_129783_().m_46467_(); }
    void dirty() { army.data.m_77762_(); }
    public void combat(AbstractRecruitEntity r) {
        if (!r.getIsOwned() || r.getOwnerUUID() == null) return; army.observe(r);
        army.data.squad(MilitarySystem.group(r)).combat = now();
        r.getPersistentData().m_128356_("TRBattleAt", now()); dirty();
    }
    boolean allies(AbstractRecruitEntity a, AbstractRecruitEntity b) {
        return Policy.relation(army.bridge.snapshot, army.bridge.nation(a), army.bridge.nation(b)) > 0;
    }
    List<AbstractRecruitEntity> nearby(AbstractRecruitEntity r) {
        return army.bridge.loaded.stream().filter(o -> o != r && o.getIsOwned() && o.m_6084_() && o.m_20193_() == r.m_20193_()
            && o.m_20280_(r) <= MoralePolicy.ALLY_RADIUS * MoralePolicy.ALLY_RADIUS && allies(r,o)).toList();
    }
    boolean stable(UUID group) {
        Squad s = army.data.squad(group);
        return !s.routing && (s.lost == 0 || now() - s.lastLoss > MoralePolicy.BATTLE_WINDOW);
    }
    public void casualty(AbstractRecruitEntity dead, boolean battle) {
        if (!dead.getIsOwned() || dead.getOwnerUUID() == null) return;
        UUID group = MilitarySystem.group(dead); Squad s = army.data.squad(group); s.deaths++;
        if (!battle) { dirty(); return; }
        long now = now();
        if (now - s.lastLoss > MoralePolicy.BATTLE_WINDOW) { s.initial = 0; s.lost = 0; }
        s.initial = Math.max(s.initial, army.count(group) + s.lost);
        var allies = nearby(dead); boolean support = allies.stream().anyMatch(r -> !group.equals(MilitarySystem.group(r)) && stable(MilitarySystem.group(r)));
        s.lost++; s.combat = now; s.lastLoss = now; s.morale = MoralePolicy.ownLoss(s.morale, s.initial, support);
        Set<UUID> affected = new HashSet<>();
        for (var r : allies) {
            UUID other = MilitarySystem.group(r); if (other.equals(group) || !affected.add(other)) continue;
            Squad neighbor = army.data.squad(other); neighbor.morale = MoralePolicy.alliedLoss(neighbor.morale, army.count(other)); neighbor.combat = now;
        }
        considerBreak(group, dead.m_20182_(), support); dirty();
    }
    void considerBreak(UUID group, Vec3 threat, boolean support) {
        Squad s = army.data.squad(group); if (s.routing) return;
        var units = army.loaded(group); if (units.isEmpty()) return;
        if (units.get(0).m_217043_().m_188500_() < MoralePolicy.breakChance(s.morale, s.lost, s.initial, support)) {
            s.routing = true; s.routSize = army.count(group); s.escaped = 0; for (var r : units) begin(r, threat);
            var owner = army.bridge.server.m_6846_().m_11259_(units.get(0).getOwnerUUID());
            if (owner != null) owner.m_213846_(Component.m_237115_("army.townyrecruits.rout"));
            dirty();
        }
        checks.put(group, now());
    }
    void begin(AbstractRecruitEntity r, Vec3 threat) {
        if (routing(r)) return;
        TacticalOrders.cancel(r); r.setListen(false); r.setFleeing(true); r.setAggroState(3);
        var d = r.getPersistentData(); Vec3 here = TacticalActor.position(r);
        d.m_128379_("TRRouting", true);
        d.m_128347_("TRRoutX", here.f_82479_); d.m_128347_("TRRoutY", here.f_82480_); d.m_128347_("TRRoutZ", here.f_82481_);
        d.m_128347_("TRThreatX", threat.f_82479_); d.m_128347_("TRThreatY", threat.f_82480_); d.m_128347_("TRThreatZ", threat.f_82481_);
        TacticalActor.install(r);
    }
    static Vec3 vector(AbstractRecruitEntity r, String prefix) {
        var d = r.getPersistentData(); return new Vec3(d.m_128459_(prefix+"X"), d.m_128459_(prefix+"Y"), d.m_128459_(prefix+"Z"));
    }
    public void tick() {
        long now = now(); if (now % 20 != 0 || !army.bridge.ready || !army.bridge.censusReady()) return;
        Map<UUID,List<AbstractRecruitEntity>> groups = new HashMap<>();
        for (var r : List.copyOf(army.bridge.loaded)) if (r.getIsOwned() && r.getOwnerUUID() != null && r.m_6084_()) {
            army.observe(r); install(r); UUID group = MilitarySystem.group(r); groups.computeIfAbsent(group, id -> new ArrayList<>()).add(r);
            Squad s = army.data.squad(group);
            if (s.routing && !routing(r)) begin(r, r.m_5448_() == null ? TacticalActor.position(r) : r.m_5448_().m_20182_());
            if (routing(r)) {
                r.setListen(false); r.setFleeing(true); r.setAggroState(3); r.m_6710_(null); TacticalActor.install(r);
                boolean enemyNear = r.m_20193_().m_6443_(LivingEntity.class, r.m_20191_().m_82400_(16), e -> e != r && e.m_6084_()
                    && (Boolean.TRUE.equals(Bridge.combat(r,e)) || e instanceof net.minecraft.world.entity.monster.Enemy)).size() > 0;
                if (MoralePolicy.escaped(TacticalActor.horizontal(TacticalActor.position(r), vector(r,"TRRout")),
                    TacticalActor.horizontal(TacticalActor.position(r), vector(r,"TRThreat")), enemyNear)) army.desert(r, "army.townyrecruits.reason_rout");
            } else r.setMoral((float)(Militia.member(r)?Math.min(30,s.morale):s.morale));
        }
        for (var e : groups.entrySet()) {
            Squad s = army.data.squad(e.getKey());
            if (!s.routing && now - s.combat < MoralePolicy.CALM && now - checks.computeIfAbsent(e.getKey(), id -> now) >= 300) {
                var r = e.getValue().get(0); var near = nearby(r);
                considerBreak(e.getKey(), r.m_5448_() == null ? TacticalActor.position(r) : r.m_5448_().m_20182_(),
                    near.stream().anyMatch(a -> !e.getKey().equals(MilitarySystem.group(a)) && stable(MilitarySystem.group(a))));
            }
            if (!s.routing && now % 200 == 0 && now - s.combat >= MoralePolicy.CALM && s.morale < 100) { s.morale = Math.min(100, s.morale + 1); dirty(); }
            if (s.routing && army.count(e.getKey()) == 0) { s.routing = false; s.morale = 60; s.lost = 0; s.initial = 0; dirty(); }
            for (var r : e.getValue()) straggler(r, e.getValue(), now);
        }
        for (var e : army.data.squads.entrySet()) if ((e.getValue().routing || e.getValue().lost > 0) && army.count(e.getKey()) == 0) {
            e.getValue().routing = false; e.getValue().morale = 60; e.getValue().lost = 0; e.getValue().initial = 0; dirty();
        }
    }
    void straggler(AbstractRecruitEntity r, List<AbstractRecruitEntity> group, long now) {
        if (!r.getIsOwned()) return;
        var cohort = group.stream().filter(o -> o.getIsOwned() && o.m_6084_() && o.m_20193_() == r.m_20193_()).toList();
        Squad s = army.data.squad(MilitarySystem.group(r)); boolean escapedMajority = routing(r) && s.routSize >= 3 && s.escaped > s.routSize/2 && TacticalActor.dimension(r).equals(s.escapeDimension);
        if (cohort.size() < 3 && !escapedMajority) { r.getPersistentData().m_128473_("TRLagAt"); return; }
        double[] xs = cohort.stream().mapToDouble(o -> TacticalActor.position(o).f_82479_).sorted().toArray();
        double[] zs = cohort.stream().mapToDouble(o -> TacticalActor.position(o).f_82481_).sorted().toArray();
        Vec3 here = TacticalActor.position(r), median = new Vec3(xs[xs.length/2], here.f_82480_, zs[zs.length/2]), center = median;
        int clustered = (int)cohort.stream().filter(o -> TacticalActor.horizontal(TacticalActor.position(o), median) <= 24).count();
        int size = cohort.size();
        if (escapedMajority) { center = new Vec3(s.escapeX,s.escapeY,s.escapeZ); clustered = s.escaped; size = s.routSize; }
        var d = r.getPersistentData(); boolean expected = routing(r) || d.m_128471_("TRStranded") || TacticalMovement.moving(r) || TacticalCombat.manoeuvring(r)
            || r.getShouldFollow() || r.getShouldMovePos() || !TacticalActor.body(r).m_21573_().m_26571_();
        if (!expected || TacticalActor.horizontal(here, center) <= 48 || clustered <= size/2) { d.m_128473_("TRLagAt"); return; }
        if (!d.m_128441_("TRLagAt") || Math.hypot(here.f_82479_ - d.m_128459_("TRLagX"), here.f_82481_ - d.m_128459_("TRLagZ")) >= 2) {
            d.m_128356_("TRLagAt", now); d.m_128347_("TRLagX", here.f_82479_); d.m_128347_("TRLagZ", here.f_82481_); return;
        }
        if (MoralePolicy.straggler(size, clustered, TacticalActor.horizontal(here,center), now - d.m_128454_("TRLagAt"),
            Math.hypot(here.f_82479_ - d.m_128459_("TRLagX"), here.f_82481_ - d.m_128459_("TRLagZ")), expected)) army.desert(r, "army.townyrecruits.reason_straggler");
    }
    private static final class RoutGoal extends Goal {
        final AbstractRecruitEntity r; long next;
        RoutGoal(AbstractRecruitEntity r) { this.r = r; m_7021_(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean m_8036_() { return routing(r); }
        @Override public boolean m_8045_() { return m_8036_(); }
        @Override public boolean m_183429_() { return true; }
        @Override public void m_8041_() { TacticalActor.stop(r); }
        @Override public void m_8037_() {
            long now = r.m_20193_().m_46467_(); if (now < next) return; next = now + 10;
            Vec3 here = TacticalActor.position(r), threat = vector(r,"TRThreat");
            double dx = here.f_82479_ - threat.f_82479_, dz = here.f_82481_ - threat.f_82481_;
            double len = Math.hypot(dx,dz); if (len < 0.1) { dx = Math.cos(r.m_19879_()); dz = Math.sin(r.m_19879_()); len = 1; }
            for (double turn : new double[]{0, 35, -35, 70, -70, 100, -100}) {
                double[] p = TacticalMath.rotate(dx/len*8, dz/len*8, (float)turn);
                Vec3 destination = TacticalTerrain.stand(r, here.m_82520_(p[0],0,p[1])); if (destination == null) continue;
                if (TacticalTerrain.route(r,destination).isEmpty()) continue;
                TacticalActor.navigate(r,destination,1.25); return;
            }
            TacticalActor.stop(r);
        }
    }
}
