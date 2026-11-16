package ua.empirecraft.teatest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;
import com.pockyl.handson_delight.HandsOnConfig;
import com.pockyl.handson_delight.assembly.AssemblyRecipes;
import com.pockyl.handson_delight.station.*;
import umpaz.farmersrespite.common.block.entity.KettleBlockEntity;
import sereneseasons.init.ModFertility;
import sereneseasons.season.*;
@Mod("empirecraft_tea_probe")
public class Probe {
    static int checks; static java.util.List<String> problems=new ArrayList<>();
    static void check(boolean b,String why){checks++;if(!b)problems.add(why);}
    static ItemStack item(String id){return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));}
    static BlockEntity place(net.minecraft.server.level.ServerLevel l,String id,BlockPos p){var b=ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));check(b!=null,"Block exists "+id);l.setBlockAndUpdate(p,b.defaultBlockState());return l.getBlockEntity(p);}
    public Probe(){MinecraftForge.EVENT_BUS.addListener(this::start);}
    void start(ServerStartedEvent e){var server=e.getServer();try{
        var l=server.overworld();var manager=server.getRecipeManager();var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(l);var p=new BlockPos(0,100,0);
        check(!HandsOnConfig.removeCraftingRecipes(),"Grid crafting retained");
        check(place(l,"farmersdelight:cooking_pot",p) instanceof KitchenPot,"Interactive pot mixin");
        var board=place(l,"farmersdelight:cutting_board",p.east(2));check(board instanceof KitchenBoard,"Interactive board mixin");
        check(place(l,"farmersdelight:stove",p.east(4)) instanceof StoveTop,"Interactive stove mixin");
        check(place(l,"farmersdelight:skillet",p.east(6)) instanceof FryingPan,"Interactive skillet mixin");
        check(place(l,"brewinandchewin:keg",p.east(8)) instanceof KitchenKeg,"Interactive keg mixin");
        var kettle=(KettleBlockEntity)place(l,"farmersrespite:kettle",p.east(10));check(kettle instanceof KitchenKettle,"Interactive kettle mixin");
        var kitchen=(KitchenKettle)kettle;
        var water=new ItemStack(Items.WATER_BUCKET);check(kitchen.handson$useOne(water).is(Items.BUCKET)&&water.isEmpty(),"Kettle receives water and returns bucket");
        check(kitchen.handson$amount()==1000,"Kettle water volume conserved");
        var coffee=manager.byKey(new ResourceLocation("farmersrespite:brewing/coffee_from_water")).orElseThrow();
        for(var in:coffee.getIngredients()){
            check(in.test(item("rusticdelight:roasted_coffee_beans")),"Roasted coffee accepted");
            check(in.test(item("farmersrespite:coffee_beans")),"Respite coffee accepted");
            check(!in.test(item("rusticdelight:coffee_beans")),"Raw Rustic coffee requires roasting");
        }
        kitchen.handson$setItem(0,item("rusticdelight:roasted_coffee_beans"));kitchen.handson$setItem(1,item("farmersrespite:coffee_beans"));
        l.setBlockAndUpdate(kettle.getBlockPos().below(),net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState());
        for(int t=0;t<2420;t++)KettleBlockEntity.brewingTick(l,kettle.getBlockPos(),kettle.getBlockState(),kettle);
        check(kitchen.handson$fluid()==ForgeRegistries.FLUIDS.getValue(new ResourceLocation("farmersrespite:coffee")),"Mixed coffee brews through native kettle");
        check(kitchen.handson$getItem(0).isEmpty()&&kitchen.handson$getItem(1).isEmpty(),"Coffee ingredients consumed once");
        check(kitchen.handson$canServe(new ItemStack(Items.GLASS_BOTTLE)),"Brew can be served in bottles");
        for(String grain:List.of("barley","maize","oat","rice","rye","wheat")){
            var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);
            grid.setItem(0,item("tfc:food/"+grain+"_flour"));grid.setItem(1,item("farmersrespite:green_tea_leaves"));grid.setItem(2,item("tfc:food/"+grain+"_flour"));
            var selected=manager.getRecipeFor(RecipeType.CRAFTING,grid,l);check(selected.isPresent()&&selected.get().getResultItem(server.registryAccess()).is(item("farmersrespite:green_tea_cookie").getItem()),"Cookie made with "+grain+" flour");
            check(Cutting.findWith(l,item("tfc:food/"+grain+"_dough"),item("tfc:metal/knife/wrought_iron")).isPresent(),"Interactive pasta from "+grain+" dough");
        }
        for(String id:List.of("farmersrespite:coffee_cake","farmersrespite:green_tea_cookie"))check(AssemblyRecipes.all().stream().anyMatch(a->a.id().toString().equals(id)),"Board sees current integrated recipe "+id);
        for(var recipe:manager.getRecipes())if(Set.of("farmersdelight","farmersrespite","rusticdelight","brewinandchewin").contains(recipe.getId().getNamespace())){
            for(var in:recipe.getIngredients())check(in.isEmpty()||in.getItems().length>0,"Resolvable ingredient "+recipe.getId());
            var out=recipe.getResultItem(server.registryAccess());check(!out.is(item("create:dough").getItem())&&!out.is(item("farmersdelight:wheat_dough").getItem()),"Retired dough not restored "+recipe.getId());
        }
        // Test the real fertility lookup at open-sky height, not merely generated JSON.
        var data=SeasonHandler.getSeasonSavedData(l);int before=data.seasonCycleTicks;
        var time=new SeasonTime(0);int duration=time.getSeasonDuration();
        for(int season=0;season<4;season++){
            data.seasonCycleTicks=season*duration+duration/2;
            check(sereneseasons.api.season.SeasonHelper.getSeasonState(l).getSeason().ordinal()==season,"Fixture calendar season "+season);
            check(ModFertility.isCropFertile("farmersrespite:tea_bush",l,p)==(season==0||season==1),"Tea season "+season);
            check(ModFertility.isCropFertile("farmersrespite:coffee_bush",l,p)==(season==1||season==2),"Coffee season "+season);
            for(String wild:List.of("wild_tea_bush","wild_coffee_bush"))check(ModFertility.isCropFertile("farmersrespite:"+wild,l,p)==(season!=3),"Wild season "+wild+" "+season);
        }
        data.seasonCycleTicks=before;
        for(String id:List.of("farmersdelight:crimson_cabinet","farmersdelight:warped_cabinet"))check(!ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id)),"Dimensional exclusions retained "+id);
        check(!ForgeRegistries.ITEMS.containsKey(new ResourceLocation("farmersrespite:nether_wart_sourdough")),"Respite native exclusions retained");
        if(!problems.isEmpty())throw new AssertionError(problems.toString());
        System.out.println("TEA_NATIVE_PASS checks="+checks+" stations=6 coffee=brewed tfc_flours=6 tfc_dough=6 seasons=4 grid_crafting=retained dimensional_exclusions=retained");
    }catch(Throwable failure){System.out.println("TEA_NATIVE_FAIL "+failure);failure.printStackTrace();}finally{server.halt(false);}}
}
