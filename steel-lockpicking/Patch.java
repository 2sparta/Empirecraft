import java.nio.file.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

/** Restore only steel registration and switch the existing menu type to its secured factory. */
public final class Patch implements Opcodes {
    public static void main(String[] args) throws Exception {
        try (var jar = new ZipFile(args[0])) {
            patch(jar, args[1], "melonslise/locks/common/init/LocksItems", true);
            patch(jar, args[1], "melonslise/locks/common/container/LockPickingContainer", false);
        }
    }
    private static void patch(ZipFile jar, String directory, String name, boolean item) throws Exception {
        var node = new ClassNode(); new ClassReader(jar.getInputStream(jar.getEntry(name + ".class"))).accept(node, 0);
        int changes = 0;
        for (var method : node.methods) if (method.name.equals("<clinit>")) for (var insn : method.instructions.toArray()) {
            if (item && insn.getOpcode() == RETURN) {
                var code = new InsnList();
                code.add(new LdcInsnNode("steel_lock_pick"));
                // Supplier construction is in the small helper to avoid generating an invokedynamic bootstrap.
                code.add(new MethodInsnNode(INVOKESTATIC, "ua/empirecraft/locks/PickRegistration", "supplier", "()Ljava/util/function/Supplier;", false));
                code.add(new MethodInsnNode(INVOKESTATIC, name, "add", "(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/minecraftforge/registries/RegistryObject;", false));
                code.add(new FieldInsnNode(PUTSTATIC, name, "STEEL_LOCK_PICK", "Lnet/minecraftforge/registries/RegistryObject;"));
                method.instructions.insertBefore(insn, code); changes++;
            } else if (!item && insn instanceof FieldInsnNode field && field.getOpcode() == PUTSTATIC && field.name.equals("FACTORY")) {
                var code = new InsnList(); code.add(new InsnNode(POP));
                code.add(new MethodInsnNode(INVOKESTATIC, "ua/empirecraft/locks/SteelPickingMenu", "factory", "()Lnet/minecraftforge/network/IContainerFactory;", false));
                method.instructions.insertBefore(insn, code); changes++;
            }
        }
        if (changes != 1) throw new AssertionError("Upstream initialization changed: " + name);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
        var output = Path.of(directory, name + ".class"); Files.createDirectories(output.getParent()); Files.write(output, writer.toByteArray());
    }
}
