import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;

/** Version-pinned offline ASM patcher, using the ASM bundled with JDK 17. */
public class PatchCreate implements Opcodes {
    static final String POLICY = "ua/empirecraft/create/MedievalPolicy";
    static final List<String> changes = new ArrayList<>();
    static final String[] BLOCKED = {
        "/kinetics/steamEngine/", "/kinetics/motor/", "/kinetics/fan/", "/kinetics/crafter/",
        "/kinetics/crusher/", "/kinetics/deployer/", "/kinetics/drill/", "/kinetics/mixer/",
        "/kinetics/mechanicalArm/", "/kinetics/speedController/", "/kinetics/chainConveyor/",
        "/fluids/", "/trains/", "/schematics/cannon/", "/processing/burner/",
        "/contraptions/elevator/", "/contraptions/mounted/", "/contraptions/actors/harvester/",
        "/contraptions/actors/roller/", "/contraptions/actors/plough/", "/contraptions/actors/psi/",
        "/logistics/packager", "/logistics/packagePort/", "/logistics/factoryBoard/",
        "/logistics/stockTicker/", "/logistics/redstoneRequester/", "/redstone/link/",
        "/redstone/nixieTube/", "/redstone/displayLink/", "/equipment/armor/"
    };
    static boolean blocked(String name) { return Arrays.stream(BLOCKED).anyMatch(name::contains); }
    static void replace(MethodNode method, InsnList body) {
        method.instructions = body;
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
    }
    static InsnList nothing() { InsnList b = new InsnList(); b.add(new InsnNode(RETURN)); return b; }
    static InsnList zero() { InsnList b = new InsnList(); b.add(new InsnNode(ICONST_0)); b.add(new InsnNode(IRETURN)); return b; }
    static void patch(ClassNode node) {
        if (node.name.endsWith("/steamEngine/PoweredShaftBlockEntity") || node.name.endsWith("/motor/CreativeMotorBlockEntity")) {
            // Also neutralize saved speed/capacity while old worlds rebuild their kinetic networks.
            for (String name : new String[]{"getSpeed", "getTheoreticalSpeed", "calculateAddedStressCapacity"}) {
                if (node.methods.stream().noneMatch(m -> m.name.equals(name) && m.desc.equals("()F"))) {
                    MethodNode method = new MethodNode(ACC_PUBLIC, name, "()F", null, null);
                    method.instructions.add(new InsnNode(FCONST_0)); method.instructions.add(new InsnNode(FRETURN));
                    node.methods.add(method); changes.add(node.name + "#" + name + "()F [override]");
                }
            }
        }
        for (MethodNode m : node.methods) {
            boolean changed = false;
            if (node.name.equals("com/simibubi/create/Create") && m.name.equals("<init>")) {
                for (AbstractInsnNode n : m.instructions.toArray()) if (n.getOpcode() == RETURN)
                    m.instructions.insertBefore(n, new MethodInsnNode(INVOKESTATIC, POLICY, "register", "()V", false));
                changed = true;
            }
            if (node.name.endsWith("AllCreativeModeTabs$RegistrateDisplayItemsGenerator") && m.name.equals("outputAll")) {
                InsnList b = new InsnList(); b.add(new VarInsnNode(ALOAD, 1));
                b.add(new MethodInsnNode(INVOKESTATIC, POLICY, "filter", "(Ljava/util/List;)Ljava/util/List;", false));
                b.add(new VarInsnNode(ASTORE, 1)); m.instructions.insert(b); changed = true;
            }
            if (node.name.equals("com/simibubi/create/compat/jei/CreateJEI") && m.name.equals("onRuntimeAvailable")) {
                // Run after Create's own runtime setup so its added ingredients are filtered too.
                for (AbstractInsnNode n : m.instructions.toArray()) if (n.getOpcode() == RETURN) {
                    InsnList b = new InsnList(); b.add(new VarInsnNode(ALOAD, 1));
                    b.add(new MethodInsnNode(INVOKESTATIC, POLICY, "hideJei", "(Ljava/lang/Object;)V", false));
                    m.instructions.insertBefore(n, b);
                }
                changed = true;
            }
            if (node.name.contains("/fan/processing/AllFanProcessingTypes$")) {
                if (m.name.equals("isValidAt") || m.name.equals("canProcess")) { replace(m, zero()); changed = true; }
                if (m.name.equals("process")) {
                    InsnList b = new InsnList(); b.add(new VarInsnNode(ALOAD, 1));
                    b.add(new MethodInsnNode(INVOKESTATIC, "java/util/Collections", "singletonList", "(Ljava/lang/Object;)Ljava/util/List;", false));
                    b.add(new InsnNode(ARETURN)); replace(m, b); changed = true;
                }
                if (m.name.equals("affectEntity")) { replace(m, nothing()); changed = true; }
            }
            if (blocked(node.name) && (m.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) == 0) {
                if (Set.of("tick", "lazyTick", "visitNewPosition", "startMoving", "stopMoving", "onBlockBroken", "activate").contains(m.name)
                        && Type.getReturnType(m.desc).equals(Type.VOID_TYPE)) { replace(m, nothing()); changed = true; }
                if (Set.of("getGeneratedSpeed", "getSpeed", "getTheoreticalSpeed", "calculateAddedStressCapacity").contains(m.name) && m.desc.equals("()F")) {
                    InsnList b = new InsnList(); b.add(new InsnNode(FCONST_0)); b.add(new InsnNode(FRETURN));
                    replace(m, b); changed = true;
                }
            }
            if (changed) changes.add(node.name + "#" + m.name + m.desc);
        }
    }
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        Files.createDirectories(output);
        try (ZipFile jar = new ZipFile(input.toFile())) {
            for (ZipEntry entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class") || !entry.getName().startsWith("com/simibubi/create/")) continue;
                byte[] original = jar.getInputStream(entry).readAllBytes();
                ClassNode node = new ClassNode(); new ClassReader(original).accept(node, 0);
                int before = changes.size(); patch(node);
                if (before == changes.size()) continue;
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
                byte[] patched = writer.toByteArray();
                ClassNode check = new ClassNode(); new ClassReader(patched).accept(check, 0);
                for (MethodNode method : check.methods) if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) == 0)
                    new Analyzer<BasicValue>(new BasicVerifier()).analyze(check.name, method);
                Path file = output.resolve(entry.getName()); Files.createDirectories(file.getParent()); Files.write(file, patched);
            }
        }
        for (String required : new String[]{"Create#<init>", "Generator#outputAll", "CreateJEI#onRuntimeAvailable", "SplashingType#isValidAt", "SteamEngineBlockEntity#tick", "SchematicannonBlockEntity#tick"})
            if (changes.stream().noneMatch(s -> s.contains(required))) throw new IllegalStateException("Missing required patch: " + required);
        Files.write(output.resolve("patches.txt"), changes);
        System.out.println("Patched and verified " + changes.size() + " methods.");
    }
}
