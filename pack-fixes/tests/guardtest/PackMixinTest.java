package guardtest;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

public final class PackMixinTest {
  public static void main(String[] args)throws Exception {
    MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);
    Mixins.addConfiguration("empirecraft_pack_fixes.mixins.json");
    var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();
    com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
    for(String name:new String[]{"ua.empirecraft.engagement.client.FlagRenderer","ua.empirecraft.engagement.NavalFlags","net.minecraft.server.level.ServerPlayerGameMode","net.minecraft.server.network.ServerGamePacketListenerImpl"}){
      var node=service.getClassNode(name);if(!transformer.transformClass(env,name,node))throw new AssertionError("Missing compatibility hook: "+name);
      var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);node=new ClassNode();new ClassReader(writer.toByteArray()).accept(node,ClassReader.EXPAND_FRAMES);
      boolean helper=false;
      for(var method:node.methods){
        if((method.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.startsWith("ua/empirecraft/packfixes/"))helper=true;
      }
      if(!helper)throw new AssertionError("Compatibility helper missing: "+name);
      System.out.println("PACK_MIXIN_PASS exact installed bytecode "+name);
    }
  }
}
