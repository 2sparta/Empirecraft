package ua.empirecraft.casting.test;
import ua.empirecraft.casting.*;import net.minecraft.server.MinecraftServer;import net.minecraft.world.item.*;import net.minecraft.world.item.crafting.*;import net.minecraft.resources.ResourceLocation;import net.minecraftforge.fluids.*;import net.minecraftforge.fluids.capability.IFluidHandler;import net.minecraftforge.registries.ForgeRegistries;import net.dries007.tfc.common.recipes.*;import net.dries007.tfc.common.recipes.inventory.ItemStackInventory;
public final class CrucibleProbe {
 static int checks;static void check(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
 static FluidStack fluid(String path,int units){return new FluidStack(ForgeRegistries.FLUIDS.getValue(new ResourceLocation("empirecraft_casting:"+path)),units);}
 static net.minecraftforge.items.IItemHandlerModifiable inputs(CastingProbe.Pair p){return ((ua.empirecraft.casting.mixin.InventoryAccess)p.crucible()).casting$inventory();}
 static void ticks(CastingProbe.Pair p,float temperature,int count){for(int i=0;i<count;i++){var heat=p.crucible().getCapability(net.dries007.tfc.common.capabilities.heat.HeatCapability.BLOCK_CAPABILITY).resolve().orElseThrow();heat.setTemperatureIfWarmer(temperature);heat.setTemperature(temperature);net.dries007.tfc.common.blockentities.CrucibleBlockEntity.serverTick(p.crucible().getLevel(),p.crucible().getBlockPos(),p.crucible().getBlockState(),p.crucible());}}
 static void hotFlux(CastingProbe.Pair p,int slot){var f=new ItemStack(CastingProbe.item("tfc:powder/flux"));net.dries007.tfc.common.capabilities.heat.HeatCapability.setTemperature(f,1600);inputs(p).setStackInSlot(slot,f);p.crucible().setAndUpdateSlots(slot);}
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
  // Regression: exercise native crucible ticks and both recipe-cache update paths.
  var standby=CastingProbe.pair();CastingProbe.fill(standby,"steel",800);hotFlux(standby,0);ticks(standby,1600,200);
  check(inputs(standby).getStackInSlot(0).is(flux),"Hot flux waits in a steel bath instead of becoming lime");
  var cool=CastingProbe.pair();CastingProbe.fill(cool,"pig_iron",100);hotFlux(cool,0);ticks(cool,1000,200);
  check(inputs(cool).getStackInSlot(0).is(flux)&&CastingProbe.amount(cool)==100,"Flux survives above lime temperature but below refining temperature");
  var continuous=CastingProbe.pair();CastingProbe.fill(continuous,"steel",1200);CastingProbe.fill(continuous,"pig_iron",100);hotFlux(continuous,0);ticks(continuous,1600,1);
  check(continuous.metal().getFluidInTank(0).isFluidEqual(CastingProbe.fluid("steel",1300))&&CastingProbe.amount(continuous)==1300,"A steel/pig-iron bath refines despite its initially unknown alloy result: temp="+continuous.crucible().getTemperature()+" alloy="+((net.dries007.tfc.util.Alloy)continuous.crucible().getAlloy()).serializeNBT()+" flux="+inputs(continuous).getStackInSlot(0));
  check(inputs(continuous).getStackInSlot(0).isEmpty(),"Only new pig iron is charged, not the 1200 units of existing steel");
  check(continuous.metal().drain(300,IFluidHandler.FluidAction.EXECUTE).getAmount()==300,"Refined steel can be poured between batches");
  CastingProbe.fill(continuous,"pig_iron",400);hotFlux(continuous,0);ticks(continuous,1600,1);
  check(CastingProbe.amount(continuous)==1400&&continuous.metal().getFluidInTank(0).isFluidEqual(CastingProbe.fluid("steel",1400))&&inputs(continuous).getStackInSlot(0).isEmpty(),"Pouring and refilling supports a second exact-volume refining cycle");
  // The charge arrives as a real item, melting after the HEAD injection in this tick.
  var fed=CastingProbe.pair();CastingProbe.fill(fed,"steel",800);var pigItem=new ItemStack(CastingProbe.item("tfc:metal/ingot/pig_iron"));net.dries007.tfc.common.capabilities.heat.HeatCapability.setTemperature(pigItem,1600);inputs(fed).setStackInSlot(0,pigItem);hotFlux(fed,1);ticks(fed,1600,1);
  check(inputs(fed).getStackInSlot(0).isEmpty()&&inputs(fed).getStackInSlot(1).isEmpty()&&CastingProbe.amount(fed)==900&&fed.metal().getFluidInTank(0).isFluidEqual(CastingProbe.fluid("steel",900)),"Newly melted item charge refines in the same native tick");
  var insufficient=CastingProbe.pair();CastingProbe.fill(insufficient,"steel",1000);CastingProbe.fill(insufficient,"pig_iron",1500);hotFlux(insufficient,0);ticks(insufficient,1600,100);
  check(inputs(insufficient).getStackInSlot(0).is(flux)&&CastingProbe.amount(insufficient)==2500,"Insufficient flux survives in a hot mixed bath");
  hotFlux(insufficient,1);ticks(insufficient,1600,1);check(CastingProbe.amount(insufficient)==2500&&insufficient.metal().getFluidInTank(0).isFluidEqual(CastingProbe.fluid("steel",2500))&&inputs(insufficient).getStackInSlot(0).isEmpty()&&inputs(insufficient).getStackInSlot(1).isEmpty(),"Adding the missing flux recovers the complete mixed bath");
  var foreign=CastingProbe.pair();CastingProbe.fill(foreign,"steel",100);CastingProbe.fill(foreign,"pig_iron",100);CastingProbe.fill(foreign,"copper",100);hotFlux(foreign,0);var foreignState=((net.dries007.tfc.util.Alloy)foreign.crucible().getAlloy()).serializeNBT();ticks(foreign,1600,200);
  check(foreignState.equals(((net.dries007.tfc.util.Alloy)foreign.crucible().getAlloy()).serializeNBT())&&inputs(foreign).getStackInSlot(0).is(flux),"Other alloy ingredients are neither erased nor converted to steel");
  var saved=CastingProbe.pair();CastingProbe.fill(saved,"steel",500);CastingProbe.fill(saved,"pig_iron",500);var savedNbt=saved.crucible().saveWithFullMetadata();var restored=(net.dries007.tfc.common.blockentities.CrucibleBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(saved.crucible().getBlockPos(),saved.crucible().getBlockState(),savedNbt);saved.crucible().setRemoved();level.setBlockEntity(restored);
  var recovered=new CastingProbe.Pair(saved.table(),restored,restored.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow());hotFlux(recovered,0);ticks(recovered,1600,1);
  check(CastingProbe.amount(recovered)==1000&&recovered.metal().getFluidInTank(0).isFluidEqual(CastingProbe.fluid("steel",1000)),"Existing mixed baths recover after save/reload without emptying the crucible");
  var fluxRecipe=HeatingRecipe.getRecipe(new ItemStack(flux));check(fluxRecipe!=null&&!fluxRecipe.assemble(new ItemStackInventory(new ItemStack(flux)),server.registryAccess()).is(flux),"Flux still has its normal heating recipe outside the crucible");
  System.out.println("CASTING_NATIVE_CRUCIBLE_PASS checks="+checks+" andesite=passed ore_bypass=removed sulfur_mod=absent retrogen=passed continuous_flux=passed flux_waiting=passed");
 }
}
