package guardtest;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

public final class RuntimeMixinTest {
    public static void main(String[] args)throws Exception {
        MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("empirecraft_pack_fixes.mixins.json");
        var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        for(String name:new String[]{"net.minecraft.world.level.chunk.HashMapPalette","net.minecraft.world.entity.animal.horse.AbstractHorse","net.createmod.ponder.foundation.registration.PonderSceneRegistry","com.simibubi.create.foundation.item.ItemDescription$Modifier","ua.empirecraft.create.MedievalPolicy"}) {
            var node=service.getClassNode(name);
            if(!transformer.transformClass(env,name,node))throw new AssertionError("Missing runtime mixin "+name);
            var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);node=new ClassNode();new ClassReader(writer.toByteArray()).accept(node,ClassReader.EXPAND_FRAMES);
            boolean injected=false;
            for(var method:node.methods) {
                if((method.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
                if(method.name.contains("empirecraft$"))injected=true;
            }
            if(!injected)throw new AssertionError(name);
            System.out.println("RUNTIME_MIXIN_PASS "+name);
        }
    }
}
