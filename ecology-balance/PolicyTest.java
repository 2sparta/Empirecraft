import ua.empirecraft.worldrules.TreeRules;
import ua.empirecraft.calendar.HabitatRules;
public final class PolicyTest {
 public static void main(String[] args) {
  int rare=0;for(int i=0;i<10000;i++)if(TreeRules.useRare(i/10000.,false,false,.1))rare++;
  assert rare==1000;
  assert !TreeRules.useRare(.5,false,true,.1);
  assert TreeRules.weight("minecraft:acacia_sapling","minecraft:birch_forest",.6f)==0;
  assert TreeRules.weight("minecraft:birch_sapling","minecraft:birch_forest",.6f)>TreeRules.weight("minecraft:oak_sapling","minecraft:birch_forest",.6f);
  assert TreeRules.weight("minecraft:spruce_sapling","minecraft:taiga",.25f)>0;
  assert HabitatRules.factor("naturalist:butterfly","insect",0,.6f,"minecraft:forest",false)==0;
  assert HabitatRules.factor("naturalist:butterfly","insect",2.5,.6f,"minecraft:forest",false)>2.5;
  assert HabitatRules.factor("naturalist:butterfly","insect",0,1.5f,"minecraft:jungle",false)>0;
  assert HabitatRules.factor("naturalist:bear","bear",3,.6f,"minecraft:forest",false)>HabitatRules.factor("naturalist:bear","bear",.6,.6f,"minecraft:forest",false);
  assert HabitatRules.factor("minecraft:zombie","unchanged",1,.2f,"minecraft:taiga",true)==1;
  assert HabitatRules.factor("minecraft:polar_bear","cold",2,0,"minecraft:snowy_plains",false)>HabitatRules.factor("minecraft:polar_bear","cold",2,1,"minecraft:plains",false);
  assert HabitatRules.factor("spawn:frog","wetland",2,.8f,"minecraft:swamp",true)>HabitatRules.factor("spawn:frog","wetland",2,.8f,"minecraft:swamp",false);
  System.out.println("PASS 90/10 tree pools, biome species, seasonal mod preference, tropical winters, wetlands, cold habitats and unchanged monsters");
 }
}
