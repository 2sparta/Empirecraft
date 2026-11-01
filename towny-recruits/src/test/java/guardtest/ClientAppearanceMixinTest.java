package guardtest;
import java.util.*;
import org.objectweb.asm.*;
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
  int pushes=0,pops=0;Set<String> synthetic=new TreeSet<>();boolean ordered=false;
  for(MethodNode m:node.methods){
   if((m.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,m);
   int remember=-1,scale=-1,nativeRender=-1,restore=-1,position=0;
   for(AbstractInsnNode insn:m.instructions){
    position++;
    if(insn instanceof MethodInsnNode call){
     scan(call.owner,synthetic);scan(call.desc,synthetic);
     if(call.owner.equals(node.name) && call.name.contains("rememberEntity"))remember=position;
     if(call.owner.equals(node.name) && call.name.contains("scalePose"))scale=position;
     if(call.owner.equals(node.name) && call.name.contains("restore"))restore=position;
     if(call.owner.equals("net/minecraft/client/renderer/entity/MobRenderer") && call.name.equals("m_7392_"))nativeRender=position;
     if(call.owner.equals("com/mojang/blaze3d/vertex/PoseStack")){if(call.name.equals("m_85836_"))pushes++;if(call.name.equals("m_85849_"))pops++;}
    }
    if(insn instanceof TypeInsnNode type)scan(type.desc,synthetic);
    if(insn instanceof FieldInsnNode field){scan(field.owner,synthetic);scan(field.desc,synthetic);}
   }
   if(m.name.equals("render") && m.desc.startsWith("(Lcom/talhanation/recruits/entities/AbstractRecruitEntity;"))ordered=remember>=0 && scale>remember && nativeRender>scale && restore>nativeRender;
  }
  if(!synthetic.isEmpty())throw new AssertionError("Client crash regression: generated Args dependency remains: "+synthetic);
  assert pushes==1&&pops==1;assert ordered:"Entity capture, pose scaling, native render and restoration order is invalid";
  System.out.println("PASS real Recruits client renderer transformation and ASM verification; ordered and balanced pose hooks; no generated Args classes required");
 }
 static void scan(String type,Set<String> found){if(type.contains("org/spongepowered/asm/synthetic/args/"))found.add(type);}
}
