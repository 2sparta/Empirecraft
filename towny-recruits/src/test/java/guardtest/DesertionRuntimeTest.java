package guardtest;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the production dismissal/cleanup body with small ownership fixtures. */
public final class DesertionRuntimeTest {
    public static final class Vehicle {}
    public static final class Player { public void m_213846_(Component text) {} }
    public static final class Players { public Player m_11259_(UUID id) { return null; } }
    public static final class Server { public Players m_6846_() { return new Players(); } }
    public static final class Army {
        public final Set<UUID> units = new HashSet<>(); public void remove(UUID id) { units.remove(id); }
    }
    public static final class Bridge { public final Army data = new Army(); public final Server server = new Server(); }
    public static final class Squad { public int deserted, escaped; public boolean routing = true; public double escapeX, escapeY, escapeZ; public String escapeDimension; }
    public static final class Actors {
        public static net.minecraft.world.phys.Vec3 position(Recruit r) { return new net.minecraft.world.phys.Vec3(0,64,0); }
        public static String dimension(Recruit r) { return "minecraft:overworld"; }
    }
    public static final class Data {
        public final Map<UUID,UUID> members = new HashMap<>(); public final Squad state = new Squad();
        public Squad squad(UUID id) { return state; } public void m_77762_() {}
    }
    public static final class Recruit {
        final UUID id = UUID.randomUUID(), group = UUID.randomUUID(); UUID owner = UUID.randomUUID();
        boolean owned = true, allow = true, fleeing = true, listen, mounted = true; int state = 3, follow = 3, dismissed, stopped;
        float morale; public boolean isInFormation = true, holdFormation = true;
        final CompoundTag tag = new CompoundTag(); final List<String> equipment = List.of("pike","shield","helmet","bomb");
        public boolean getIsOwned() { return owned; } public UUID getOwnerUUID() { return owner; } public UUID getGroup() { return group; }
        public UUID m_20148_() { return id; } public CompoundTag getPersistentData() { return tag; }
        public Vehicle m_20202_() { return mounted ? new Vehicle() : null; } public void m_8127_() { mounted = false; }
        public void disband(Player player, boolean keep, boolean cost) { dismissed++; if (allow) { owned = false; owner = null; } }
        public void setFleeing(boolean v) { fleeing = v; } public void setListen(boolean v) { listen = v; }
        public void setFollowState(int v) { follow = v; } public void setAggroState(int v) { state = v; } public void setMoral(float v) { morale = v; }
    }
    public static final class Orders { public static void cancel(Recruit r) { r.stopped++; } }
    static final class Loader extends ClassLoader { Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); } }
    static Class<?> harness() throws Exception {
        String source = "ua/townyrecruits/MilitarySystem", fixture = "guardtest/DesertionRuntimeTest$";
        Map<String,String> names = new HashMap<>(); names.put(source,"guardtest/generated/Desertion");
        names.put("ua/townyrecruits/Bridge",fixture+"Bridge"); names.put("ua/townyrecruits/ArmyData",fixture+"Army");
        names.put("ua/townyrecruits/MilitaryData",fixture+"Data"); names.put("ua/townyrecruits/MilitaryData$Squad",fixture+"Squad");
        names.put("ua/townyrecruits/tactics/TacticalOrders",fixture+"Orders");
        names.put("ua/townyrecruits/tactics/TacticalActor",fixture+"Actors");
        names.put("com/talhanation/recruits/entities/AbstractRecruitEntity",fixture+"Recruit");
        names.put("net/minecraft/world/entity/Entity",fixture+"Vehicle");
        names.put("net/minecraft/world/entity/player/Player",fixture+"Player"); names.put("net/minecraft/server/level/ServerPlayer",fixture+"Player");
        names.put("net/minecraft/server/MinecraftServer",fixture+"Server"); names.put("net/minecraft/server/players/PlayerList",fixture+"Players");
        ClassNode node = new ClassNode();
        try (var in = DesertionRuntimeTest.class.getClassLoader().getResourceAsStream(source+".class")) { new ClassReader(in.readAllBytes()).accept(node,0); }
        node.methods.removeIf(m -> !Set.of("desert","forget","group","clearStraggler").contains(m.name));
        node.fields.removeIf(f -> !Set.of("bridge","data").contains(f.name)); node.nestMembers = null;
        MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD,0)); ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false)); ctor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(ctor);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(new ClassRemapper(writer,new SimpleRemapper(names)));
        return new Loader().define(writer.toByteArray());
    }
    public static void main(String[] args) throws Exception {
        Class<?> type = harness(); Object service = type.getConstructor().newInstance(); Bridge bridge = new Bridge(); Data data = new Data();
        for (var name : List.of("bridge","data")) { var f = type.getDeclaredField(name); f.setAccessible(true); f.set(service,name.equals("bridge") ? bridge : data); }
        var desert = type.getMethod("desert",Recruit.class,String.class); Recruit r = new Recruit();
        bridge.data.units.add(r.id); data.members.put(r.id,r.group); r.tag.m_128379_("TRRouting",true); r.tag.m_128356_("TRLagAt",123);
        r.allow = false; desert.invoke(service,r,"test");
        assert r.owned && data.members.containsKey(r.id) && bridge.data.units.contains(r.id) && data.state.deserted == 0 : "Cancelled dismissal must not retire an owned recruit";
        r.allow = true; desert.invoke(service,r,"test");
        assert !r.owned && r.owner == null && !r.mounted && !r.fleeing && r.listen && r.state == 0 && r.follow == 0 && r.morale == 50;
        assert !r.isInFormation && !r.holdFormation && !r.tag.m_128441_("TRRouting") && !r.tag.m_128441_("TRLagAt");
        assert !data.members.containsKey(r.id) && !bridge.data.units.contains(r.id) && data.state.deserted == 1;
        assert data.state.escaped == 1 && data.state.escapeY == 64;
        assert r.equipment.equals(List.of("pike","shield","helmet","bomb")) : "Desertion preserves the entity and its equipment";
        desert.invoke(service,r,"test"); assert data.state.deserted == 1 && r.dismissed == 2;
        System.out.println("PASS executed production desertion: ownership/formation/navigation cleanup, native dismissal cancellation, no duplicate count, mounted dismount, persistent state cleanup and equipment retention");
    }
}
