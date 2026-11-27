package guardtest;
import java.util.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.service.MixinService;
public final class BridgeMixinTest {
 public static void main(String[] args)throws Exception{
  MixinBootstrap.init();var env=MixinEnvironment.getDefaultEnvironment();env.setSide(MixinEnvironment.Side.SERVER);Mixins.addConfiguration("townyrecruits.mixins.json");
  var service=(OfflineMixinService)MixinService.getService();var transformer=service.transformer();
  List<String> targets=new ArrayList<>(List.of("com.talhanation.recruits.entities.AbstractRecruitEntity","com.talhanation.recruits.RecruitEvents","com.talhanation.recruits.entities.ai.RecruitUpkeepPosGoal","com.talhanation.recruits.entities.ai.RecruitUpkeepEntityGoal","com.talhanation.recruits.world.RecruitsPlayerUnitManager","com.talhanation.recruits.entities.ai.compat.RecruitRangedMusketAttackGoal","com.talhanation.recruits.entities.ai.RecruitRangedCrossbowAttackGoal","com.talhanation.recruits.FactionEvents"));
  for(String name:List.of("MessageCreateTeam","MessageSaveTeamSettings","MessageLeaveTeam","MessageRemoveFromTeam","MessageSendJoinRequestTeam","MessageAddPlayerToTeam","MessageAddRecruitToTeam","MessageChangeDiplomacyStatus","MessageSendTreaty","MessageAnswerTreaty","MessageAddEmbargo","MessageAddEmbargoFaction","MessageRemoveEmbargo"))targets.add("com.talhanation.recruits.network."+name);
  for(String name:targets){var node=service.getClassNode(name);if(!transformer.transformClass(env,name,node))throw new AssertionError("No injection "+name);
   for(MethodNode m:node.methods)if((m.access&0x0500)==0)new Analyzer<>(new BasicVerifier()).analyze(node.name,m);
   System.out.println("PASS real Mixin transform + bytecode verification: "+name);
  }
 }
}
