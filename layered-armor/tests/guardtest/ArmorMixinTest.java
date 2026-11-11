package guardtest;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;

public final class ArmorMixinTest {
  public static void main(String[] args)throws Exception {
    MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);
    Mixins.addConfiguration("empirecraft_armor.mixins.json");
    var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();
    com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
    for(String name:new String[]{"net.minecraft.world.inventory.AbstractContainerMenu","net.minecraft.world.inventory.InventoryMenu","net.minecraft.client.gui.screens.inventory.InventoryScreen","net.minecraft.world.entity.player.Inventory","net.minecraft.world.entity.LivingEntity","net.minecraft.world.entity.player.Player","net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer"}){
      var node=service.getClassNode(name);if(!transformer.transformClass(env,name,node))throw new AssertionError("Missing compatibility hook: "+name);
      var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);node=new ClassNode();new ClassReader(writer.toByteArray()).accept(node,ClassReader.EXPAND_FRAMES);
      boolean helper=false;
      for(var method:node.methods){
        if((method.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.startsWith("ua/empirecraft/armor/"))helper=true;
      }
      if(!helper && !name.endsWith("HumanoidArmorLayer"))throw new AssertionError("Compatibility helper missing: "+name);
      System.out.println("ARMOR_MIXIN_PASS exact installed bytecode "+name);
    }
  }
}
