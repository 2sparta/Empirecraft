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
  System.out.println("CASTING_NATIVE_CRUCIBLE_PASS checks="+checks+" andesite=passed ore_bypass=removed sulfur_mod=absent retrogen=passed");
 }
}
