package guardtest;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

public final class ContentMixinTest {
    public static void main(String[] args)throws Exception {
        MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.SERVER);
        // Both mods touch ItemStack; verify they can transform the same native class together.
        Mixins.addConfiguration("empirecraft_world_rules.mixins.json");
        Mixins.addConfiguration("empirecraft_content_policy.mixins.json");
        var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();
        for(String name:new String[]{"net.minecraft.world.item.ItemStack","ua.empirecraft.worldrules.SeedMarket","com.ninni.spawn.server.block.DaytimeGemBlock","com.ninni.spawn.server.block.entity.HeartOfTheSunBlockEntity","com.ninni.spawn.server.block.UnstableHeliostoneBlock","com.ninni.spawn.server.block.HeliostoneBlock","com.ninni.spawn.server.block.MagmaticSplatterBlock","com.ninni.spawn.server.entity.mob.Dodo"}) {
            var node=service.getClassNode(name);
            if(!transformer.transformClass(env,name,node))throw new AssertionError("No transformation: "+name);
            int helpers=0;for(MethodNode method:node.methods) {
                if((method.access&0x500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
                if(method.name.contains("empirecraft$"))helpers++;
            }
            if(helpers==0)throw new AssertionError(name);
            System.out.println("PASS actual native Mixin transformation and ASM verification: "+name);
        }
    }
}
