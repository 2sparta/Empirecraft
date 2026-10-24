package compatcheck;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

/** Apply the real installed Valkyrien Skies mixins to the real medieval Create classes. */
public final class CreateMixinTest {
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        var env=MixinEnvironment.getDefaultEnvironment();
        env.setSide(args[0].equals("client") ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("create-vs-regression.mixins.json");
        var service=(OfflineMixinService)MixinService.getService();
        var transformer=service.transformer();
        env.setActiveTransformer(transformer);
        MixinExtrasBootstrap.init();
        for(String name : new String[]{
                "com.simibubi.create.content.kinetics.fan.NozzleBlockEntity",
                "com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity",
                "com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity"}) {
            var node=service.getClassNode(name);
            if(!transformer.transformClass(env,name,node))throw new AssertionError("No VS mixin applied to "+name);
            // MixinExtras adds synthetic methods before the normal class-writing stage computes maxima.
            var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            var emitted=new ClassNode();
            new ClassReader(writer.toByteArray()).accept(emitted,ClassReader.EXPAND_FRAMES);
            for(MethodNode method:emitted.methods) {
                if((method.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
            }
            System.out.println("PASS actual VS mixins and ASM verification ["+args[0]+"]: "+name);
        }
    }
}
