package ua.empirecraft.tywtest;
import java.util.*;import java.io.*;import com.google.gson.*;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.item.*;import net.minecraft.world.item.crafting.*;import net.minecraft.world.inventory.TransientCraftingContainer;import net.minecraft.world.SimpleContainer;import net.minecraftforge.fml.common.Mod;import net.minecraftforge.common.MinecraftForge;import net.minecraftforge.event.server.ServerStartedEvent;import net.minecraftforge.registries.ForgeRegistries;import net.dries007.tfc.common.capabilities.heat.HeatCapability;import net.stirdrem.overgeared.recipe.ForgingRecipe;
@Mod("empirecraft_tyw_probe")
public class Probe {
 static int checks;static java.util.List<String> problems=new java.util.ArrayList<>();static void check(boolean b,String why){checks++;if(!b)problems.add(why);}
 public Probe(){MinecraftForge.EVENT_BUS.addListener(this::start);}
 void start(ServerStartedEvent event){var server=event.getServer();try{
  var level=server.overworld();var data=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/tyw-recipes.json"))).getAsJsonObject();
  var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);var conflicts=new ArrayList<String>();int forgeCount=0,craftCount=0;
  for(var entry:data.entrySet()){
   String name="thirtyyearswar:"+entry.getKey();var spec=entry.getValue().getAsJsonObject();var actual=server.getRecipeManager().byKey(new ResourceLocation(name)).orElseThrow(()->new AssertionError("Missing "+name));
   check(!spec.toString().contains("netherite"),"No nether dependency "+name);
   check(actual.getResultItem(server.registryAccess()).is(ForgeRegistries.ITEMS.getValue(new ResourceLocation((spec.has("result")?spec.getAsJsonObject("result"):spec.has("recipe")?spec.getAsJsonObject("recipe").getAsJsonObject("result"):spec.getAsJsonArray("results").get(0).getAsJsonObject()).get("item").getAsString()))),"Correct output "+name);
   for(var ingredient:actual.getIngredients())check(ingredient.isEmpty()||ingredient.getItems().length>0,"Resolvable ingredient "+name+" "+ingredient);
   if(actual instanceof ShapedRecipe shaped){
    var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);
    for(int y=0;y<shaped.getHeight();y++)for(int x=0;x<shaped.getWidth();x++){var i=shaped.getIngredients().get(y*shaped.getWidth()+x);if(!i.isEmpty()&&i.getItems().length>0){var stack=i.getItems()[0].copy();stack.getOrCreateTag().putString("ForgingQuality","master");grid.setItem(y*3+x,stack);}}
    var selected=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level);if(selected.isEmpty()||!selected.get().getId().equals(actual.getId()))conflicts.add(name+" selected "+selected.map(r->r.getId().toString()).orElse("none"));
    var out=shaped.assemble(grid,server.registryAccess());if(actual instanceof ua.empirecraft.casting.QualityRecipe)check(out.hasTag()&&out.getTag().getString("ForgingQuality").equals("master"),"Assembly preserves quality "+name);craftCount++;
   }else if(actual instanceof ShapelessRecipe shapeless){
    var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);int slot=0;for(var i:shapeless.getIngredients())if(i.getItems().length>0)grid.setItem(slot++,i.getItems()[0].copy());var selected=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level);if(selected.isEmpty()||!selected.get().getId().equals(actual.getId()))conflicts.add(name+" selected "+selected.map(r->r.getId().toString()).orElse("none"));craftCount++;
   }else if(actual instanceof ForgingRecipe forging){
    var inputs=new SimpleContainer(12);boolean heat=false;
    for(int y=0;y<forging.getHeight();y++)for(int x=0;x<forging.getWidth();x++){var i=forging.getIngredients().get(y*forging.getWidth()+x);if(!i.isEmpty()&&i.getItems().length>0){var stack=i.getItems()[0].copy();HeatCapability.setTemperature(stack,1400);inputs.setItem(y*3+x,stack);}}
    for(var i:forging.getForgingIngredients()){if(i.ingredient().isEmpty()||i.ingredient().getItems().length==0)continue;var sample=new ItemStack(i.ingredient().getItems()[0].getItem());HeatCapability.setTemperature(sample,0);if(!i.test(sample)){HeatCapability.setTemperature(sample,1400);check(i.test(sample),"Hot ingredient accepted "+name);heat=true;}}
    var selected=ForgingRecipe.findBestMatch(level,inputs);if(selected.isEmpty()||!selected.get().getId().equals(actual.getId()))conflicts.add(name+" selected forge "+selected.map(r->r.getId().toString()).orElse("none"));check(heat,"Metal forging needs heat "+name);forgeCount++;
   }
  }
  for(String weapon:List.of("baroque_steel_pike","baroque_steel_partisan","baroque_steel_rapier"))check(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("thirtyyearswar:"+weapon))).is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation("tfc:deals_piercing_damage"))),"TFC piercing tag "+weapon);
  check(conflicts.isEmpty(),"Recipe conflicts: "+conflicts);if(!problems.isEmpty())throw new AssertionError(problems.toString());
  System.out.println("TYW_NATIVE_PASS checks="+checks+" recipes="+data.size()+" forging="+forgeCount+" crafting="+craftCount+" conflicts=0");
 }catch(Throwable e){System.out.println("TYW_NATIVE_FAIL "+e);e.printStackTrace();}finally{server.halt(false);}}
}
