package ua.empirecraft.ceramics.test;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.foundation.block.IBE;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blockentities.*;
import net.dries007.tfc.common.blocks.devices.PitKilnBlock;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.network.PlaceBlockSpecialPacket;
import net.dries007.tfc.util.calendar.Calendars;

/** Native interactions in a disposable generated world; never a copy of player world data. */
@Mod("empirecraft_ceramics_probe")
public final class CeramicsProbe {
 static final String ID="empirecraft_ceramics_probe";
 static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
 static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
 static final RegistryObject<Block> MOTOR=BLOCKS.register("test_source",Motor::new);
 static final RegistryObject<BlockEntityType<MotorBE>> MOTOR_BE=BES.register("test_source",()->BlockEntityType.Builder.of(MotorBE::new,MOTOR.get()).build(null));
 static float rpm=0;
 static int checks;
 net.minecraft.server.MinecraftServer running;int ticks,beforeStop;
 final BlockPos mill=new BlockPos(32,100,32);
 net.minecraftforge.items.IItemHandler quernInventory;
 static net.minecraft.world.level.block.Block block(String id){var value=ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));if(value==null||value==Blocks.AIR)throw new AssertionError("Unknown block "+id);return value;}
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static Item item(String id){var value=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));if(value==null||value==Items.AIR)throw new AssertionError("Unknown item "+id);return value;}
 static net.minecraftforge.items.IItemHandler inventory(net.minecraft.world.level.block.entity.BlockEntity be)throws Exception {
  var field=InventoryBlockEntity.class.getDeclaredField("inventory");field.setAccessible(true);return (net.minecraftforge.items.IItemHandler)field.get(be);
 }
 public CeramicsProbe(){var bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);BES.register(bus);MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event)->{
  var server=event.getServer();var level=server.overworld();
  try{
   var player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("6affd411-5b92-4181-9871-020c1601f6b3"),"CeramicsProbe"));
   for(String sand:List.of("minecraft:sand","minecraft:red_sand","tfc:sand/white","tfc:sand/yellow","tfc:sand/black","tfc:sand/green","tfc:sand/brown","tfc:sand/red","tfc:sand/pink")){
    var grid=new TransientCraftingContainer(player.inventoryMenu,2,2);grid.setItem(0,new ItemStack(Items.PAPER));grid.setItem(3,new ItemStack(item(sand)));
    var selected=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level);
    System.out.println("CERAMICS_SANDPAPER sand="+sand+" recipe="+selected.map(r->r.getId().toString()).orElse("MISSING")+" result="+selected.map(r->ForgeRegistries.ITEMS.getKey(r.assemble(grid,server.registryAccess()).getItem()).toString()).orElse("NONE"));
   }
   if(Boolean.getBoolean("empirecraft.ceramics.fix"))for(String sand:List.of("minecraft:sand","minecraft:red_sand","tfc:sand/white","tfc:sand/yellow","tfc:sand/black","tfc:sand/green","tfc:sand/brown","tfc:sand/red","tfc:sand/pink"))for(int size:new int[]{2,3}){
    var grid=new TransientCraftingContainer(player.inventoryMenu,size,size);grid.setItem(size*size-1,new ItemStack(Items.PAPER));grid.setItem(0,new ItemStack(item(sand)));
    var selected=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow();String expected=sand.equals("minecraft:red_sand")||sand.equals("tfc:sand/red")||sand.equals("tfc:sand/pink")?"create:red_sand_paper":"create:sand_paper";
    var out=selected.assemble(grid,server.registryAccess());check(out.is(item(expected))&&out.getCount()==1,"matching native sandpaper result "+size+" "+sand);check(!selected.isSpecial()&&selected.getIngredients().stream().allMatch(i->i.getItems().length>0),"ordinary visible recipe with actual ingredients "+sand);
    check(selected.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty),"one paper and one sand consumed");
   }
   for(String recipe:List.of("create:crafting/materials/sand_paper","create:crafting/materials/red_sand_paper"))System.out.println("CERAMICS_RECIPE "+recipe+" present="+server.getRecipeManager().byKey(new ResourceLocation(recipe)).isPresent());
   check(TFCConfig.SERVER.enablePlacingItems.get(),"V placement enabled in shipped configuration");check(!TFCConfig.SERVER.usePlacedItemWhitelist.get(),"placement whitelist disabled");
   for(String clay:List.of("tfc:ceramic/unfired_ingot_mold","tfc:ceramic/unfired_vessel")){
    int px=clay.endsWith("unfired_vessel")?14:10;var pos=new BlockPos(px,100,10);level.getChunkAt(pos);
    for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=4;y++)level.setBlockAndUpdate(pos.offset(x,y,z),y<0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
    for(var d:Direction.Plane.HORIZONTAL)level.setBlockAndUpdate(pos.relative(d),Blocks.DIRT.defaultBlockState());
    player.setPos(px+0.25,102,10.25);player.setXRot(90);player.setYRot(0);player.setShiftKeyDown(false);player.getAbilities().mayBuild=true;
    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item(clay)));
    var hit=new BlockHitResult(new Vec3(px+0.25,100,10.25),Direction.UP,pos.below(),false);
    player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
    check(level.isEmptyBlock(pos),"ordinary right click does not place raw pottery "+clay);
    var method=PlaceBlockSpecialPacket.class.getDeclaredMethod("handle",net.minecraft.server.level.ServerPlayer.class);method.setAccessible(true);method.invoke(new PlaceBlockSpecialPacket(),player);
    var picked=player.pick(5,1,false);String pickedInfo=picked instanceof BlockHitResult b?b.getBlockPos()+"/"+b.getDirection():picked.toString();
    check(level.getBlockState(pos).is(TFCBlocks.PLACED_ITEM.get()),"native V packet places raw pottery "+clay+" picked="+pickedInfo+" placed="+level.getBlockState(pos)+" hand="+player.getMainHandItem()+" mayBuild="+player.mayBuild());
    check(player.getMainHandItem().isEmpty(),"V consumes exactly one pottery item");
    check(inventory(level.getBlockEntity(pos)).getStackInSlot(3).is(item(clay)),"placed block retains raw pottery");
    var onItems=new BlockHitResult(new Vec3(px+0.25,100.05,10.25),Direction.UP,pos,false);
    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item("tfc:straw"),8));
    player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,onItems);
    check(level.getBlockState(pos).is(TFCBlocks.PIT_KILN.get()),"right click straw converts placed pottery to pit kiln");
    for(int i=0;i<7;i++)player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,onItems);
    check(player.getMainHandItem().isEmpty(),"eight straw used");
    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.OAK_LOG,8));
    for(int i=0;i<8;i++)player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,onItems);
    var kiln=(PitKilnBlockEntity)level.getBlockEntity(pos);
    check(kiln.hasFuel(),"eight straw and eight vanilla logs fill fuel");check(player.getMainHandItem().isEmpty(),"eight logs used");
    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.FLINT_AND_STEEL));
    player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,onItems);
    if(!kiln.isLit()){
     player.setShiftKeyDown(true);player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,onItems);player.setShiftKeyDown(false);
     System.out.println("CERAMICS_PIT_IGNITION uses=shift_right_click");
    }
    check(kiln.isLit(),"vanilla flint and steel lights complete kiln with native interaction");
    var litTick=PitKilnBlockEntity.class.getDeclaredField("litTick");litTick.setAccessible(true);litTick.setLong(kiln,Calendars.SERVER.getTicks()-TFCConfig.SERVER.pitKilnTicks.get());
    PitKilnBlockEntity.serverTick(level,pos,level.getBlockState(pos),kiln);
    var result=inventory(level.getBlockEntity(pos)).getStackInSlot(3);
    check(result.is(item(clay.replace("unfired_",""))),"native kiln completes firing "+clay+" -> "+result);
    System.out.println("CERAMICS_PIT_FIRING_PASS input="+clay+" output="+ForgeRegistries.ITEMS.getKey(result.getItem()));
    level.removeBlock(pos,false);
   }
   var driveRecipe=server.getRecipeManager().byKey(new ResourceLocation("empirecraft_tfc:quern_drive")).orElseThrow();check(driveRecipe.getIngredients().stream().filter(i->i!=Ingredient.EMPTY).allMatch(i->i.getItems().length>0),"quern drive recipe has resolved non-blank ingredients");
   var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);grid.setItem(1,new ItemStack(item("create:iron_sheet")));grid.setItem(3,new ItemStack(item("create:cogwheel")));grid.setItem(4,new ItemStack(Items.OAK_PLANKS));grid.setItem(5,new ItemStack(item("create:cogwheel")));grid.setItem(7,new ItemStack(item("create:shaft")));
   var selected=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow();check(selected.assemble(grid,server.registryAccess()).is(item("empirecraft_tfc:quern_drive")),"actual crafting grid selects quern drive");
   level.setChunkForced(mill.getX()>>4,mill.getZ()>>4,true);level.setBlockAndUpdate(mill,block("tfc:quern").defaultBlockState());level.setBlockAndUpdate(mill.above(),block("create:shaft").defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.Y));
   var quern=(QuernBlockEntity)level.getBlockEntity(mill);quernInventory=inventory(quern);
   check(!quern.isConnectedToNetwork(),"shaft above alone does not connect a native TFC quern");
   level.removeBlock(mill.above(),false);level.setBlockAndUpdate(mill.below(),block("empirecraft_tfc:quern_drive").defaultBlockState());level.setBlockAndUpdate(mill.below(2),block("create:shaft").defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.Y));
   // Production Creative Motor is deliberately disabled; this test-only generator uses Create propagation.
   level.setBlockAndUpdate(mill.below(3),MOTOR.get().defaultBlockState());
   check(!quern.isConnectedToNetwork(),"drive still requires a handstone");
   quernInventory.insertItem(0,new ItemStack(item("tfc:handstone")),false);quernInventory.insertItem(1,new ItemStack(item("tfc:food/wheat_grain"),8),false);running=server;
  }catch(Throwable e){System.out.println("CERAMICS_NATIVE_FAIL");e.printStackTrace();}
  if(running==null)server.halt(false);
 });MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ServerTickEvent e)->{
  if(running==null||e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
  try{
   ticks++;var level=running.overworld();var motor=(MotorBE)level.getBlockEntity(mill.below(3));
   if(ticks==10){rpm=32;motor.updateGeneratedRotation();}
   if(ticks==60){var drive=(ua.empirecraft.tfc.QuernDriveBlockEntity)level.getBlockEntity(mill.below());check(drive.getSpeed()!=0,"actual Create network powers quern drive");check(((QuernBlockEntity)level.getBlockEntity(mill)).isConnectedToNetwork(),"powered quern has handstone and adapter");}
   if(ticks==180){check(!quernInventory.getStackInSlot(2).isEmpty(),"native powered quern outputs flour");check(quernInventory.getStackInSlot(0).getDamageValue()>0,"grinding consumes handstone durability");rpm=0;motor.updateGeneratedRotation();beforeStop=quernInventory.getStackInSlot(2).getCount();}
   if(ticks==300){check(quernInventory.getStackInSlot(2).getCount()==beforeStop,"quern stops when Create shaft stops");System.out.println("CERAMICS_QUERN_PASS recipe=crafted source=Create handstone=worn stop=verified");System.out.println("CERAMICS_NATIVE_PASS checks="+checks);running.halt(false);running=null;}
  }catch(Throwable error){System.out.println("CERAMICS_NATIVE_FAIL");error.printStackTrace();running.halt(false);running=null;}
 });}
 public static final class Motor extends KineticBlock implements IBE<MotorBE>{
  Motor(){super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK));}
  @Override public Direction.Axis getRotationAxis(BlockState state){return Direction.Axis.Y;}
  @Override public boolean hasShaftTowards(net.minecraft.world.level.LevelReader l,BlockPos p,BlockState s,Direction face){return face==Direction.UP;}
  @Override public Class<MotorBE> getBlockEntityClass(){return MotorBE.class;}
  @Override public BlockEntityType<? extends MotorBE> getBlockEntityType(){return MOTOR_BE.get();}
 }
 public static final class MotorBE extends GeneratingKineticBlockEntity{
  MotorBE(BlockPos p,BlockState s){super(MOTOR_BE.get(),p,s);}
  @Override public float getGeneratedSpeed(){return rpm;}
  @Override public float calculateAddedStressCapacity(){lastCapacityProvided=1024;return 1024;}
 }
}
