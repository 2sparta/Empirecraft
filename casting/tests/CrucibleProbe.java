package ua.empirecraft.casting.test;
import ua.empirecraft.casting.*;import net.minecraft.server.MinecraftServer;import net.minecraft.world.item.*;import net.minecraft.world.item.crafting.*;import net.minecraft.resources.ResourceLocation;import net.minecraftforge.fluids.*;import net.minecraftforge.fluids.capability.IFluidHandler;import net.minecraftforge.registries.ForgeRegistries;import net.dries007.tfc.common.recipes.*;import net.dries007.tfc.common.recipes.inventory.ItemStackInventory;
public final class CrucibleProbe {
 static int checks;static void check(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
 static FluidStack fluid(String path,int units){return new FluidStack(ForgeRegistries.FLUIDS.getValue(new ResourceLocation("empirecraft_casting:"+path)),units);}
 public static void check(MinecraftServer server)throws Exception{
  check(!net.minecraftforge.fml.ModList.get().isLoaded("sulfur_gunpowder"),"Obsolete sulfur mod is absent");
  var andesite=new ItemStack(Items.ANDESITE);var heating=HeatingRecipe.getRecipe(andesite);check(heating!=null&&heating.assembleFluid(new ItemStackInventory(andesite)).isFluidEqual(fluid("andesite_melt",90)),"Andesite has its own melting recipe");
  for(String metal:new String[]{"cast_iron","zinc"}){var pair=CastingProbe.pair();CastingProbe.mold(pair);pair.metal().fill(fluid("andesite_melt",90),IFluidHandler.FluidAction.EXECUTE);CastingProbe.fill(pair,metal,10);CastingProbe.heat(pair);check(pair.metal().drain(100,IFluidHandler.FluidAction.SIMULATE).isFluidEqual(fluid("andesite_alloy",100)),"Actual crucible mixes andesite with "+metal);CastingProbe.run(pair,CastingBlockEntity.CYCLE_TICKS);check(CastingProbe.out(pair).is(CastingProbe.item("create:andesite_alloy"))&&CastingProbe.amount(pair)==0,"Exact 100-unit batch casts one alloy with "+metal);}
  var level=server.overworld();var input=new net.minecraft.world.SimpleContainer(new ItemStack(Items.RAW_IRON));check(server.getRecipeManager().getRecipeFor(RecipeType.SMELTING,input,level).isEmpty()&&server.getRecipeManager().getRecipeFor(RecipeType.BLASTING,input,level).isEmpty(),"Raw iron has no furnace bypass");
  var method=Class.forName("ua.empirecraft.ores.OreRetrogen").getDeclaredMethod("requireBlock",String.class);method.setAccessible(true);check(method.invoke(null,"sulfur_gunpowder:sulfurore")==ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc:ore/sulfur/andesite")),"Existing one-time ore pass now generates TFC sulfur");
  var refined=CastingProbe.pair();CastingProbe.fill(refined,"pig_iron",1500);
  var inv=((ua.empirecraft.casting.mixin.InventoryAccess)refined.crucible()).casting$inventory();
  var flux=CastingProbe.item("tfc:powder/flux");inv.setStackInSlot(0,new ItemStack(flux));
  CrucibleRefining.step(refined.crucible());check(CastingProbe.amount(refined)==1500&&inv.getStackInSlot(0).getCount()==1,"Cold refining consumes neither flux nor metal");
  CastingProbe.heat(refined);CrucibleRefining.step(refined.crucible());check(CastingProbe.amount(refined)==1500&&!inv.getStackInSlot(0).isEmpty(),"Insufficient flux preserves whole batch");
  inv.setStackInSlot(1,new ItemStack(flux));inv.setStackInSlot(2,new ItemStack(flux));
  check(refined.crucible().isItemValid(0,new ItemStack(flux)),"Flux is accepted by the real crucible input");
  CrucibleRefining.step(refined.crucible());check(refined.metal().getFluidInTank(0).isFluidEqual(CastingProbe.fluid("steel",1500))&&CastingProbe.amount(refined)==1500,"Flux converts exact molten pig iron volume to steel");
  check(inv.getStackInSlot(0).isEmpty()&&inv.getStackInSlot(1).isEmpty()&&inv.getStackInSlot(2).getCount()==1,"Exact rounded-up flux cost with excess preserved");
  CrucibleRefining.step(refined.crucible());check(inv.getStackInSlot(2).getCount()==1,"Steel cannot consume flux a second time");
  var mixed=CastingProbe.pair();CastingProbe.fill(mixed,"copper",100);CastingProbe.heat(mixed);var mixInv=((ua.empirecraft.casting.mixin.InventoryAccess)mixed.crucible()).casting$inventory();mixInv.setStackInSlot(0,new ItemStack(flux));CrucibleRefining.step(mixed.crucible());check(mixed.metal().getFluidInTank(0).isFluidEqual(CastingProbe.fluid("copper",100))&&!mixInv.getStackInSlot(0).isEmpty(),"Unrelated metals do not refine");
  CastingProbe.mold(refined);CastingProbe.run(refined,CastingBlockEntity.CYCLE_TICKS);check(CastingProbe.out(refined).is(CastingProbe.item("overgeared:steel_ingot")),"Refined liquid casts a canonical steel ingot");
  for(String metal:new String[]{"createbigcannons:bronze_ingot","tfc:metal/ingot/pig_iron","tfc:metal/ingot/black_steel","create:brass_ingot","magistuarmory:silver_ingot"})for(var type:java.util.List.of(RecipeType.SMELTING,RecipeType.BLASTING)){
   var stack=new ItemStack(CastingProbe.item(metal));var recipe=server.getRecipeManager().getRecipeFor(type,new net.minecraft.world.SimpleContainer(stack),level);check(recipe.isPresent()&&recipe.get().getResultItem(server.registryAccess()).is(stack.getItem()),"Metal reheats to itself: "+metal+" "+type);
  }
  System.out.println("CASTING_NATIVE_CRUCIBLE_PASS checks="+checks+" andesite=passed ore_bypass=removed sulfur_mod=absent retrogen=passed");
 }
}
