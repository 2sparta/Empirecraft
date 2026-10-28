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

@Mod("empirecraft_native_test")
public class Smoke {
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 public Smoke(){MinecraftForge.EVENT_BUS.addListener(this::started);}
 void started(ServerStartedEvent e){
  try {
   JsonObject policy;
   try(var in=Smoke.class.getResourceAsStream("/native-policy.json")){policy=JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();}
   int count=0;
   for(var value:policy.getAsJsonArray("removed")){
    var id=new ResourceLocation(value.getAsString());
    check(!ForgeRegistries.ITEMS.containsKey(id),"Native item still registered: "+id);
    check(!ForgeRegistries.BLOCKS.containsKey(id),"Native block still registered: "+id);
    check(!ForgeRegistries.FLUIDS.containsKey(id),"Native fluid still registered: "+id);count++;
   }
   for(String id:List.of("create:shaft","create:water_wheel","createbigcannons:bronze_ingot","createbigcannons:bronze_scrap","antiquelegacy:bronze_mixture","antiquelegacy:bronze_plate","overgeared:steel_ingot","overgeared:steel_nugget","farmersdelight:iron_knife","contact:postcard","naturalist:crimson_froglass"))
    check(ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id)),"Retained content missing: "+id);
   var server=e.getServer();var level=server.overworld();
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
   System.out.println("NATIVE_CONTENT_SMOKE_PASS excluded="+count);
  }catch(Throwable t){System.err.println("NATIVE_CONTENT_SMOKE_FAIL");t.printStackTrace();}
  e.getServer().halt(false);
 }
}
