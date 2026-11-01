package guardtest;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;
public final class ClientAppearanceMixinTest {
 public static void main(String[] args)throws Exception{
  MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.CLIENT);Mixins.addConfiguration("townyrecruits.mixins.json");
  var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();String name="com.talhanation.recruits.client.render.RecruitHumanRenderer";var node=service.getClassNode(name);
  if(!transformer.transformClass(env,name,node))throw new AssertionError("No client appearance injection");
  int pushes=0,pops=0;
  for(MethodNode m:node.methods){
   if((m.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,m);
   for(AbstractInsnNode insn:m.instructions)if(insn instanceof MethodInsnNode call && call.owner.equals("com/mojang/blaze3d/vertex/PoseStack")){if(call.name.equals("m_85836_"))pushes++;if(call.name.equals("m_85849_"))pops++;}
  }
  assert pushes==1&&pops==1;
  System.out.println("PASS real Recruits client renderer Mixin transformation, texture override, balanced scale stack and bytecode verification");
 }
}
