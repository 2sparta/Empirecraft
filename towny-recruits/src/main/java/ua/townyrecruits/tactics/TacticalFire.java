package ua.townyrecruits.tactics;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import java.util.*;
import ua.townyrecruits.tactics.TacticalOrder.Mode;

/** Releases ready weapons on one server tick; blocked shooters retain ammunition and their order. */
public final class TacticalFire {
    private static final String HOLD = "TRHoldFire";
    private record Ready(boolean loaded, boolean eligible, long tick) {}
    private static final Map<AbstractRecruitEntity, Ready> READY = new IdentityHashMap<>();
    private static final Map<AbstractRecruitEntity, Long> GRANTS = new IdentityHashMap<>();
    private static final List<Batch> BATCHES = new ArrayList<>();
    private static final class Batch {
        final List<AbstractRecruitEntity> members;
        final Map<AbstractRecruitEntity,Integer> ranks = new IdentityHashMap<>();
        final Map<AbstractRecruitEntity,UUID> owners = new IdentityHashMap<>();
        final Set<AbstractRecruitEntity> pending = Collections.newSetFromMap(new IdentityHashMap<>());
        final boolean alternating;
        long next, deadline; int rank, rankCount;
        Batch(List<AbstractRecruitEntity> units, boolean alternating) {
            this.members = new ArrayList<>(units); this.alternating = alternating; pending.addAll(units);
            long now = units.get(0).m_20193_().m_46467_(); next = now + 2; deadline = now + 1200;
            int min = units.stream().mapToInt(r -> r.getPersistentData().m_128451_("TRRank")).min().orElse(0);
            int max = units.stream().mapToInt(r -> r.getPersistentData().m_128451_("TRRank")).max().orElse(0);
            for (int i = 0; i < units.size(); i++) {
                ranks.put(units.get(i), max == min ? i % 2 : units.get(i).getPersistentData().m_128451_("TRRank") - min);
                owners.put(units.get(i), units.get(i).getOwnerUUID());
            }
            rankCount = Math.max(1, ranks.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1);
        }
    }
    private TacticalFire() {}
    public static boolean held(AbstractRecruitEntity r) { return r.getPersistentData().m_128471_(HOLD); }
    public static void ready(AbstractRecruitEntity r, boolean loaded, boolean eligible) {
        READY.put(r, new Ready(loaded, eligible, r.m_20193_().m_46467_()));
    }
    public static boolean loaded(AbstractRecruitEntity r) { Ready s = READY.get(r); return s != null && s.loaded; }
    public static boolean permit(AbstractRecruitEntity r) {
        return (!held(r) || Objects.equals(GRANTS.get(r), r.m_20193_().m_46467_())) && TacticalCombat.firingPhase(r);
    }
    public static void fired(AbstractRecruitEntity r) {
        READY.remove(r); GRANTS.remove(r);
        for (Batch batch : BATCHES) if (!batch.alternating) batch.pending.remove(r);
        TacticalCombat.shot(r);
    }
    public static void hold(AbstractRecruitEntity r, boolean hold) {
        detach(r); r.getPersistentData().m_128379_(HOLD, hold);
        if (hold && TacticalEquipment.ranged(r)) r.setShouldRanged(true);
    }
    public static void order(List<AbstractRecruitEntity> recruits, Mode mode) {
        if (recruits.isEmpty()) return;
        for (var r : recruits) hold(r, mode != Mode.FREE_FIRE);
        if (mode == Mode.VOLLEY || mode == Mode.RANK_FIRE) BATCHES.add(new Batch(recruits, mode == Mode.RANK_FIRE));
    }
    public static void tick() {
        for (Batch batch : List.copyOf(BATCHES)) {
            batch.members.removeIf(r -> !r.m_6084_() || !r.getIsOwned() || !r.getListen() || !TacticalEquipment.ranged(r)
                || !Objects.equals(batch.owners.get(r), r.getOwnerUUID()));
            if (batch.members.isEmpty()) { BATCHES.remove(batch); continue; }
            long now = batch.members.get(0).m_20193_().m_46467_();
            if ((!batch.alternating && (batch.pending.isEmpty() || now > batch.deadline))) { BATCHES.remove(batch); continue; }
            if (now < batch.next) continue;
            List<AbstractRecruitEntity> eligible = batch.members.stream().filter(r -> {
                Ready state = READY.get(r);
                return state != null && state.eligible && now - state.tick <= 2
                    && (batch.alternating ? batch.ranks.get(r) == batch.rank : batch.pending.contains(r));
            }).toList();
            if (eligible.isEmpty()) { if (batch.alternating) { batch.rank = (batch.rank + 1) % batch.rankCount; batch.next = now + 10; } continue; }
            if (eligible.stream().anyMatch(r -> !READY.get(r).loaded)) continue;
            // Server START runs before ServerLevel increments gameTime. Entity goals see
            // the following world tick, so every grant must name that upcoming tick.
            for (var r : eligible) GRANTS.put(r, now + 1);
            batch.next = now + (batch.alternating ? 20 : 2);
            if (batch.alternating) batch.rank = (batch.rank + 1) % batch.rankCount;
        }
        GRANTS.entrySet().removeIf(e -> e.getValue() < e.getKey().m_20193_().m_46467_());
    }
    private static void detach(AbstractRecruitEntity r) {
        GRANTS.remove(r);
        for (Batch batch : BATCHES) { batch.members.remove(r); batch.pending.remove(r); }
    }
    public static void forget(AbstractRecruitEntity r) { detach(r); READY.remove(r); }
    public static void clear() { BATCHES.clear(); READY.clear(); GRANTS.clear(); TacticalAim.clear(); }
}
