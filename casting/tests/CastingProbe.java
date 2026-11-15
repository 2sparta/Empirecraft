package ua.empirecraft.casting.test;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.capabilities.MoldLike;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import ua.empirecraft.casting.*;

/** Real native block entities, molten alloys, recipes, capabilities and saved mold items. */
@Mod("empirecraft_casting_probe")
public final class CastingProbe {
 static int checks,cursor=0;static ServerLevel level;
 static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
 static Item item(String id){var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));if(item==null||item==Items.AIR)throw new AssertionError("Missing item "+id);return item;}
 static FluidStack fluid(String name,int amount){var fluid=ForgeRegistries.FLUIDS.getValue(new ResourceLocation("tfc:metal/"+name));if(fluid==null)throw new AssertionError("Missing fluid "+name);return new FluidStack(fluid,amount);}
 record Pair(CastingBlockEntity table,CrucibleBlockEntity crucible,IFluidHandler metal){}
 static Pair pair(){
  var pos=new BlockPos(1700+cursor++*4,100,1700);level.getChunkAt(pos);level.getChunkAt(pos.east());level.setBlockAndUpdate(pos,Casting.TABLE.get().defaultBlockState());
  var crucible=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc:crucible"));level.setBlockAndUpdate(pos.east(),crucible.defaultBlockState());
  var table=(CastingBlockEntity)level.getBlockEntity(pos);var source=(CrucibleBlockEntity)level.getBlockEntity(pos.east());
  check(level.getBlockState(pos).getValue(CastingBlock.FACING)==Direction.EAST,"Neighbor crucible automatically connects the channel");
  var metal=source.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(()->new AssertionError("Native crucible fluid handler"));return new Pair(table,source,metal);
 }
 static void heat(Pair p){p.crucible.getCapability(HeatCapability.BLOCK_CAPABILITY).orElseThrow(()->new AssertionError("Native crucible heat")).setTemperatureIfWarmer(1600);}
 static void mold(Pair p){check(p.table.inventory().insertItem(0,new ItemStack(item("tfc:ceramic/ingot_mold")),false).isEmpty(),"Fired mold inserted");}
 static void run(Pair p,int steps){for(int i=0;i<steps;i++)p.table.step();}
 static ItemStack out(Pair p){return p.table.inventory().getStackInSlot(1);}
 static int amount(Pair p){return p.metal.getFluidInTank(0).getAmount();}
 static void fill(Pair p,String metal,int units){check(p.metal.fill(fluid(metal,units),IFluidHandler.FluidAction.EXECUTE)==units,"Native crucible receives exact metal units");}
 public CastingProbe(){MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event)->{
  level=event.getServer().overworld();
  try{
   var player=FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.fromString("4b0d12a3-ec77-43cc-acaa-e73591cff9de"),"CastingProbe"));
   var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);String[] materials={"create:brass_sheet","create:mechanical_press","create:brass_sheet","overgeared:steel_ingot","minecraft:hopper","overgeared:steel_ingot","tfc:fire_bricks","create:andesite_casing","tfc:fire_bricks"};
   for(int i=0;i<materials.length;i++)grid.setItem(i,new ItemStack(item(materials[i])));
   var recipe=event.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow(()->new AssertionError("Casting table recipe missing for the complete 3x3 grid"));check(recipe.assemble(grid,event.getServer().registryAccess()).is(Casting.ITEM.get()),"Actual expensive 3x3 crafting grid makes one casting table");
   for(String material:new String[]{"create:brass_sheet","create:mechanical_press","overgeared:steel_ingot","minecraft:hopper","tfc:fire_bricks","create:andesite_casing"})check(event.getServer().getRecipeManager().getRecipes().stream().anyMatch(r->r.getResultItem(event.getServer().registryAccess()).is(item(material))),"Craft ingredient has a current recipe: "+material);
   var p=pair();fill(p,"copper",700);heat(p);run(p,80);check(amount(p)==700&&out(p).isEmpty(),"No mold never spends metal");mold(p);run(p,5*CastingBlockEntity.CYCLE_TICKS);check(out(p).is(Items.COPPER_INGOT)&&out(p).getCount()==5&&amount(p)==200,"Five batches consume exactly 500 units and stack five canonical copper ingots");check(HeatCapability.getTemperature(out(p))==0&&out(p).getMaxStackSize()==64,"Cold output stacks to vanilla 64");
   check(MoldLike.get(p.table.inventory().getStackInSlot(0)).getFluidInTank(0).isEmpty(),"Mold is emptied and retained after every cast");
   p.table.inventory().setStackInSlot(1,new ItemStack(Items.COPPER_INGOT,64));run(p,100);check(amount(p)==200&&p.table.status()==CastingBlockEntity.FULL,"Full output prevents draining");
   p.table.inventory().setStackInSlot(1,new ItemStack(Items.GOLD_INGOT));run(p,80);check(amount(p)==200&&MoldLike.get(p.table.inventory().getStackInSlot(0)).getFluidInTank(0).isEmpty(),"Incompatible output prevents draining");
   var down=p.table.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN).orElseThrow(()->new AssertionError("Bottom port"));check(down.insertItem(0,new ItemStack(Items.GOLD_INGOT),false).getCount()==1,"Automation cannot insert into output");check(down.extractItem(0,1,false).is(Items.GOLD_INGOT),"Hopper-facing output is extractable");
   var up=p.table.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(()->new AssertionError("Top port"));check(up.extractItem(0,1,false).isEmpty(),"Automation cannot steal the retained mold");check(p.table.inventory().insertItem(0,new ItemStack(item("tfc:ceramic/hammer_head_mold")),true).getCount()==1,"Tool molds are rejected");
   var cold=pair();mold(cold);fill(cold,"copper",100);run(cold,100);check(out(cold).isEmpty()&&amount(cold)==100&&cold.table.status()==CastingBlockEntity.COLD,"Solid crucible metal cannot be poured");
   var shortMetal=pair();mold(shortMetal);fill(shortMetal,"copper",99);heat(shortMetal);run(shortMetal,100);check(amount(shortMetal)==99&&out(shortMetal).isEmpty(),"Insufficient batch never rounds up or partially drains");
   var bronze=pair();mold(bronze);fill(bronze,"copper",90);fill(bronze,"tin",10);heat(bronze);run(bronze,CastingBlockEntity.CYCLE_TICKS);check(out(bronze).is(item("createbigcannons:bronze_ingot"))&&amount(bronze)==0,"Native 90/10 crucible alloy produces canonical CBC bronze");
   for(var metal:Map.ofEntries(Map.entry("tin","antiquelegacy:tin_ingot"),Map.entry("silver","magistuarmory:silver_ingot"),Map.entry("gold","minecraft:gold_ingot"),Map.entry("cast_iron","minecraft:iron_ingot"),Map.entry("wrought_iron","minecraft:iron_ingot"),Map.entry("zinc","create:zinc_ingot"),Map.entry("brass","create:brass_ingot"),Map.entry("steel","overgeared:steel_ingot"),Map.entry("black_steel","tfc:metal/ingot/black_steel"),Map.entry("nickel","tfc:metal/ingot/nickel")) .entrySet()){
    var test=pair();mold(test);fill(test,metal.getKey(),200);heat(test);run(test,2*CastingBlockEntity.CYCLE_TICKS);check(out(test).is(item(metal.getValue()))&&out(test).getCount()==2&&amount(test)==0,"Canonical metal/rare alloy casts and stacks: "+metal.getKey());check(out(test).getMaxStackSize()==64,"64-stack material: "+metal.getKey());
   }
   var stacked=pair();var stackPort=stacked.table.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(()->new AssertionError("Mold stack port"));check(stackPort.insertItem(0,new ItemStack(item("tfc:ceramic/ingot_mold"),64),false).getCount()==63&&stacked.table.inventory().getStackInSlot(0).getCount()==1,"A stack of empty molds inserts exactly one mold without manual splitting");
   var fire=pair();check(fire.table.inventory().insertItem(0,new ItemStack(item("tfc:ceramic/fire_ingot_mold")),false).isEmpty(),"Fire-clay ingot mold is supported");fill(fire,"copper",100);heat(fire);run(fire,CastingBlockEntity.CYCLE_TICKS);check(out(fire).is(Items.COPPER_INGOT),"Fire-clay mold also casts a canonical ingot");
   var shared=pair();mold(shared);fill(shared,"copper",100);heat(shared);var otherPos=shared.crucible.getBlockPos().east();level.setBlockAndUpdate(otherPos,Casting.TABLE.get().defaultBlockState().setValue(CastingBlock.FACING,Direction.WEST));var other=(CastingBlockEntity)level.getBlockEntity(otherPos);other.inventory().insertItem(0,new ItemStack(item("tfc:ceramic/ingot_mold")),false);for(int i=0;i<CastingBlockEntity.CYCLE_TICKS;i++){shared.table.step();other.step();}check(out(shared).getCount()+other.inventory().getStackInSlot(1).getCount()==1&&amount(shared)==0,"Two tables sharing one crucible cannot duplicate its last 100 metal units");
   boolean automation=net.dries007.tfc.config.TFCConfig.SERVER.crucibleEnableAutomation.get();try{net.dries007.tfc.config.TFCConfig.SERVER.crucibleEnableAutomation.set(false);var direct=pair();mold(direct);fill(direct,"copper",100);heat(direct);run(direct,CastingBlockEntity.CYCLE_TICKS);check(out(direct).is(Items.COPPER_INGOT),"Direct casting does not require TFC pipe/automation ports");}finally{net.dries007.tfc.config.TFCConfig.SERVER.crucibleEnableAutomation.set(automation);}
   var partial=pair();mold(partial);MoldLike.get(partial.table.inventory().getStackInSlot(0)).fill(fluid("copper",50),IFluidHandler.FluidAction.EXECUTE);fill(partial,"copper",50);heat(partial);run(partial,CastingBlockEntity.CYCLE_TICKS);check(out(partial).is(Items.COPPER_INGOT)&&amount(partial)==0,"Partly filled mold uses only the missing 50 units");
   var different=pair();mold(different);MoldLike.get(different.table.inventory().getStackInSlot(0)).fill(fluid("gold",50),IFluidHandler.FluidAction.EXECUTE);fill(different,"copper",100);heat(different);run(different,80);check(amount(different)==100&&MoldLike.get(different.table.inventory().getStackInSlot(0)).getFluidInTank(0).getAmount()==50,"Different molten metals cannot mix in the mold");
   var saved=pair();mold(saved);fill(saved,"copper",100);heat(saved);run(saved,20);check(amount(saved)==0&&out(saved).isEmpty(),"Pending metal is stored in the real mold item");
   var nbt=saved.table.saveWithFullMetadata();var restored=(CastingBlockEntity)BlockEntity.loadStatic(saved.table.getBlockPos(),saved.table.getBlockState(),nbt);check(restored!=null&&restored.progress()==20,"Saved batch retains exact progress");saved.table.setRemoved();level.setBlockEntity(restored);var loaded=new Pair(restored,saved.crucible,saved.metal);run(loaded,20);check(out(loaded).getCount()==1&&amount(loaded)==0,"Reload resumes one batch without duplication");
   var removed=pair();mold(removed);fill(removed,"copper",100);heat(removed);run(removed,10);var filledMold=removed.table.inventory().extractItem(0,1,false);check(MoldLike.get(filledMold).getFluidInTank(0).getAmount()==100,"Removing the mold preserves all pending metal");run(removed,100);check(out(removed).isEmpty()&&amount(removed)==0,"Removed mold cannot also become an output ingot");
   var broken=pair();mold(broken);fill(broken,"silver",100);heat(broken);run(broken,10);var pos=broken.table.getBlockPos();
   check(MoldLike.get(broken.table.inventory().getStackInSlot(0)).getFluidInTank(0).getAmount()==100,"Silver batch is present before block destruction");
   // Generated chunks have no player tracking ticket: query visibility does not include their item entities.
   // Observe the real, uncancelled Forge spawn event instead of querying the client-visible entity index.
   var spawned=new ArrayList<ItemStack>();
   java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> dropListener=e->{if(e.getLevel()==level&&e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item&&item.blockPosition().distSqr(pos)<9)spawned.add(item.getItem().copy());};
   MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,net.minecraftforge.event.entity.EntityJoinLevelEvent.class,dropListener);
   try{level.destroyBlock(pos,false);}finally{MinecraftForge.EVENT_BUS.unregister(dropListener);}
   check(level.getBlockState(pos).isAir(),"Breaking the table does not recreate its block");
   System.out.println("CASTING_NATIVE_DROPS count="+spawned.size()+" mold_units="+spawned.stream().mapToInt(stack->CastingBlockEntity.isMold(stack)?MoldLike.get(stack).getFluidInTank(0).getAmount():0).sum());
   check(spawned.stream().anyMatch(stack->CastingBlockEntity.isMold(stack)&&MoldLike.get(stack).getFluidInTank(0).getAmount()==100),"Broken table drops a mold containing the exact pending silver");
   MetallurgyProbe.check(event.getServer(),level);
   ThermalProbe.check(event.getServer(),level);
   FoodProbe.check(event.getServer());
   CrucibleProbe.check(event.getServer());
   System.out.println("CASTING_NATIVE_PASS checks="+checks+" cycle_ticks="+CastingBlockEntity.CYCLE_TICKS+" canonical_metals=12 alloy=90copper_10tin save_reload=passed break=passed automation=passed");
  }catch(Throwable error){System.out.println("CASTING_NATIVE_FAIL");error.printStackTrace();}
  event.getServer().halt(false);
 });}
}
