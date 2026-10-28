package ua.empirecraft.nativevalidation;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.Blocks;

@Mod("empirecraft_native_test")
public class Smoke {
 static final boolean BASELINE=Boolean.getBoolean("native.baseline");
 static boolean success;
 static final net.minecraft.core.BlockPos POS=new net.minecraft.core.BlockPos(4,100,4);
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 public Smoke(){
  MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,this::started);
  MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,this::stopped);
 }
 void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){
  System.out.println("NATIVE_CONTENT_WORLD_SAVED baseline="+BASELINE);
  try{java.nio.file.Files.writeString(java.nio.file.Path.of("native-finished-"+BASELINE),success?"PASS":"FAIL");}
  catch(java.io.IOException error){throw new RuntimeException(error);}
 }
 void started(ServerStartedEvent e){
  try {
   var server=e.getServer();var level=server.overworld();level.getChunkAt(POS);
   if(BASELINE){
    level.setBlockAndUpdate(POS,Blocks.CHEST.defaultBlockState());
    var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(POS);
    String[] ids={"magistuarmory:steel_ingot","magistuarmory:steel_nugget","antiquelegacy:bronze_ingot","antiquelegacy:bronze_nugget"};
    for(int slot=0;slot<ids.length;slot++){
     var id=new ResourceLocation(ids[slot]);check(ForgeRegistries.ITEMS.containsKey(id),"Baseline native metal absent: "+id);
     var stack=new ItemStack(ForgeRegistries.ITEMS.getValue(id),slot+3);stack.getOrCreateTag().putString("NativeMigration","slot"+slot);chest.setItem(slot,stack);
    }
    level.setBlockAndUpdate(POS.east(),ForgeRegistries.BLOCKS.getValue(new ResourceLocation("create:framed_glass_trapdoor")).defaultBlockState());
    success=true;System.out.println("NATIVE_CONTENT_SMOKE_PASS baseline=true");e.getServer().halt(false);return;
   }
   JsonObject policy;
   try(var in=Smoke.class.getResourceAsStream("/native-policy.json")){policy=JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();}
   int count=0;var violations=new TreeSet<String>();
   for(var value:policy.getAsJsonArray("removed")){
    var id=new ResourceLocation(value.getAsString());
    // Vanilla registries stay intact; the existing guard owns vanilla End restrictions.
    if(id.getNamespace().equals("minecraft"))continue;
    if(ForgeRegistries.ITEMS.getKeys().contains(id)||ForgeRegistries.BLOCKS.getKeys().contains(id)||ForgeRegistries.FLUIDS.getKeys().contains(id)||ForgeRegistries.ENTITY_TYPES.getKeys().contains(id))violations.add(id.toString());
    count++;
   }
   var forbidden=java.util.regex.Pattern.compile(policy.get("forbidden_pattern").getAsString());
   var exceptions=new HashSet<String>();for(var value:policy.getAsJsonArray("preserved_false_positives"))exceptions.add(value.getAsString());
   for(var keys:List.of(ForgeRegistries.ITEMS.getKeys(),ForgeRegistries.BLOCKS.getKeys(),ForgeRegistries.FLUIDS.getKeys(),ForgeRegistries.ENTITY_TYPES.getKeys()))for(var id:keys)
    if(!id.getNamespace().equals("minecraft")&&!exceptions.contains(id.toString())&&forbidden.matcher(id.getPath()).find())violations.add(id.toString());
   check(violations.isEmpty(),"Native exclusions still registered: "+violations);
   for(String id:List.of("create:shaft","create:water_wheel","createbigcannons:bronze_ingot","createbigcannons:bronze_scrap","antiquelegacy:bronze_mixture","antiquelegacy:bronze_plate","overgeared:steel_ingot","overgeared:steel_nugget","farmersdelight:iron_knife","contact:postcard","naturalist:crimson_froglass"))
    check(ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id)),"Retained content missing: "+id);
   var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(POS);
   String[] migrated={"overgeared:steel_ingot","overgeared:steel_nugget","createbigcannons:bronze_ingot","createbigcannons:bronze_scrap"};
   for(int slot=0;slot<migrated.length;slot++){
    var stack=chest.getItem(slot);check(stack.is(ForgeRegistries.ITEMS.getValue(new ResourceLocation(migrated[slot]))),"Saved metal migration "+slot);
    check(stack.getCount()==slot+3&&stack.hasTag()&&stack.getTag().getString("NativeMigration").equals("slot"+slot),"Saved metal count/NBT "+slot);
   }
   check(level.getBlockState(POS.east()).is(Blocks.STONE),"Saved Create trapdoor did not migrate to stone");
   check(server.getRecipeManager().byKey(new ResourceLocation("antiquelegacy:furnace/bronze_ingot_smelting")).isPresent(),"Epic bronze furnace recipe unavailable");
   check(server.getRecipeManager().byKey(new ResourceLocation("createbigcannons:medieval/bronze")).isEmpty(),"Competing bronze alloy recipe present");
   CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(),true,server.registryAccess());
   for(var tab:CreativeModeTabs.allTabs())for(var stack:tab.getDisplayItems()){
    var raw=(Item)stack.getClass().getMethod("empirecraft$rawItem").invoke(stack);
    String id=ForgeRegistries.ITEMS.getKey(raw).toString();
    for(var value:policy.getAsJsonArray("removed"))check(!value.getAsString().equals(id),"Creative removed identity "+id);
   }
   var entity=net.minecraft.world.entity.EntityType.SHEEP.create(level);
   for(var dimension:List.of(Level.END,Level.NETHER)){
    var event=new EntityTravelToDimensionEvent(entity,dimension);MinecraftForge.EVENT_BUS.post(event);check(event.isCanceled(),"Dimension entry allowed: "+dimension);
   }
   var exit=new EntityTravelToDimensionEvent(entity,Level.OVERWORLD);MinecraftForge.EVENT_BUS.post(exit);check(!exit.isCanceled(),"Overworld exit denied");
   success=true;System.out.println("NATIVE_CONTENT_SMOKE_PASS baseline=false excluded="+count);
  }catch(Throwable t){System.err.println("NATIVE_CONTENT_SMOKE_FAIL");t.printStackTrace();}
  e.getServer().halt(false);
 }
}
