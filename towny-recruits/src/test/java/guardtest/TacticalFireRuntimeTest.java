package guardtest;

import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import ua.townyrecruits.tactics.TacticalOrder.Mode;

/** Runs the actual compiled fire coordinator with small entity/clock fixtures, without claiming a game boot. */
public final class TacticalFireRuntimeTest {
    public static final class Clock { long tick; public long m_46467_() { return tick; } }
    public static final class Data {
        final Map<String,Object> values = new HashMap<>();
        public boolean m_128471_(String key) { return Boolean.TRUE.equals(values.get(key)); }
        public int m_128451_(String key) { return (int)values.getOrDefault(key, 0); }
        public void m_128379_(String key, boolean value) { values.put(key, value); }
    }
    public static final class Recruit {
        final Clock clock; final Data data = new Data(); UUID owner; boolean alive = true, listen = true, ranged = true, phase = true;
        int shots; boolean shouldRanged;
        Recruit(Clock clock, UUID owner, int rank) { this.clock = clock; this.owner = owner; data.values.put("TRRank", rank); }
        public Clock m_20193_() { return clock; }
        public Data getPersistentData() { return data; }
        public UUID getOwnerUUID() { return owner; }
        public boolean m_6084_() { return alive; }
        public boolean getIsOwned() { return owner != null; }
        public boolean getListen() { return listen; }
        public void setShouldRanged(boolean value) { shouldRanged = value; }
    }
    public static final class Equipment { public static boolean ranged(Recruit r) { return r.ranged; } }
    public static final class Combat {
        public static boolean firingPhase(Recruit r) { return r.phase; }
        public static void shot(Recruit r) { r.shots++; }
    }
    public static final class Aim { public static void clear() {} }
    private static final class Loader extends ClassLoader {
        Class<?> define(byte[] data) { return defineClass(null, data, 0, data.length); }
    }
    static Class<?> coordinator() throws Exception {
        String source = "ua/townyrecruits/tactics/TacticalFire", dest = "guardtest/generated/Fire";
        String fixture = "guardtest/TacticalFireRuntimeTest$";
        Map<String,String> names = new HashMap<>();
        names.put(source, dest); names.put(source + "$Batch", dest + "$Batch"); names.put(source + "$Ready", dest + "$Ready");
        names.put("com/talhanation/recruits/entities/AbstractRecruitEntity", fixture + "Recruit");
        names.put("net/minecraft/world/level/Level", fixture + "Clock"); names.put("net/minecraft/nbt/CompoundTag", fixture + "Data");
        for (String name : List.of("Equipment", "Combat", "Aim")) names.put("ua/townyrecruits/tactics/Tactical" + name, fixture + name);
        Loader loader = new Loader(); Class<?> result = null;
        for (String name : List.of(source, source + "$Batch", source + "$Ready")) {
            try (var in = TacticalFireRuntimeTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
                ClassWriter writer = new ClassWriter(0);
                new ClassReader(Objects.requireNonNull(in).readAllBytes()).accept(new ClassRemapper(writer, new SimpleRemapper(names)), 0);
                Class<?> type = loader.define(writer.toByteArray()); if (result == null) result = type;
            }
        }
        return result;
    }
    static Class<?> fire;
    static Object call(String method, Object... args) throws Exception {
        var target = Arrays.stream(fire.getDeclaredMethods()).filter(m -> m.getName().equals(method)).findFirst().orElseThrow();
        return target.invoke(null, args);
    }
    static void ready(Recruit r, boolean loaded, boolean eligible) throws Exception { call("ready", r, loaded, eligible); }
    static boolean permit(Recruit r) throws Exception { return (boolean)call("permit", r); }
    static void serverStart(Clock clock, long before) throws Exception {
        clock.tick = before; call("tick");
        // Forge's ServerTick START precedes ServerLevel.tickTime and entity AI.
        clock.tick++;
    }
    public static void main(String[] args) throws Exception {
        fire = coordinator(); Clock clock = new Clock(); UUID owner = UUID.randomUUID();
        Recruit a = new Recruit(clock, owner, 0), b = new Recruit(clock, owner, 0), blocked = new Recruit(clock, owner, 1);
        call("order", List.of(a, b, blocked), Mode.VOLLEY);
        ready(a, true, true); ready(b, false, true); ready(blocked, true, false);
        serverStart(clock, 1); assert !permit(a) && !permit(b) && !permit(blocked) : "A synchronized volley must wait for its ready line";
        ready(a, true, true); ready(b, true, true); ready(blocked, true, false);
        serverStart(clock, 2); assert permit(a) && permit(b) && !permit(blocked);
        call("fired", a); call("fired", b); assert a.shots == 1 && b.shots == 1 && blocked.shots == 0;
        clock.tick = 5; ready(blocked, true, true); serverStart(clock, 5); assert permit(blocked) : "A blocked soldier keeps the pending shot";
        call("fired", blocked); assert blocked.shots == 1;
        serverStart(clock, 6); assert !permit(a) && !permit(b) && !permit(blocked) : "A volley grants one shot only";
        call("clear"); clock.tick = 10;
        a = new Recruit(clock, owner, 0); b = new Recruit(clock, owner, 1);
        call("order", List.of(a, b), Mode.RANK_FIRE); ready(a, true, true); ready(b, true, true);
        serverStart(clock, 12); assert permit(a) && !permit(b); call("fired", a);
        clock.tick = 31; ready(a, false, true); ready(b, true, true); serverStart(clock, 31); assert !permit(b);
        serverStart(clock, 32); assert !permit(a) && permit(b) : "The second rank covers the first rank's reload";
        call("fired", b); call("hold", a, true); call("hold", b, true);
        clock.tick = 100; ready(a, true, true); ready(b, true, true); serverStart(clock, 100); assert !permit(a) && !permit(b) : "Cancellation/hold must remove every future grant";
        call("order", List.of(a, b), Mode.VOLLEY); a.owner = UUID.randomUUID();
        clock.tick = 103; ready(a, true, true); ready(b, true, true); serverStart(clock, 103); assert !permit(a) && permit(b) : "Owner transfer invalidates the old commander's grant";
        call("order", List.of(a), Mode.FREE_FIRE); assert permit(a);
        a.phase = false; assert !permit(a) : "Mounted archers must reload/escape before another firing pass";
        call("clear");
        System.out.println("PASS executed production fire coordinator: same-tick volley, loaded barrier, friendly obstruction retains shot, alternating ranks, hold/cancel, ownership transfer, free fire and mounted firing phases");
    }
}
