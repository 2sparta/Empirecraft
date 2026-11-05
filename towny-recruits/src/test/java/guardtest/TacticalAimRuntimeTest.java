package guardtest;

import java.lang.reflect.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.phys.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the production obstruction check with real Vec3/AABB intersections and small world fixtures. */
public final class TacticalAimRuntimeTest {
    public static class Living {
        double x, y, z; boolean friendly; Recruit rider; Level level;
        Living(Level level, double x, double y, double z, boolean friendly) { this.level = level; this.x = x; this.y = y; this.z = z; this.friendly = friendly; }
        public boolean m_6084_() { return true; }
        public double m_20185_() { return x; } public double m_20188_() { return y + 1.6; } public double m_20189_() { return z; }
        public AABB m_20191_() { return new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.9, z + 0.3); }
        public Level m_20193_() { return level; }
    }
    public static final class Recruit extends Living { Living mount; Recruit(Level level) { super(level, 0, 0, 0, true); } }
    public static final class Actors {
        public static Living body(Recruit r) { return r.mount == null ? r : r.mount; }
        public static Recruit rider(Living e) { return e instanceof Recruit r ? r : e.rider; }
    }
    public static final class Bridge { public static Boolean combat(Living a, Living b) { return b.friendly ? false : null; } }
    public static final class Clip {
        public enum Block { COLLIDER } public enum Fluid { NONE }
        final Vec3 a, b;
        public Clip(Vec3 a, Vec3 b, Block block, Fluid fluid, Living context) { this.a = a; this.b = b; }
    }
    public static final class Hit {
        public enum Type { BLOCK, MISS }
        final Vec3 point; final Type type;
        Hit(Vec3 point, Type type) { this.point = point; this.type = type; }
        public Vec3 m_82450_() { return point; } public Type m_6662_() { return type; }
    }
    public static final class Level {
        final List<Living> entities = new ArrayList<>(); double wall = Double.NaN;
        public List<Living> m_6443_(Class<?> type, AABB box, Predicate<Living> filter) { return entities.stream().filter(filter).toList(); }
        public Hit m_45547_(Clip clip) {
            if (clip.a.f_82479_ <= wall && clip.b.f_82479_ >= wall) return new Hit(new Vec3(wall, clip.a.f_82480_, 0), Hit.Type.BLOCK);
            return new Hit(clip.b, Hit.Type.MISS);
        }
    }
    static final class Loader extends ClassLoader { Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); } }
    static Method check() throws Exception {
        String source = "ua/townyrecruits/tactics/TacticalAim", fixture = "guardtest/TacticalAimRuntimeTest$";
        Map<String,String> names = new HashMap<>(); names.put(source, "guardtest/generated/AimCheck");
        names.put("com/talhanation/recruits/entities/AbstractRecruitEntity", fixture + "Recruit");
        for (String name : List.of("LivingEntity", "Mob", "Entity")) names.put("net/minecraft/world/entity/" + name, fixture + "Living");
        names.put("net/minecraft/world/level/Level", fixture + "Level"); names.put("ua/townyrecruits/Bridge", fixture + "Bridge");
        names.put("ua/townyrecruits/tactics/TacticalActor", fixture + "Actors");
        names.put("net/minecraft/world/level/ClipContext", fixture + "Clip");
        for (String name : List.of("Block", "Fluid")) names.put("net/minecraft/world/level/ClipContext$" + name, fixture + "Clip$" + name);
        for (String name : List.of("HitResult", "BlockHitResult")) names.put("net/minecraft/world/phys/" + name, fixture + "Hit");
        names.put("net/minecraft/world/phys/HitResult$Type", fixture + "Hit$Type");
        ClassNode node = new ClassNode();
        try (var in = TacticalAimRuntimeTest.class.getClassLoader().getResourceAsStream(source + ".class")) { new ClassReader(in.readAllBytes()).accept(node, 0); }
        node.methods.removeIf(m -> (!Set.of("clear", "arrow", "origin").contains(m.name) && !m.name.startsWith("lambda$clear$")) || m.desc.equals("()V"));
        node.fields.clear(); node.nestMembers = null;
        ClassWriter out = new ClassWriter(0); node.accept(new ClassRemapper(out, new SimpleRemapper(names)));
        return new Loader().define(out.toByteArray()).getMethod("clear", Recruit.class, Living.class, Vec3.class, double.class, boolean.class);
    }
    public static void main(String[] args) throws Exception {
        Method check = check(); Level level = new Level(); Recruit r = new Recruit(level);
        Living target = new Living(level, 24, 0, 0, false), friend = new Living(level, 3, 0, 0, true);
        Vec3 aim = new Vec3(24, 1.2, 0);
        for (boolean arrow : new boolean[]{false, true}) {
            friend.x = 3; friend.z = 0;
            level.entities.clear(); assert (boolean)check.invoke(null, r, target, aim, 2.6, arrow);
            level.entities.add(friend); assert !(boolean)check.invoke(null, r, target, aim, 2.6, arrow) : "A friendly recruit ahead must block the shot";
            friend.z = 2; assert (boolean)check.invoke(null, r, target, aim, 2.6, arrow);
            friend.x = -3; friend.z = 0; assert (boolean)check.invoke(null, r, target, aim, 2.6, arrow);
            Living mount = new Living(level, 0, 0, 0, true); mount.rider = r; r.mount = mount;
            level.entities.add(mount); assert (boolean)check.invoke(null, r, target, aim, 2.6, arrow) : "An archer must not block its own mounted shot";
            Living allyHorse = new Living(level, 3, 0, 0, false); Recruit alliedRider = new Recruit(level); allyHorse.rider = alliedRider;
            level.entities.add(allyHorse); assert !(boolean)check.invoke(null, r, target, aim, 2.6, arrow) : "Friendly cavalry also blocks the firing lane";
            level.entities.remove(allyHorse); level.wall = 12; assert !(boolean)check.invoke(null, r, target, aim, 2.6, arrow);
            level.wall = Double.NaN;
        }
        System.out.println("PASS executed production shot clearance: friendly infantry and cavalry, shooter mount exclusion, soldiers beside/behind the lane, block obstruction, straight shots and discrete arrow arcs");
    }
}
