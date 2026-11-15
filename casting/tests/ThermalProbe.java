package ua.empirecraft.casting.test;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.level.GameType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.stirdrem.overgeared.recipe.ForgingRecipe;
import net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity;
import ua.empirecraft.casting.*;

public final class ThermalProbe {
 static int checks;
 static final class DamagePlayer extends net.minecraftforge.common.util.FakePlayer {
  DamagePlayer(ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.fromString("efffe471-c9d0-4f09-aaef-653630d7c388"),"ThermalProbe"));}
  // Forge FakePlayer is unconditionally invulnerable; use the ordinary damage path here.
  @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
 }

 static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 static ItemStack item(String id){return new ItemStack(CastingProbe.item(id));}
 static Recipe<?> recipe(MinecraftServer server,String id){return server.getRecipeManager().byKey(new ResourceLocation(id)).orElseThrow(()->new AssertionError("Missing "+id));}
 public static void check(MinecraftServer server,ServerLevel level)throws Exception{
  for(String metal:List.of("iron","copper","steel","silver")){
   var old=item("overgeared:heated_"+metal+"_ingot");old.setCount(7);old.getOrCreateTag().putString("ForgingQuality","master");old.setHoverName(net.minecraft.network.chat.Component.literal("Named legacy metal"));var normal=Thermal.normalize(old);
   check(!Thermal.legacy(Thermal.id(normal))&&normal.getCount()==7,"Legacy ID migrates without changing metal count: "+metal);
   check(HeatCapability.getTemperature(normal)>=80&&normal.getTag().getString("ForgingQuality").equals("master")&&normal.hasCustomHoverName(),"Migration preserves heat, quality and name: "+metal);
  }
  var plate=item("create:iron_sheet");check(HeatCapability.get(plate)!=null,"Create sheets have native heat capability");HeatCapability.setTemperature(plate,1100);
  var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());buffer.writeItem(plate);var received=buffer.readItem();check(received.is(plate.getItem())&&HeatCapability.getTemperature(received)>900,"Native item network codec preserves actual heat");buffer.release();
  var saved=ItemStack.of(plate.save(new net.minecraft.nbt.CompoundTag()));check(HeatCapability.getTemperature(saved)>900,"Heat survives disk serialization");
  int heatedIngredients=0;
  for(var r:server.getRecipeManager().getRecipes()){
   check(!Thermal.legacy(Thermal.id(r.getResultItem(server.registryAccess()))),"No recipe creates a retired hot item: "+r.getId());
   if(!(r instanceof ForgingRecipe forging))continue;
   for(var ing:forging.getForgingIngredients()){
    if(ing.ingredient().isEmpty()||ing.ingredient().getItems().length==0)continue;var sample=ing.ingredient().getItems()[0].copy();if(Thermal.material(Thermal.id(sample))==null)continue;
    HeatCapability.setTemperature(sample,0);check(!ing.test(sample),"Cold forging metal rejected: "+r.getId());HeatCapability.setTemperature(sample,1400);check(ing.test(sample),"Warm ordinary item accepted: "+r.getId());heatedIngredients++;
   }
  }
  check(heatedIngredients>100,"Temperature checks cover existing Overgeared and modpack recipes");
  var furnacePos=new BlockPos(2100,100,2100);level.getChunkAt(furnacePos);level.setBlockAndUpdate(furnacePos,Blocks.FURNACE.defaultBlockState());var furnace=(AbstractFurnaceBlockEntity)level.getBlockEntity(furnacePos);furnace.setItem(0,new ItemStack(Items.COPPER_INGOT,2));furnace.setItem(1,new ItemStack(Items.CHARCOAL,4));
  for(int ticks=0;ticks<1000&&furnace.getItem(2).getCount()<2;ticks++)AbstractFurnaceBlockEntity.serverTick(level,furnacePos,level.getBlockState(furnacePos),furnace);
  check(furnace.getItem(2).is(Items.COPPER_INGOT)&&furnace.getItem(2).getCount()==2,"Actual furnace produces and stacks ordinary ingots");check(HeatCapability.getTemperature(furnace.getItem(2))>600,"Actual furnace output carries native TFC temperature");
  var p=new DamagePlayer(level);p.spawnInvulnerableTime=0;p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setHealth(20);p.invulnerableTime=0;p.tickCount=20;
  var iron=item("minecraft:iron_ingot");HeatCapability.setTemperature(iron,1100);p.getInventory().setItem(5,iron);Thermal.player(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));check(p.getHealth()<20,"Hot metal anywhere in inventory causes a real burn");
  p.setHealth(20);p.invulnerableTime=0;var tongs=item("overgeared:iron_tongs");p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,tongs);p.tickCount=40;Thermal.player(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));check(p.getHealth()==20&&tongs.getDamageValue()>0,"Held tongs prevent burns and wear once per interval");
  p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);Thermal.quench(iron);p.tickCount=60;p.invulnerableTime=0;Thermal.player(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));check(p.getHealth()==20,"Cold metal causes no burn");
  p.getInventory().clearContent();var cursor=item("minecraft:copper_ingot");HeatCapability.setTemperature(cursor,500);p.containerMenu.setCarried(cursor);p.tickCount=80;Thermal.player(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));check(p.getHealth()<20,"A hot cursor stack cannot bypass burns");p.containerMenu.setCarried(ItemStack.EMPTY);
  var waterPos=furnacePos.offset(5,0,0);level.setBlockAndUpdate(waterPos.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(waterPos,Blocks.WATER.defaultBlockState());var hot=item("minecraft:iron_ingot");hot.setCount(3);hot.getOrCreateTag().putString("ForgingQuality","master");hot.setHoverName(net.minecraft.network.chat.Component.literal("Keep my name"));HeatCapability.setTemperature(hot,1200);var drop=new ItemEntity(level,waterPos.getX()+.5,waterPos.getY()+.2,waterPos.getZ()+.5,hot);drop.setNoGravity(true);for(int i=0;i<3;i++)drop.tick();
  check(HeatCapability.getTemperature(drop.getItem())==0&&drop.getItem().getCount()==3&&drop.getItem().hasCustomHoverName()&&drop.getItem().getTag().getString("ForgingQuality").equals("master"),"Actual water entry instantly quenches without losing item data");
  var dry=new ItemEntity(level,waterPos.getX()+3,waterPos.getY()+2,waterPos.getZ(),item("minecraft:iron_ingot"));HeatCapability.setTemperature(dry.getItem(),1000);dry.tick();check(HeatCapability.getTemperature(dry.getItem())>0,"Dry ground does not instantly quench metal");
  var anvilPos=furnacePos.offset(12,0,0);var anvilBlock=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation("overgeared:stone_anvil"));check(anvilBlock!=null,"Native Overgeared anvil exists");level.setBlockAndUpdate(anvilPos,anvilBlock.defaultBlockState());var anvil=(AbstractSmithingAnvilBlockEntity)level.getBlockEntity(anvilPos);var inventory=anvil.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new AssertionError("Anvil item capability"));
  for(int slot:new int[]{0,1,3}){var input=item(slot==3?"create:iron_sheet":"minecraft:iron_ingot");HeatCapability.setTemperature(input,1100);check(inventory.insertItem(slot,input,false).isEmpty(),"Anvil accepts heated normal components");}
  var craft=AbstractSmithingAnvilBlockEntity.class.getDeclaredMethod("craftItem");craft.setAccessible(true);craft.invoke(anvil);check(Thermal.id(inventory.getStackInSlot(10)).equals("empirecraft_casting:arquebus_barrel_blank")&&HeatCapability.getTemperature(inventory.getStackInSlot(10))>900,"Native anvil produces a hot canonical forged component");
  var arquebus=(ForgingRecipe)recipe(server,"empirecraft_casting:arquebus/assembly");check(arquebus.hasQuality()&&arquebus.needsMinigame(),"Arquebus retains forging quality gameplay");
  var ammo=recipe(server,"empirecraft_casting:arquebus/ammunition");check(ammo.getResultItem(server.registryAccess()).getCount()==4,"Small-caliber ammunition batch yields four charges");var grid=new TransientCraftingContainer(p.inventoryMenu,3,3);grid.setItem(0,item("tfc_muskets:bullet"));grid.setItem(1,item("tfc_muskets:bullet"));grid.setItem(2,item("tfc_muskets:gunpowder_pinch"));grid.setItem(3,item("minecraft:paper"));var chosen=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level);check(chosen.isPresent()&&chosen.get().getId().equals(ammo.getId()),"Ammo recipe has no conflict with musket cartridges");
  var weapon=(ProjectileWeaponItem)CastingProbe.item("medieval_boomsticks:arquebus");check(weapon.getAllSupportedProjectiles().test(item("medieval_boomsticks:round_ball")),"The actual arquebus accepts the crafted ammunition");
  var part=(ForgingRecipe)recipe(server,"empirecraft_casting:arquebus/barrel_blank");var bytes=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());ForgingRecipe.Serializer.INSTANCE.toNetwork(bytes,part);var remote=ForgingRecipe.Serializer.INSTANCE.fromNetwork(part.getId(),bytes);for(var ingredient:remote.getForgingIngredients())if(!ingredient.ingredient().isEmpty()){var input=ingredient.ingredient().getItems()[0].copy();HeatCapability.setTemperature(input,0);check(!ingredient.test(input),"Cold rejection survives recipe synchronization");HeatCapability.setTemperature(input,1100);check(ingredient.test(input),"Hot normal components work after recipe synchronization");}bytes.release();
  System.out.println("CASTING_NATIVE_THERMAL_PASS checks="+checks+" heated_ingredients="+heatedIngredients+" furnace=passed tongs=passed burns=passed water=passed migration=passed network=passed arquebus=passed");
 }
}
