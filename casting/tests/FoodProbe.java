package ua.empirecraft.casting.test;
import net.minecraft.server.MinecraftServer;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.item.*;import net.minecraft.world.item.crafting.*;import net.minecraft.tags.TagKey;import net.minecraft.core.registries.Registries;import ua.empirecraft.casting.*;
public final class FoodProbe {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void check(MinecraftServer server){
  for(String kind:new String[]{"dough","flour"})for(String grain:new String[]{"wheat","maize","barley","oat","rye","rice"}){var item=new ItemStack(CastingProbe.item("tfc:food/"+grain+"_"+kind));check(item.is(TagKey.create(Registries.ITEM,new ResourceLocation("forge:"+kind))),"All grains join common food ingredients: "+item);}
  int uses=0;var mods=new java.util.HashSet<String>();
  for(var r:server.getRecipeManager().getRecipes()){
   check(!FoodPolicy.removed(Thermal.id(r.getResultItem(server.registryAccess()))),"No removed output: "+r.getId());
   for(var ingredient:r.getIngredients())for(var item:ingredient.getItems())check(!FoodPolicy.removed(Thermal.id(item)),"No removed recipe representative: "+r.getId()+" "+item);
   if(!java.util.Set.of("tfc","empirecraft_casting").contains(r.getId().getNamespace()))for(var ingredient:r.getIngredients())if(ingredient.test(new ItemStack(CastingProbe.item("tfc:food/maize_dough")))){uses++;mods.add(r.getId().getNamespace());}
  }
  check(uses>2&&mods.contains("farmersdelight")&&mods.contains("create"),"Maize dough is usable across other mods");
  var nativeDough=server.getRecipeManager().byKey(new ResourceLocation("tfc:crafting/dough/wheat_dough_1")).orElseThrow();for(var ingredient:nativeDough.getIngredients())for(var item:ingredient.getItems())check(!FoodPolicy.removed(Thermal.id(item)),"Fluid recipes show allowed containers");
  check(Thermal.id(Thermal.normalize(new ItemStack(CastingProbe.item("create:dough")))).equals("tfc:food/wheat_dough"),"Stored dough migrates");
  for(String id:new String[]{"empirecraft_casting:assembly/boomsticks/javelin","empirecraft_casting:assembly/boomsticks/throwing_knife","empirecraft_casting:ammo/musket_cartridge","empirecraft_casting:arquebus/ammunition"}){
   var recipe=server.getRecipeManager().byKey(new ResourceLocation(id)).orElseThrow();var player=net.minecraftforge.common.util.FakePlayerFactory.get(server.overworld(),new com.mojang.authlib.GameProfile(java.util.UUID.fromString("04108eb4-999a-40ca-802c-d0c88147a2c1"),"FoodRecipes"));var grid=new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu,3,3);int width=recipe instanceof ShapedRecipe s?s.getWidth():3;
   var list=recipe.getIngredients();for(int slot=0;slot<list.size();slot++)if(!list.get(slot).isEmpty()){var input=list.get(slot).getItems()[0].copy();if(Thermal.id(input).startsWith("tfc:metal/"))input.getOrCreateTag().putString("ForgingQuality","master");grid.setItem(slot/width*3+slot%width,input);}
   var selected=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,server.overworld());check(selected.isPresent()&&selected.get().getId().equals(recipe.getId()),"Exact weapon/ammunition recipe selected: "+id);var output=((CraftingRecipe)recipe).assemble(grid,server.registryAccess());check(!output.isEmpty(),"Weapon/ammunition craft succeeds: "+id);if(recipe instanceof QualityRecipe)check(output.hasTag()&&output.getTag().getString("ForgingQuality").equals("master"),"Thrown weapon preserves forged quality");
  }
  System.out.println("CASTING_NATIVE_FOOD_PASS checks="+checks+" maize_external_uses="+uses+" buckets=passed armor=passed retired_metals=passed");
 }
}
