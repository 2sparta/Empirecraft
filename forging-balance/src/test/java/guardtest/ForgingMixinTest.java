package guardtest;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

public final class ForgingMixinTest {
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(args[0].equals("client") ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("empirecraft_forging.mixins.json");
        var service = (OfflineMixinService)MixinService.getService();
        var transformer = service.transformer();
        var targets = new java.util.ArrayList<>(java.util.List.of(
            "net.stirdrem.overgeared.item.custom.BlueprintItem",
            "net.stirdrem.overgeared.item.ToolTypeRegistry",
            "net.stirdrem.overgeared.recipe.ForgingRecipe",
            "net.minecraft.world.item.crafting.RecipeManager",
            "net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity",
            "net.stirdrem.overgeared.networking.packet.PacketSendCounterC2SPacket",
            "com.TBK.medieval_boomsticks.common.items.RechargeItem"));
        if (args[0].equals("client")) targets.add("net.stirdrem.overgeared.event.AnvilMinigameEvents");
        if (args.length > 1) { targets.clear(); targets.add(args[1]); }
        for (String name : targets) {
            var node = service.getClassNode(name);
            if (!transformer.transformClass(environment, name, node)) throw new AssertionError("No transformation: "+name);
            if (args.length > 2 && args[2].equals("linkage")) { PacketLinkage.verify(node); continue; }
            var referenceCheck = new ClassRemapper(new ClassWriter(0), new Remapper() {
                @Override public String map(String internalName) {
                    if (internalName.startsWith("ua/empirecraft/forging/mixin/"))
                        throw new AssertionError("Illegal runtime Mixin reference in "+name+": "+internalName);
                    return internalName;
                }
            });
            // Mixin's final writer recomputes frames; debug local types are not linkage references.
            node.accept(new ClassVisitor(Opcodes.ASM9, referenceCheck) {
                @Override public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, methodName, descriptor, signature, exceptions)) {
                        @Override public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {}
                        @Override public void visitLocalVariable(String name, String desc, String sig, Label start, Label end, int index) {}
                    };
                }
            });
            int helpers = 0;
            for (MethodNode method : node.methods) {
                if ((method.access & 0x500) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
                if (method.name.contains("empirecraft$")) helpers++;
            }
            if (helpers == 0) throw new AssertionError(name);
            if (name.endsWith(".PacketSendCounterC2SPacket")) PacketLinkage.verify(node);
            System.out.println("PASS native "+args[0]+" Mixin and ASM verification: "+name);
        }
    }
}
