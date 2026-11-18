import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Remove retired registrations and their eager Forge client consumers. */
public final class TrimBoomsticks implements Opcodes {
    private static final Set<String> ITEMS = Set.of("ARQUEBUS", "HANDGONNE", "UPGRADE_KIT", "ROUND_BALL");
    private static final String ROOT = "com/TBK/medieval_boomsticks/";
    private static final String REG = ROOT + "common/registers/";

    private static void remove(InsnList list, AbstractInsnNode first, AbstractInsnNode last) {
        for (var instruction = first; ; ) {
            var next = instruction.getNext();
            if (instruction.getOpcode() >= 0) list.remove(instruction);
            if (instruction == last) return;
            instruction = next;
        }
    }

    private static AbstractInsnNode previousOpcode(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return Objects.requireNonNull(node);
    }

    private static boolean isField(AbstractInsnNode node, int opcode, String owner, Set<String> names) {
        return node instanceof FieldInsnNode field && field.getOpcode() == opcode
            && field.owner.equals(owner) && names.contains(field.name);
    }

    private static int registrations(ClassNode type, Set<String> names, int arguments) {
        int count = 0;
        for (MethodNode method : type.methods) {
            if (!method.name.equals("<clinit>")) continue;
            for (var node : method.instructions.toArray()) {
                if (!isField(node, PUTSTATIC, type.name, names)) continue;
                var first = node;
                for (int i = 0; i < arguments; i++) first = previousOpcode(first);
                remove(method.instructions, first, node);
                count++;
            }
        }
        return count;
    }

    private static int consumers(ClassNode type, String owner, Set<String> names, String endpoint) {
        int count = 0;
        for (MethodNode method : type.methods) {
            for (var node : method.instructions.toArray()) {
                if (!isField(node, GETSTATIC, owner, names)) continue;
                var end = node.getNext();
                while (!(end instanceof MethodInsnNode call && call.name.equals(endpoint))) {
                    if (end == null) throw new IllegalStateException(type.name + "." + method.name);
                    end = end.getNext();
                }
                if (endpoint.equals("m_246326_")) {
                    remove(method.instructions, previousOpcode(node), end);
                } else {
                    remove(method.instructions, node, end);
                }
                count++;
            }
        }
        return count;
    }

    private static int creative(ClassNode type) {
        int icons = 0;
        for (MethodNode method : type.methods) {
            for (var node : method.instructions.toArray()) {
                if (method.desc.equals("()Lnet/minecraft/world/item/ItemStack;")
                        && isField(node, GETSTATIC, REG + "MBItems", Set.of("ARQUEBUS"))) {
                    ((FieldInsnNode) node).name = "ARBALEST";
                    icons++;
                }
            }
        }
        return icons + consumers(type, REG + "MBItems", ITEMS, "m_246326_");
    }

    private static int arbalestRecharge(ClassNode type) {
        var method = type.methods.stream()
            .filter(m -> m.name.equals("getModifySpeedRecharge"))
            .findFirst().orElseThrow();
        var code = method.instructions;
        code.clear();
        method.localVariables = null;
        var standard = new LabelNode();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new FieldInsnNode(GETSTATIC, REG + "MBItems", "ARBALEST", "Lnet/minecraftforge/registries/RegistryObject;"));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraftforge/registries/RegistryObject", "get", "()Ljava/lang/Object;"));
        code.add(new TypeInsnNode(CHECKCAST, "net/minecraft/world/item/Item"));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "m_150930_", "(Lnet/minecraft/world/item/Item;)Z"));
        code.add(new JumpInsnNode(IFEQ, standard));
        code.add(new FieldInsnNode(GETSTATIC, ROOT + "Config", "rechargeSpeedArbalest", "Ljava/lang/Double;"));
        code.add(new InsnNode(ARETURN));
        code.add(standard);
        code.add(new FrameNode(F_SAME, 0, null, 0, null));
        code.add(new InsnNode(DCONST_1));
        code.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;"));
        code.add(new InsnNode(ARETURN));
        return 1;
    }

    public static void main(String[] args) throws IOException {
        Path directory = Path.of(args[0]);
        for (String name : List.of("MBItems", "MBEntityType", "MBSounds", "MBCreativeTabs", "MBItemProperties", "RKMedievalBoomStick", "RechargeItem")) {
            Path path = directory.resolve((name.startsWith("MB") ? REG : name.equals("RechargeItem") ? ROOT + "common/items/" : ROOT) + name + ".class");
            var type = new ClassNode();
            new ClassReader(Files.readAllBytes(path)).accept(type, 0);
            int edits = switch (name) {
                case "MBItems" -> registrations(type, ITEMS, 4);
                case "MBEntityType" -> registrations(type, Set.of("ROUND_BALL"), 4);
                case "MBSounds" -> registrations(type, Set.of("ARQUEBUS_SHOOT", "HANDGONNE_SHOOT"), 2);
                case "MBCreativeTabs" -> creative(type);
                case "MBItemProperties" -> consumers(type, REG + "MBItems", ITEMS, "register");
                case "RechargeItem" -> arbalestRecharge(type);
                default -> consumers(type, REG + "MBEntityType", Set.of("ROUND_BALL"), "m_174036_");
            };
            int expected = switch (name) {
                case "MBItems" -> 4;
                case "MBSounds" -> 2;
                case "MBCreativeTabs" -> 5;
                case "MBItemProperties" -> 6;
                default -> 1;
            };
            if (edits != expected) throw new IllegalStateException(name + ": " + edits);
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            type.accept(writer);
            Files.write(path, writer.toByteArray());
        }
    }
}
