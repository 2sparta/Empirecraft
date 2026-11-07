package guardtest;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;
public class IntegrationTest {
 public static void main(String[] args)throws Exception {
  MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.SERVER);
  Mixins.addConfiguration("empirecraft_calendar.mixins.json");
  Mixins.addConfiguration("empirecraft_seasonal_vegetation.mixins.json");
  var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();
  for(String name:new String[]{"ua.empirecraft.worldrules.NaturalRenewal","net.minecraft.world.level.block.SaplingBlock","net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase","net.minecraft.world.level.block.LeavesBlock"}){
   var node=service.getClassNode(name);
   if(!transformer.transformClass(env,name,node))throw new AssertionError("No transformation: "+name);
   String all="";
   for(MethodNode method:node.methods){
    if((method.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
    all+=method.name+" ";
   }
   if(name.endsWith("NaturalRenewal"))for(String hook:new String[]{"filterRare","rememberRare","seasonalRenewal","slowPlants","slowTrees","plantYoungTree","onePlantAttempt"}){
    if(!all.contains(hook))throw new AssertionError("Missing hook "+hook);
   }
   else if(name.endsWith("SaplingBlock") && !all.contains("slowNaturalGrowth"))throw new AssertionError("Missing tree growth guard");
   else if(name.endsWith("BlockStateBase") && !all.contains("growthSwitch"))throw new AssertionError("Missing random-tick growth guard");
   else if(name.endsWith("LeavesBlock") && !all.contains("keepSupport"))throw new AssertionError("Missing leaf-support guard");
   System.out.println("MIXIN_PASS "+name);
  }
 }
}
