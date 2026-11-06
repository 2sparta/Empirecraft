package guardtest;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import ua.townyrecruits.tactics.TacticalOrder;
import ua.townyrecruits.tactics.TacticalPlanner.Failure;

/** Executes the production route-leg revalidation, especially when only some previewed slots survive. */
public final class RouteSlotsRuntimeTest {
    public static final class Recruit {
        final int id; Vec3 position; boolean blocked, hungry, mounted, pike = true;
        Recruit(int id, Vec3 position) { this.id = id; this.position = position; }
        public int m_19879_() { return id; }
        public boolean getFleeing() { return false; } public boolean needsToGetFood() { return hungry; }
        public boolean getShouldMount() { return false; }
    }
    public static final class Shot {}
    public record Marker(int entity, Vec3 from, Vec3 target, float yaw, Failure failure, List<Vec3> route, double footprint) {
        public boolean valid() { return failure == Failure.NONE; }
    }
    public record Assignment(Recruit recruit, Marker marker, Shot shot, int rank) {}
    public record Leg(TacticalOrder order, List<Assignment> units) {}
    public record Plan(TacticalOrder order, List<Assignment> units, boolean hasBombs) {}
    public static final class Actors {
        public static Vec3 position(Recruit r) { return r.position; }
        public static double width(Recruit r) { return r.mounted ? 1.4 : 0.6; }
        public static boolean available(Recruit r) { return true; }
        public static boolean mounted(Recruit r) { return r.mounted; }
    }
    public static final class Equipment {
        public static boolean pike(Recruit r) { return r.pike; }
        public static boolean shield(Recruit r) { return true; }
        public static boolean ranged(Recruit r) { return true; }
    }
    public static final class Terrain {
        public static Vec3 stand(Recruit r, Vec3 dest) { return r.blocked ? null : dest; }
        public static List<Vec3> route(Recruit r, Vec3 dest) { return List.of(dest); }
    }
    static final class Loader extends ClassLoader { Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); } }
    static java.lang.reflect.Method refresh() throws Exception {
        String source = "ua/townyrecruits/tactics/TacticalPlanner", fixture = "guardtest/RouteSlotsRuntimeTest$";
        Map<String,String> names = new HashMap<>(); names.put(source, "guardtest/generated/RoutePlan");
        names.put("com/talhanation/recruits/entities/AbstractRecruitEntity", fixture + "Recruit");
        for (String name : List.of("Marker", "Assignment", "Leg", "Plan")) names.put(source + "$" + name, fixture + name);
        names.put("ua/townyrecruits/tactics/RecruitBombs$Shot", fixture + "Shot");
        names.put("ua/townyrecruits/tactics/TacticalActor", fixture + "Actors");
        names.put("ua/townyrecruits/tactics/TacticalEquipment", fixture + "Equipment");
        names.put("ua/townyrecruits/tactics/TacticalTerrain", fixture + "Terrain");
        ClassNode node = new ClassNode();
        try (var in = RouteSlotsRuntimeTest.class.getClassLoader().getResourceAsStream(source + ".class")) { new ClassReader(in.readAllBytes()).accept(node, 0); }
        node.methods.removeIf(m -> !Set.of("refresh", "marker", "equipment").contains(m.name)); node.fields.clear(); node.nestMembers = null;
        ClassWriter writer = new ClassWriter(0); node.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
        return new Loader().define(writer.toByteArray()).getMethod("refresh", Leg.class, List.class);
    }
    public static void main(String[] args) throws Exception {
        var refresh = refresh();
        Recruit a = new Recruit(1, new Vec3(-10, 64, 0)), b = new Recruit(2, new Vec3(0, 64, 0)), c = new Recruit(3, new Vec3(10, 64, 0));
        var order = new TacticalOrder(TacticalOrder.Mode.MOVE, TacticalOrder.Formation.PIKE_WALL, List.of(UUID.randomUUID()), new Vec3(0, 64, 30), 90, false);
        Vec3 first = new Vec3(-3, 64, 30), last = new Vec3(3, 64, 30);
        var leg = new Leg(order, List.of(
            new Assignment(a, new Marker(1, a.position, first, 90, Failure.NONE, List.of(first), 0.75), null, 0),
            new Assignment(b, new Marker(2, b.position, new Vec3(0,64,30), 90, Failure.GROUND, List.of(), 0.75), null, 0),
            new Assignment(c, new Marker(3, c.position, last, 90, Failure.NONE, List.of(last), 0.75), null, 1)));
        Plan partial = (Plan)refresh.invoke(null, leg, List.of(a, c));
        assert partial.units().size() == 2;
        assert partial.units().get(0).marker().target().equals(first) && partial.units().get(1).marker().target().equals(last)
            : "Dropping a red slot must not move the green slots away from their previews";
        assert partial.units().get(1).rank() == 1 && partial.units().get(1).marker().yaw() == 90;
        a.blocked = true; Plan obstacle = (Plan)refresh.invoke(null, leg, List.of(a, c));
        assert obstacle.units().get(0).marker().failure() == Failure.GROUND && obstacle.units().get(1).marker().valid();
        a.blocked = false; a.pike = false; Plan changedWeapon = (Plan)refresh.invoke(null, leg, List.of(a, c));
        assert changedWeapon.units().get(0).marker().failure() == Failure.PIKE;
        Plan casualty = (Plan)refresh.invoke(null, leg, List.of(c));
        assert casualty.units().size() == 1 && casualty.units().get(0).marker().target().equals(last) && casualty.units().get(0).rank() == 1;
        assert a.position.equals(new Vec3(-10, 64, 0)) && c.position.equals(new Vec3(10, 64, 0));
        System.out.println("PASS executed production route revalidation: partial availability and casualties preserve exact preview slots/facings/ranks; new obstacles and lost equipment affect only that recruit; planning never moves entities");
    }
}
