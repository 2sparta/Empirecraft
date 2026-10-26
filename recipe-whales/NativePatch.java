import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Offline, signature-checked patches. Retains all existing Create/VS mixin anchors. */
public class NativePatch implements Opcodes {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[1]);
        int count = 0;
        try (ZipFile zip = new ZipFile(args[0])) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                boolean jei = name.equals("com/simibubi/create/compat/jei/CreateJEI$CategoryBuilder.class");
                boolean corpse = Set.of("BowheadWhaleCorpseRightClickProcedure", "GrayWhaleCoprseRightClickProcedure",
                    "HumpbackCorpseRightClickedOnEntityProcedure", "RightWhaleCorpseRightClickProcedure")
                    .stream().anyMatch(s -> name.equals("net/mcreator/wonderoussea/procedures/" + s + ".class"));
                if (!jei && !corpse) continue;
                ClassNode node = new ClassNode();
                new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(node, 0);
                int edits = 0;
                for (MethodNode method : node.methods) {
                    if (jei && method.name.equals("build")) {
                        for (AbstractInsnNode n : method.instructions.toArray()) {
                            if (n instanceof MethodInsnNode call && call.owner.equals("java/util/List") && call.name.equals("add")) {
                                method.instructions.insertBefore(call, new VarInsnNode(ALOAD, 1));
                                method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, "ua/empirecraft/nativepatch/JeiCategories",
                                    "add", "(Ljava/util/List;Ljava/lang/Object;Ljava/lang/String;)Z", false));
                                edits++;
                            }
                        }
                    }
                    if (corpse && method.name.equals("execute")) {
                        if (!method.desc.equals("(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V"))
                            throw new IllegalStateException("Unreviewed corpse signature " + method.desc);
                        method.instructions.clear(); method.tryCatchBlocks.clear();
                        if (method.localVariables != null) method.localVariables.clear();
                        method.visibleLocalVariableAnnotations = null; method.invisibleLocalVariableAnnotations = null;
                        method.instructions.add(new VarInsnNode(ALOAD, 7));
                        method.instructions.add(new MethodInsnNode(INVOKESTATIC, "ua/empirecraft/nativepatch/WhaleHarvest",
                            "harvest", "(Lnet/minecraft/world/entity/Entity;)V", false));
                        method.instructions.add(new InsnNode(RETURN));
                        edits++;
                    }
                }
                if (edits != 1) throw new IllegalStateException("Expected one patch in " + name + ", got " + edits);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
                byte[] bytes = writer.toByteArray();
                ClassNode check = new ClassNode(); new ClassReader(bytes).accept(check, 0);
                for (MethodNode method : check.methods) if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) == 0)
                    new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name, method);
                Path target = out.resolve(name); Files.createDirectories(target.getParent()); Files.write(target, bytes); count++;
            }
        }
        int expected = args[0].contains("wonderoussea") ? 4 : 1;
        if (count != expected) throw new IllegalStateException("Expected " + expected + " patched classes, got " + count);
        System.out.println("PASS native bytecode and stack verification: " + count + " classes");
    }
}
