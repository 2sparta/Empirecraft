package ua.empirecraft.endtest;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("empirecraft_end_test")
public final class EndSmoke {
    static final boolean BASELINE=Boolean.getBoolean("noend.baseline");
    static final BlockPos POS=new BlockPos(4,100,4);
    static final JsonObject RETIRED;
    static final JsonArray LEGACY;
    static {try(var stream=EndSmoke.class.getResourceAsStream("/native.json")){RETIRED=JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static {try(var stream=EndSmoke.class.getResourceAsStream("/legacy.json")){LEGACY=JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonArray();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static ResourceLocation id(String s){return new ResourceLocation(s);}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ItemStack item(String s){return new ItemStack(ForgeRegistries.ITEMS.getValue(id(s)));}
    public EndSmoke(){MinecraftForge.EVENT_BUS.addListener(this::started);}
    void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();
        try{
            int count=0;
            for(var value:LEGACY){check(!ForgeRegistries.ITEMS.getKeys().contains(id(value.getAsString())),"Previous Nether removal item "+value);check(!ForgeRegistries.BLOCKS.getKeys().contains(id(value.getAsString())),"Previous Nether removal block "+value);}
            for(var list:RETIRED.entrySet())for(var value:list.getValue().getAsJsonArray()){
                check(ForgeRegistries.ITEMS.getKeys().contains(id(value.getAsString()))==BASELINE,"Native item registry: "+value);count++;
                if(value.getAsString().startsWith("chipped:"))check(ForgeRegistries.BLOCKS.getKeys().contains(id(value.getAsString()))==BASELINE,"Native block registry: "+value);
            }
            for(String value:List.of("chipped:angry_stone","chipped:nether_engraved_glowstone","contact:postcard","contact:parcel","contact:wrapping_paper","farmersdelight:iron_knife","farmersdelight:flint_knife","farmersdelight:golden_knife"))check(ForgeRegistries.ITEMS.getKeys().contains(id(value)),"Preserved item: "+value);
            level.getChunkAt(POS);
            if(BASELINE){
                level.setBlockAndUpdate(POS,Blocks.CHEST.defaultBlockState());
                var chest=(ChestBlockEntity)level.getBlockEntity(POS);
                for(int slot=0;slot<3;slot++){
                    var stack=item(List.of("farmersdelight:diamond_knife","farmersdelight:netherite_knife","contact:ender_postcard").get(slot));
                    stack.getOrCreateTag().putString("NoEndPreserved","slot"+slot);chest.setItem(slot,stack);
                }
                chest.setItem(3,new ItemStack(Items.ENDER_PEARL,3));
                level.setBlockAndUpdate(POS.east(),ForgeRegistries.BLOCKS.getValue(id("chipped:angry_end_stone")).defaultBlockState());
            }else{
                check(level.getBlockState(POS.east()).is(Blocks.STONE),"Old End block maps to stone");
                var chest=(ChestBlockEntity)level.getBlockEntity(POS);
                for(int slot=0;slot<3;slot++){
                    var stack=chest.getItem(slot);String target=slot==2?"contact:postcard":"farmersdelight:iron_knife";
                    check(stack.is(ForgeRegistries.ITEMS.getValue(id(target))),"Saved item migration "+slot);
                    check(stack.getTag().getString("NoEndPreserved").equals("slot"+slot),"Saved custom metadata "+slot);
                }
                check(chest.getItem(3).is(Items.AMETHYST_SHARD)&&chest.getItem(3).getCount()==3,"Old pearls migrate without duplication");
                check(new ItemStack(Items.ENDER_PEARL).is(Items.AMETHYST_SHARD),"Live stack conversion on server thread");
                check(new ItemStack(Items.IRON_INGOT).is(Items.IRON_INGOT),"Unrelated stacks unchanged");
                var paper=Class.forName("com.flechazo.contact.common.registry.ItemRegistry");
                for(String field:List.of("ENDER_POSTCARD","ENDER_PARCEL","ENDER_WRAPPING_PAPER"))check(((Supplier<?>)paper.getField(field).get(null)).get()==Items.AIR,"Detached Contact field "+field);
                var knives=Class.forName("vectorwing.farmersdelight.common.registry.ModItems");
                for(String field:List.of("DIAMOND_KNIFE","NETHERITE_KNIFE"))check(((Supplier<?>)knives.getField(field).get(null)).get()==Items.AIR,"Detached knife field "+field);
                var sheep=EntityType.SHEEP.create(level);sheep.moveTo(8,100,8,0,0);check(level.addFreshEntity(sheep),"Ordinary mobs remain");
                for(var type:List.of(EntityType.ENDERMAN,EntityType.ENDERMITE,EntityType.SHULKER,EntityType.ENDER_DRAGON)){
                    var entity=type.create(level);entity.moveTo(8,100,8,0,0);check(!level.addFreshEntity(entity),"End mob cannot enter: "+type);
                }
                var end=new EntityTravelToDimensionEvent(sheep,Level.END);MinecraftForge.EVENT_BUS.post(end);check(end.isCanceled(),"End travel rejected");
                var overworld=new EntityTravelToDimensionEvent(sheep,Level.OVERWORLD);MinecraftForge.EVENT_BUS.post(overworld);check(!overworld.isCanceled(),"Exit from End remains possible");
                for(String value:List.of("minecraft:ender_eye","minecraft:ender_chest","farmersdelight:diamond_knife","farmersdelight:netherite_knife_smithing","contact:ender_wrapping_paper"))check(server.getRecipeManager().byKey(id(value)).isEmpty(),"Recipe excluded "+value);
                CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(),true,server.registryAccess());
                for(var tab:CreativeModeTabs.allTabs())for(var stack:tab.getDisplayItems()){
                    Item raw=(Item)stack.getClass().getMethod("empirecraft$rawItem").invoke(stack);
                    String value=ForgeRegistries.ITEMS.getKey(raw).toString();
                    check(!value.contains("purpur")&&!value.contains("end_stone")&&!value.equals("minecraft:ender_pearl")&&!value.startsWith("contact:ender_"),"Creative exclusion "+value);
                }
                System.out.println("NO_END_MIGRATION_STACK_ENTITY_TRAVEL_CREATIVE_PASS");
            }
            System.out.println("NO_END_SMOKE_PASS baseline="+BASELINE+" native_ids="+count);
            java.nio.file.Files.writeString(java.nio.file.Path.of("result-"+BASELINE+".txt"),"PASS");
        }catch(Throwable e){System.err.println("NO_END_SMOKE_FAIL");e.printStackTrace();try{java.nio.file.Files.writeString(java.nio.file.Path.of("result-"+BASELINE+".txt"),e.toString());}catch(Exception ignored){}}
        server.halt(false);
    }
}
