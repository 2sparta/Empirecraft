import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Offline, version-pinned removal of native registration paths, not a display filter. */
public final class PatchRegistries implements Opcodes {
    static final String PALETTE = "earth/terrarium/chipped/common/registry/base/ChippedPaletteRegistry";
    static int changes;
    static void knights(ClassNode cls) {
        for (MethodNode m : cls.methods) {
            if (!Set.of("addMedievalShieldItem", "addPaviseItem").contains(m.name)) continue;
            // The native methods already return null for disabled shield types. Use that same path
            // before reaching either the registry or the creative/shield supplier list.
            LabelNode removed = new LabelNode(), original = new LabelNode();
            InsnList prefix = new InsnList();
            for (String tier : List.of("DIAMOND", "NETHERITE")) {
                prefix.add(new VarInsnNode(ALOAD, 4));
                prefix.add(new FieldInsnNode(GETSTATIC, "com/magistuarmory/item/ModItemTier", tier, "Lcom/magistuarmory/item/ModItemTier;"));
                prefix.add(new JumpInsnNode(IF_ACMPEQ, removed));
            }
            prefix.add(new JumpInsnNode(GOTO, original));
            prefix.add(removed); prefix.add(new FrameNode(F_SAME, 0, null, 0, null));
            prefix.add(new InsnNode(ACONST_NULL)); prefix.add(new InsnNode(ARETURN));
            prefix.add(original); prefix.add(new FrameNode(F_SAME, 0, null, 0, null));
            m.instructions.insert(prefix); changes++;
        }
        if (changes != 2) throw new IllegalStateException("Unexpected Knights shield registration layout");
    }
    static void chipped(ClassNode cls, Set<String> families) {
        MethodNode m = cls.methods.stream().filter(x -> x.name.equals("<clinit>")).findFirst().orElseThrow();
        Set<String> found = new TreeSet<>();
        AbstractInsnNode previous = null;
        for (AbstractInsnNode n : m.instructions.toArray()) {
            if (!(n instanceof FieldInsnNode f) || n.getOpcode() != PUTSTATIC || !f.owner.equals(cls.name)) continue;
            if (families.contains(f.name.toLowerCase(Locale.ROOT))) {
                if (!f.desc.equals("L" + PALETTE + ";") || previous == null) throw new IllegalStateException(f.name);
                AbstractInsnNode start = previous.getNext();
                // Every selected field has one independent, straight-line registration expression.
                for (AbstractInsnNode p = start; p != n; p = p.getNext())
                    if (p instanceof JumpInsnNode || p instanceof TableSwitchInsnNode || p instanceof LookupSwitchInsnNode)
                        throw new IllegalStateException("Nonlinear Chipped initializer: " + f.name);
                for (AbstractInsnNode p = start; p != n;) { AbstractInsnNode next = p.getNext(); m.instructions.remove(p); p = next; }
                m.instructions.insertBefore(n, new MethodInsnNode(INVOKESTATIC, cls.name, "empirecraft$empty", "()L" + PALETTE + ";", false));
                found.add(f.name.toLowerCase(Locale.ROOT)); changes++;
            }
            previous = n;
        }
        if (!found.equals(families)) throw new IllegalStateException("Unexpected Chipped families: " + found + " expected " + families);
        MethodNode empty = new MethodNode(ACC_PRIVATE | ACC_STATIC, "empirecraft$empty", "()L" + PALETTE + ";", null, null);
        var b = empty.instructions;
        b.add(new TypeInsnNode(NEW, PALETTE)); b.add(new InsnNode(DUP));
        b.add(new FieldInsnNode(GETSTATIC, cls.name, "BLOCKS", "Lcom/teamresourceful/resourcefullib/common/registry/ResourcefulRegistry;"));
        b.add(new MethodInsnNode(INVOKESTATIC, "com/teamresourceful/resourcefullib/common/registry/ResourcefulRegistries", "create", "(Lcom/teamresourceful/resourcefullib/common/registry/ResourcefulRegistry;)Lcom/teamresourceful/resourcefullib/common/registry/ResourcefulRegistry;", false));
        b.add(new FieldInsnNode(GETSTATIC, "net/minecraft/world/level/block/Blocks", "f_50069_", "Lnet/minecraft/world/level/block/Block;"));
        b.add(new FieldInsnNode(GETSTATIC, "earth/terrarium/chipped/common/palette/Palettes", "STONE", "Learth/terrarium/chipped/common/palette/Palette;"));
        b.add(new MethodInsnNode(INVOKESPECIAL, PALETTE, "<init>", "(Lcom/teamresourceful/resourcefullib/common/registry/ResourcefulRegistry;Ljava/lang/Object;Learth/terrarium/chipped/common/palette/Palette;)V", false));
        b.add(new InsnNode(ARETURN)); cls.methods.add(empty);
        // Empty containers keep native field consumers valid. No removed Block or BlockItem is registered.
    }
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        String name = args[2]; ClassNode cls = new ClassNode();
        try (ZipFile z = new ZipFile(input.toFile())) { new ClassReader(z.getInputStream(z.getEntry(name)).readAllBytes()).accept(cls, 0); }
        if (args[3].equals("knights")) knights(cls);
        else chipped(cls, new TreeSet<>(Files.readAllLines(Path.of(args[3]))));
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); cls.accept(w); byte[] bytes = w.toByteArray();
        ClassNode check = new ClassNode(); new ClassReader(bytes).accept(check, 0);
        for (MethodNode m : check.methods) if ((m.access & (ACC_ABSTRACT | ACC_NATIVE)) == 0)
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name, m);
        Files.createDirectories(output.getParent()); Files.write(output, bytes);
        System.out.println("PASS native registration patch " + cls.name + ": " + changes + " paths; ASM verified");
    }
}
