package guardtest;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
public final class WorkshopMixinTest {
    public static void main(String[] args)throws Exception{
        MixinBootstrap.init();var environment=MixinEnvironment.getDefaultEnvironment();environment.setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("empirecraft_workshop.mixins.json");Mixins.addConfiguration("empirecraft_locks_compat.mixins.json");
        var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();
        String[] targets={"me.mss1r.siegeworks.menu.CastingMenu","net.dries007.tfc.common.blockentities.TFCChestBlockEntity","net.dries007.tfc.common.blocks.wood.TFCChestBlock$1$1","net.dries007.tfc.common.capabilities.InventoryWrapper","net.minecraftforge.items.wrapper.InvWrapper","net.minecraftforge.items.wrapper.SidedInvWrapper"};
        for(String target:targets){var node=service.getClassNode(target);if(!transformer.transformClass(environment,target,node))throw new AssertionError("Not transformed: "+target);
            for(var method:node.methods)if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0){
                new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
                for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.startsWith("org/spongepowered/asm/synthetic/"))throw new AssertionError("Hybrid-incompatible synthetic helper: "+call.owner);
            }
            System.out.println("PASS real class transformation and bytecode verifier: "+target);
        }
    }
}
