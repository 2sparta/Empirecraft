import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class PatchRemembered {
    public static void main(String[] args) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node, 0);
        int hooks = 0;
        for (var method : node.methods) {
            if (!method.name.equals("trmt$onTick") || !method.desc.equals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")) continue;
            var code = new InsnList(); var vanilla = new LabelNode();
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            code.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/entity/LivingEntity"));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "ua/empirecraft/trmt/RegionalTrails", "walk", "(Lnet/minecraft/world/entity/LivingEntity;)Z", false));
            code.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
            code.add(new InsnNode(Opcodes.RETURN));
            code.add(vanilla); code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            // Return only from TRMT's injected handler. Never cancel the Minecraft entity tick.
            method.instructions.insert(code); hooks++;
        }
        if (hooks != 1) throw new IllegalStateException("Unexpected TRMT walking implementation: " + hooks);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        Files.write(Path.of(args[1]), writer.toByteArray());
    }
}
