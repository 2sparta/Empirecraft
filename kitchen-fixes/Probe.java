package ua.empirecraft.kitchentest;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import umpaz.farmersrespite.common.block.entity.KettleBlockEntity;
import umpaz.farmersrespite.common.registry.FRRecipeTypes;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import com.pockyl.handson_delight.station.*;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;
import ua.empirecraft.kitchen.heat.HeatRules;
import net.minecraft.world.level.material.Fluids;

@Mod("empirecraft_kitchen_probe")
public class Probe {
    static int checks;
    static void check(boolean test,String message) { checks++; if(!test)throw new AssertionError(message); }
    static Item item(String id) { var i=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));check(i!=null&&i!=Items.AIR,"Item "+id);return i; }
    static BlockEntity place(net.minecraft.server.level.ServerLevel level,String id,BlockPos pos) {
        var block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));check(block!=null,"Block "+id);
        level.setBlockAndUpdate(pos,block.defaultBlockState());return level.getBlockEntity(pos);
    }
    static void heat(net.minecraft.server.level.ServerLevel level,BlockPos pos) {level.setBlockAndUpdate(pos.below(),Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,true));}
    static int ready(KitchenPot pot) {return pot.handson$meal().getCount()+pot.handson$getItem(8).getCount();}
    public Probe() { MinecraftForge.EVENT_BUS.addListener(this::start); }
    void start(ServerStartedEvent event) {
        var server=event.getServer();
        try {
            var level=server.overworld();var manager=server.getRecipeManager();var origin=new BlockPos(0,100,0);
            var kettle=(KettleBlockEntity)place(level,"farmersrespite:kettle",origin);heat(level,origin);
            var station=(KitchenKettle)kettle;
            var recipe=manager.getAllRecipesFor(FRRecipeTypes.BREWING.get()).stream()
                .filter(r->r.getFluidIn().getFluid()==Fluids.WATER&&ForgeRegistries.FLUIDS.getKey(r.getFluidOut().getFluid()).toString().equals("farmersrespite:green_tea")).findFirst().orElseThrow();
            for(int i=0;i<recipe.getIngredients().size();i++) {
                var stack=recipe.getIngredients().get(i).getItems()[0].copy();stack.setCount(64);station.handson$setItem(i,stack);
            }
            for(int cycle=0;cycle<3;cycle++) {
                ItemStack bucket=new ItemStack(Items.WATER_BUCKET);
                if(cycle==2)check(kettle.getFluidTank().fill(new FluidStack(Fluids.WATER,1000),IFluidHandler.FluidAction.EXECUTE)==1000,"Automation refills kettle");
                else check(!kettle.fluidExtract(kettle,bucket,ItemStack.EMPTY).isEmpty(),"Native world use refills kettle "+cycle);
                for(int tick=0;tick<recipe.getBrewTime()*20+60&&station.handson$fluid()!=recipe.getFluidOut().getFluid();tick++)
                    KettleBlockEntity.brewingTick(level,origin,kettle.getBlockState(),kettle);
                check(station.handson$fluid()==recipe.getFluidOut().getFluid(),"Repeated tea brewing "+cycle);
                check(station.handson$getItem(0).getCount()==63-cycle,"Tea consumes one set, leaves stay "+cycle);
                for(int serving=0;serving<4;serving++)check(!kettle.fluidExtract(kettle,new ItemStack(Items.GLASS_BOTTLE),ItemStack.EMPTY).isEmpty(),"Pour tea "+cycle+"/"+serving);
                check(station.handson$amount()==0,"Tea drained completely "+cycle);
            }
            var green=recipe.getFluidOut().getFluid();
            check(RecipeBases.visible(Fluids.EMPTY,Fluids.WATER),"Empty kettle offers ordinary tea");
            check(RecipeBases.visible(Fluids.WATER,Fluids.WATER),"Water offers ordinary tea");
            check(!RecipeBases.visible(Fluids.WATER,green),"Water hides tea upgrades");
            check(RecipeBases.visible(green,green),"Green tea offers its upgrades");
            check(!RecipeBases.visible(green,Fluids.WATER),"Tea hides ordinary water recipes");

            long clock=level.getGameTime();
            for(int resultCount:new int[]{1,2})for(boolean bonus:new boolean[]{false,true}) {
                String recipeId=resultCount==1?"farmersdelight:cooking/cooked_rice":"farmersdelight:cooking/dumplings";
                var dish=(vectorwing.farmersdelight.common.crafting.CookingPotRecipe)manager.byKey(new ResourceLocation(recipeId)).orElseThrow();
                var pos=origin.east(4+resultCount*3+(bonus?1:0));
                var block=(CookingPotBlockEntity)place(level,"farmersdelight:cooking_pot",pos);heat(level,pos);var pot=(KitchenPot)block;
                var cooking=pot.handson$cooking();cooking.watchFood(clock);
                for(int i=0;i<dish.getIngredients().size();i++){var stack=dish.getIngredients().get(i).getItems()[0].copy();stack.setCount(64);pot.handson$setItem(i,stack);}
                cooking.feedByHand();cooking.watchFood(clock);
                check(pot.handson$resultOf(dish,server.registryAccess()).getCount()==resultCount,"Native output count "+recipeId);
                check(PotLogic.batch(pot,dish)==4/resultCount,"Batch measures four output units "+recipeId);
                if(bonus) {var carried=PotCooking.class.getDeclaredField("bonusCarried");carried.setAccessible(true);carried.setFloat(cooking,.99f);}
                int elapsed=0;
                while(ready(pot)==0&&elapsed<4000) {
                    ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(++clock);cooking.attend(clock);cooking.stir(clock+3);
                    if(bonus)for(String field:new String[]{"stars","settled","lastMove"}){var f=StirSkill.class.getDeclaredField(field);f.setAccessible(true);if(field.equals("stars"))f.setFloat(cooking.skill(),3);else f.setLong(cooking.skill(),clock);}
                    CookingPotBlockEntity.cookingTick(level,pos,block.getBlockState(),block);elapsed++;
                }
                check(ready(pot)>0,"Stirred food completes "+recipeId);
                check(ready(pot)==4,"Only four items finish even with bonus "+recipeId+"/"+bonus+" got "+ready(pot));
                check(pot.handson$getItem(0).getCount()>=60,"Remaining ingredients stay in pot");
                check(pot.handson$cookTime()==0,"Next batch starts its own cooking time");
                int count=ready(pot);((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(++clock);cooking.attend(clock);cooking.stir(clock+3);CookingPotBlockEntity.cookingTick(level,pos,block.getBlockState(),block);
                check(ready(pot)==count,"Next batch is not instant");
            }
            check(!ForgeRegistries.ITEMS.containsKey(new ResourceLocation("tfc:food/cooked_egg")),"Duplicate TFC fried egg is unregistered");
            Item cooked=item("naturalist:cooked_egg");
            var eggs=ForgeRegistries.ITEMS.tags().getTag(net.minecraft.tags.ItemTags.create(new ResourceLocation("empirecraft_kitchen:raw_eggs")));
            int eggCount=0;
            for(Item egg:eggs){
                eggCount++;var input=new net.minecraft.world.SimpleContainer(new ItemStack(egg));
                for(var type:List.of(RecipeType.SMELTING,RecipeType.SMOKING,RecipeType.CAMPFIRE_COOKING)){
                    var found=manager.getRecipeFor((RecipeType)type,input,level);check(found.isPresent(),"Egg cooks "+ForgeRegistries.ITEMS.getKey(egg)+"/"+type);
                    check(((Recipe<?>)found.get()).getResultItem(server.registryAccess()).is(cooked),"All fried eggs have one output");
                }
                var heating=net.dries007.tfc.common.recipes.HeatingRecipe.getRecipe(new ItemStack(egg));check(heating!=null,"Egg also heats in TFC "+ForgeRegistries.ITEMS.getKey(egg));
            }
            System.out.println("KITCHEN_EGG_COUNT "+eggCount);
            var eggData=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("kubejs/data/empirecraft_kitchen/tags/items/raw_eggs.json"))).getAsJsonObject();
            for(var entry:eggData.getAsJsonArray("values")){String id=entry.isJsonPrimitive()?entry.getAsString():entry.getAsJsonObject().get("id").getAsString();if(id.startsWith("#"))continue;var key=new ResourceLocation(id);if(ForgeRegistries.ITEMS.containsKey(key))check(eggs.contains(ForgeRegistries.ITEMS.getValue(key)),"Every registered animal/breed egg is included "+id);}
            check(eggCount>=29,"All registered egg breeds and animal eggs included, actual="+eggCount);
            for(var r:manager.getRecipes())check(!r.getResultItem(server.registryAccess()).is(item("farmersdelight:fried_egg")),"Retired FD egg output "+r.getId());

            var player=FakePlayerFactory.getMinecraft(level);var warmPos=new BlockPos(100,100,100);player.setPos(100.5,100,100.5);
            float ambient=TemperatureUtil.getPlayerTargetTemperature(player);
            for(int i=0;i<8;i++) {
                var pos=warmPos.offset(i%3-1,0,i/3-1);if(pos.equals(warmPos))continue;
                var b=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("farmersdelight:stove"));level.setBlockAndUpdate(pos,b.defaultBlockState().setValue(BlockStateProperties.LIT,true));
            }
            float household=TemperatureUtil.getPlayerTargetTemperature(player);
            check(household<=Math.max(24,ambient)+.1,"Multiple household heat sources remain comfortable: "+ambient+" -> "+household);
            var modifier=UUID.fromString("a1760752-947c-4989-82b9-a42981e9f50f");TemperatureUtil.addTemperatureModifier(player,20,modifier);
            float hotHouse=TemperatureUtil.getPlayerTargetTemperature(player);
            check(hotHouse<35||ambient+20>=35,"Household warmth does not cause heat stroke");
            level.setBlockAndUpdate(warmPos.below(),Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,true));
            float camp=TemperatureUtil.getPlayerTargetTemperature(player);check(camp>hotHouse,"Exposed campfire can overheat "+hotHouse+" -> "+camp);
            check(HeatRules.safe(37,20)==24&&HeatRules.safe(37,34)==34&&HeatRules.safe(38,38)==38,"Ambient heat is preserved independently of household cap");
            System.out.println("KITCHEN_NATIVE_PASS checks="+checks+" tea_cycles=3 servings=4 bonus=bounded eggs="+eggCount+" household="+household+" campfire="+camp);
        }catch(Throwable error){System.out.println("KITCHEN_NATIVE_FAIL "+error);error.printStackTrace();}
        finally{server.halt(false);}
    }
}
