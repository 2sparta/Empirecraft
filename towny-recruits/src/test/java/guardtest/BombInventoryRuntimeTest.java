package guardtest;

import java.lang.reflect.*;
import java.lang.reflect.Method;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.townyrecruits.tactics.RecruitBombInventory;

/** Executes the production hooks' compiled bodies with small inventory fixtures; no game boot is claimed. */
public final class BombInventoryRuntimeTest {
    public static class Item {}
    public static final class Bomb extends Item {}
    public static final class Stack {
        final Item item; int count;
        Stack(Item item, int count) { this.item = item; this.count = count; }
        public boolean m_41619_() { return count == 0; }
        public Item m_41720_() { return item; }
        public int m_41613_() { return count; }
        public Stack m_41777_() { return new Stack(item, count); }
        public void m_41774_(int amount) { count -= amount; }
        public void m_41764_(int amount) { count = amount; }
    }
    public interface Container {
        int m_6643_(); Stack m_8020_(int slot); void m_6836_(int slot, Stack stack);
    }
    public static class BaseContainer implements Container {
        final Stack[] items = new Stack[15];
        public BaseContainer() { Arrays.setAll(items, i -> new Stack(new Item(), 0)); }
        public int m_6643_() { return items.length; }
        public Stack m_8020_(int slot) { return items[slot]; }
        public void m_6836_(int slot, Stack stack) { items[slot] = stack; }
    }
    public static class InventoryOwner {}
    public static final class Recruit extends InventoryOwner {}
    public static class Slot {
        public Container f_40218_; public int index;
        public int getSlotIndex() { return index; }
    }
    static final class Loader extends ClassLoader {
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
    static Map<String,String> mappings() {
        Map<String,String> map = new HashMap<>();
        String p = "guardtest/BombInventoryRuntimeTest$";
        map.put("net/minecraft/world/item/ItemStack", p + "Stack");
        map.put("net/minecraft/world/item/Item", p + "Item");
        map.put("net/mehvahdjukaar/supplementaries/common/items/BombItem", p + "Bomb");
        map.put("net/minecraft/world/Container", p + "Container");
        map.put("net/minecraft/world/inventory/Slot", p + "Slot");
        map.put("com/talhanation/recruits/entities/AbstractInventoryEntity", p + "InventoryOwner");
        map.put("com/talhanation/recruits/entities/AbstractRecruitEntity", p + "Recruit");
        map.put("ua/townyrecruits/tactics/RecruitBombs", "guardtest/generated/BombLogic");
        return map;
    }
    static Class<?> harness(Loader loader, String source, String target, String parent, Set<String> methods) throws Exception {
        ClassNode node = new ClassNode();
        try (var in = BombInventoryRuntimeTest.class.getClassLoader().getResourceAsStream(source + ".class")) {
            new ClassReader(Objects.requireNonNull(in).readAllBytes()).accept(node, 0);
        }
        if (methods != null) { node.methods.removeIf(m -> !methods.contains(m.name)); node.fields.clear(); }
        else node.methods.removeIf(m -> m.name.equals("<init>"));
        node.access &= ~Opcodes.ACC_ABSTRACT; node.superName = parent;
        node.visibleAnnotations = null; node.invisibleAnnotations = null;
        if (methods == null) {
            MethodNode ctor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            ctor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false));
            ctor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(ctor);
        }
        Map<String,String> names = mappings(); names.put(source, target);
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(out, new SimpleRemapper(names)));
        return loader.define(out.toByteArray());
    }
    static Object invoke(Object object, String name, Object... args) throws Exception {
        Method method = Arrays.stream(object.getClass().getDeclaredMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        method.setAccessible(true); return method.invoke(object, args);
    }
    static CallbackInfoReturnable<Boolean> placement(Object inv, int slot, Stack stack) throws Exception {
        var ci = new CallbackInfoReturnable<Boolean>("placement", true); invoke(inv, "bombPlacement", slot, stack, ci); return ci;
    }
    public static void main(String[] args) throws Exception {
        Loader loader = new Loader();
        harness(loader, "ua/townyrecruits/tactics/RecruitBombs", "guardtest/generated/BombLogic", "java/lang/Object", Set.of("bomb", "count", "inventory"));
        Class<?> containerType = harness(loader, "ua/townyrecruits/mixin/BombContainerMixin", "guardtest/generated/BombContainer",
            "guardtest/BombInventoryRuntimeTest$BaseContainer", null);
        Object inventory = containerType.getConstructor().newInstance();
        Field owner = containerType.getDeclaredField("recruit"); owner.setAccessible(true); owner.set(inventory, new Recruit());
        assert ((RecruitBombInventory)inventory).townyrecruits$isRecruitInventory();
        Stack normal = new Stack(new Item(), 64), bomb = new Stack(new Bomb(), 64), blue = new Stack(new Bomb(), 64);
        assert !placement(inventory, 6, normal).isCancelled();
        assert !placement(inventory, 5, bomb).getReturnValueZ() && placement(inventory, 6, bomb).getReturnValueZ();
        var pickup = new CallbackInfoReturnable<Stack>("pickup", true);
        invoke(inventory, "pickUpOne", bomb, pickup);
        Container inv = (Container)inventory;
        assert inv.m_8020_(6).count == 1 && pickup.getReturnValue().count == 63;
        assert bomb.count == 64 : "Native addItem contract must not mutate its input";
        assert !placement(inventory, 6, bomb).getReturnValueZ() && !placement(inventory, 7, blue).getReturnValueZ();
        pickup = new CallbackInfoReturnable<Stack>("pickup", true); invoke(inventory, "pickUpOne", blue, pickup);
        assert pickup.getReturnValue().count == 64 && inv.m_8020_(6).count == 1;
        Object slot = harness(loader, "ua/townyrecruits/mixin/BombSlotMixin", "guardtest/generated/BombSlot",
            "guardtest/BombInventoryRuntimeTest$Slot", null).getConstructor().newInstance();
        ((Slot)slot).f_40218_ = inv; ((Slot)slot).index = 6;
        var max = new CallbackInfoReturnable<Integer>("max", true); invoke(slot, "limitBomb", bomb, max); assert max.getReturnValueI() == 1;
        ((Slot)slot).index = 7;
        max = new CallbackInfoReturnable<Integer>("max", true); invoke(slot, "limitBomb", blue, max); assert max.getReturnValueI() == 0;
        var accept = new CallbackInfoReturnable<Boolean>("accept", true); invoke(slot, "acceptBomb", blue, accept); assert !accept.getReturnValueZ();
        inv.m_6836_(6, new Stack(new Item(), 0));
        accept = new CallbackInfoReturnable<Boolean>("accept", true); invoke(slot, "acceptBomb", blue, accept); assert accept.getReturnValueZ();
        max = new CallbackInfoReturnable<Integer>("max", true); invoke(slot, "limitBomb", blue, max); assert max.getReturnValueI() == 1;
        // Non-recruit inventories retain the native behavior.
        owner.set(inventory, new InventoryOwner());
        assert !placement(inventory, 6, bomb).isCancelled();
        System.out.println("PASS executed production bomb hooks: one shared bomb across types, no merge/second-slot/armor insertion, 64-item pickup keeps one and returns 63, removal reopens capacity, unrelated inventories unchanged");
    }
}
