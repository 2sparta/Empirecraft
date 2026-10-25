package guardtest;

import org.objectweb.asm.tree.*;
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
        for (String name : targets) {
            var node = service.getClassNode(name);
            if (!transformer.transformClass(environment, name, node)) throw new AssertionError("No transformation: "+name);
            int helpers = 0;
            for (MethodNode method : node.methods) {
                if ((method.access & 0x500) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
                if (method.name.contains("empirecraft$")) helpers++;
            }
            if (helpers == 0) throw new AssertionError(name);
            System.out.println("PASS native "+args[0]+" Mixin and ASM verification: "+name);
        }
    }
}
