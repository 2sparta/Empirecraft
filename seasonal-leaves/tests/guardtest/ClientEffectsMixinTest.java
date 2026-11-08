package guardtest;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

public final class ClientEffectsMixinTest {
    public static void main(String[] args)throws Exception {
        MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("empirecraft_seasonal_vegetation.mixins.json");
        var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();
        String[][] targets={
            {"net.minecraft.client.Minecraft","attach"},
            {"net.minecraft.client.renderer.block.BlockModelShaper","leafFaces"},
            {"net.minecraft.client.color.block.BlockColors","autumn"},
            {"net.minecraft.client.multiplayer.ClientLevel","seasonSky"},
            {"me.jellysquid.mods.sodium.client.model.color.ColorProviderRegistry","leafColors"}
        };
        for(var target:targets) {
            if(args.length>0&&args[0].equals("vanilla")&&target[0].startsWith("me.jellysquid"))continue;
            var node=service.getClassNode(target[0]);
            if(!transformer.transformClass(env,target[0],node))throw new AssertionError("No transformation: "+target[0]);
            boolean hook=false;
            for(MethodNode m:node.methods) {
                hook|=m.name.contains(target[1]);
                if((m.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,m);
                for(var in:m.instructions)if(in instanceof MethodInsnNode call&&call.owner.startsWith("org/spongepowered/asm/synthetic/args/"))throw new AssertionError("Generated Args crash regression");
            }
            if(!hook)throw new AssertionError("Missing hook "+target[1]);
            if(target[0].endsWith("Minecraft")) {
                if(!node.interfaces.contains("ua/empirecraft/leaves/client/ClientContext"))throw new AssertionError("Missing typed client context");
                if(node.methods.stream().noneMatch(m->m.name.equals("empirecraft$level"))||node.methods.stream().noneMatch(m->m.name.equals("empirecraft$renderer")))throw new AssertionError("Client accessors not generated");
            }
            System.out.println("CLIENT_MIXIN_PASS "+target[0]+" real bytecode, verified hooks, no generated Args");
        }
    }
}
