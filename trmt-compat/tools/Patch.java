import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class Patch {
    public static void main(String[] args) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node, 0);
        int comparisons = 0, reads = 0;
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !call.owner.equals("net/minecraft/world/level/block/state/BlockState")) continue;
                String name;
                String descriptor;
                if (call.name.equals("m_60713_") && call.desc.equals("(Lnet/minecraft/world/level/block/Block;)Z")) {
                    name = "matches";
                    descriptor = "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/Block;)Z";
                    comparisons++;
                } else if (call.name.equals("m_60734_") && call.desc.equals("()Lnet/minecraft/world/level/block/Block;")) {
                    name = "trackedBlock";
                    descriptor = "(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/Block;";
                    reads++;
                } else continue;
                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "ua/empirecraft/trmt/Surfaces", name, descriptor, false));
            }
        }
        if (comparisons < 15 || reads < 2) throw new IllegalStateException("Unexpected TRMT walking implementation");
        var writer = new ClassWriter(0);
        node.accept(writer);
        Files.write(Path.of(args[1]), writer.toByteArray());
        System.out.println(node.name + " comparisons=" + comparisons + " reads=" + reads);
    }
}
