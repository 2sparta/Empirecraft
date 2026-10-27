package net.stirdrem.overgeared.empirecraft.test;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("empirecraft_overgeared_trim_test")
public final class Smoke {
    static final boolean BASELINE=Boolean.getBoolean("trim.baseline");
    static final List<String> REMOVED=List.of("nether_alloy_furnace","casting_furnace","diamond_upgrade_smithing_template",
        "netherite_alloy","heated_netherite_alloy","diamond_arrow","diamond_shard","nether_tool_cast");
    static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    static ResourceLocation id(String value){return new ResourceLocation(value);}
    public Smoke(){MinecraftForge.EVENT_BUS.addListener(this::started);}
    void started(ServerStartedEvent event) {
        var server=event.getServer();
        try {
            for(String value:REMOVED)require(ForgeRegistries.ITEMS.getKeys().contains(id("overgeared:"+value))==BASELINE,"Item registration "+value);
            for(String value:List.of("nether_alloy_furnace","casting_furnace"))require(ForgeRegistries.BLOCKS.getKeys().contains(id("overgeared:"+value))==BASELINE,"Block registration "+value);
            for(String value:List.of("nether_alloy_furnace_be","casting_furnace_be"))require(ForgeRegistries.BLOCK_ENTITY_TYPES.getKeys().contains(id("overgeared:"+value))==BASELINE,"Block entity registration "+value);
            for(String value:List.of("nether_alloy_smelter_menu","casting_furnace"))require(ForgeRegistries.MENU_TYPES.getKeys().contains(id("overgeared:"+value))==BASELINE,"Menu registration "+value);
            for(String value:List.of("alloy_furnace","clay_tool_cast","steel_ingot","iron_arrow","steel_arrow","steel_pickaxe"))require(ForgeRegistries.ITEMS.getKeys().contains(id("overgeared:"+value)),"Retained item "+value);
            var manager=server.getRecipeManager();
            require(manager.byKey(id("empirecraft_overgeared_trim_test:forbidden_external")).isPresent()==BASELINE,"External deleted-item recipe filter");
            require(manager.byKey(id("empirecraft_overgeared_trim_test:retained_external")).isPresent(),"Retained external recipe");
            require(manager.byKey(id("overgeared:iron_pickaxe_head_from_cast_smelting")).isPresent(),"Ordinary clay-cast smelting survives");
            if(!BASELINE) {
                for(var recipe:manager.getRecipes()) {
                    var serializer=ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer());
                    require(!List.of("overgeared:casting","overgeared:nether_alloy_smelting","overgeared:shaped_nether_alloy_smelting").contains(serializer.toString()),"Removed machine recipe "+recipe.getId());
                }
                var casting=manager.byKey(id("overgeared:iron_pickaxe_head_from_cast_smelting")).orElseThrow();
                var ingredients=casting.getIngredients();
                require(ingredients.size()==1&&ingredients.get(0).getItems().length==1,"Clay cast remains the sole legal mold");
                require(ForgeRegistries.ITEMS.getKey(ingredients.get(0).getItems()[0].getItem()).toString().equals("overgeared:clay_tool_cast"),"Retained clay mold ingredient");
            }
            CreativeModeTabs.tryRebuildTabContents(server.overworld().enabledFeatures(),true,server.registryAccess());
            System.out.println("OVERGEARED_SMOKE_PASS baseline="+BASELINE+"; registries, retained equipment, external recipes, clay casting and creative tabs");
        } catch(Throwable error) {System.err.println("OVERGEARED_SMOKE_FAIL");error.printStackTrace();}
        server.halt(false);
    }
}
