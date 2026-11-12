package ua.empirecraft.tfc.test;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.registries.ForgeRegistries;
import net.dries007.tfc.common.capabilities.MoldLike;
import net.dries007.tfc.common.container.KnappingContainer;
import net.dries007.tfc.common.recipes.*;
import net.dries007.tfc.util.KnappingType;
import ua.empirecraft.tfc.*;

/** Real serializers, capabilities, player inventories and the patched native VS class. */
final class CurrentUpdateChecks {
    private static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static Item item(String id){Item v=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));check(v!=null&&v!=Items.AIR,"registered "+id);return v;}
    static JsonElement json(String name){try(var in=Integration.class.getResourceAsStream('/'+name)){
        return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in,name),StandardCharsets.UTF_8));
    }catch(IOException ex){throw new UncheckedIOException(ex);}}
    static Recipe<?> recipe(MinecraftServer server,String id){return server.getRecipeManager().byKey(new ResourceLocation(id)).orElseThrow(()->new AssertionError("missing recipe "+id));}
    static void run(MinecraftServer server,ServerPlayer player){
        casting(server,player);legacy(player);ammunition(server,player);passengers();handbook(server,player);
        System.out.println("CURRENT_UPDATE_NATIVE_PASS assertions="+checks);
    }
    static void casting(MinecraftServer server,ServerPlayer player){
        var level=server.overworld();var matrix=json("empirecraft-tfc-casting-parity.json").getAsJsonArray();
        check(matrix.size()==22,"all 22 old casting outputs retained");
        Set<String> shapes=new HashSet<>();
        for(var entry:matrix){
            var row=entry.getAsJsonObject();String prefix=row.get("metal").getAsString(),tool=row.get("tool").getAsString();
            String shape=tool.equals("sword")?"sword_blade":tool+"_head";shapes.add(shape);
            String metal=prefix.equals("iron")?"wrought_iron":prefix.equals("golden")?"gold":prefix;
            ItemStack moldStack=new ItemStack(item(row.get("mold").getAsString()));MoldLike mold=MoldLike.get(moldStack);
            check(mold!=null,"native TFC mould capability "+prefix+tool);
            int units=row.get("cast_units").getAsInt();
            var fluid=ForgeRegistries.FLUIDS.getValue(new ResourceLocation("tfc:metal/"+metal));
            check(mold.fill(new FluidStack(fluid,units),FluidAction.EXECUTE)==units,"mould accepts selected metal and exact volume "+prefix+tool);
            mold.setTemperature(0);
            CastingRecipe cast=(CastingRecipe)recipe(server,"empirecraft_tfc:casting/"+prefix+"_"+shape+"_"+metal);
            check(cast.matches(mold,level),"native casting matches "+prefix+tool);
            ItemStack blank=cast.assemble(mold,level.registryAccess());
            check(Integration.id(blank.getItem()).equals(row.get("blank").getAsString()),"correct native casting blank "+prefix+tool);
            ItemStack output=blank;
            if(!tool.equals("sword")){
                var finish=recipe(server,"empirecraft_tfc:casting/finish_"+prefix+"_"+shape);
                var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);
                grid.setItem(0,blank);int extra=row.get("extra_ingots").getAsInt();
                String ingot=switch(prefix){case "copper"->"minecraft:copper_ingot";case "iron"->"minecraft:iron_ingot";case "golden"->"minecraft:gold_ingot";default->"overgeared:steel_ingot";};
                for(int i=0;i<extra;i++)grid.setItem(i+1,new ItemStack(item(ingot)));
                grid.setItem(extra+1,new ItemStack(item("tfc:stone/hammer/igneous_intrusive")));
                @SuppressWarnings("unchecked") Recipe<net.minecraft.world.inventory.CraftingContainer> craft=(Recipe<net.minecraft.world.inventory.CraftingContainer>)finish;
                check(craft.matches(grid,level),"actual finishing grid "+prefix+tool);
                output=craft.assemble(grid,level.registryAccess());
                var remaining=craft.getRemainingItems(grid);
                check(!remaining.get(extra+1).isEmpty(),"hammer survives finishing "+prefix+tool);
                check(remaining.get(0).isEmpty(),"casting blank consumed "+prefix+tool);
            }
            check(Integration.id(output.getItem()).equals(row.get("output").getAsString()),"correct Overgeared output "+prefix+tool);
            check(output.hasTag()&&output.getTag().contains("Polished")&&!output.getTag().getBoolean("Polished"),"polishing still required "+prefix+tool);
            HeatingRecipe heating=(HeatingRecipe)recipe(server,"empirecraft_tfc:heating/cast_"+prefix+"_"+shape);
            check(heating.assembleFluid(new net.dries007.tfc.common.recipes.inventory.ItemStackInventory(output)).getAmount()==row.get("total_units").getAsInt(),"remelt equals casting plus added ingots "+prefix+tool);
            player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,moldStack);player.setShiftKeyDown(true);
            moldStack.getItem().use(level,player,InteractionHand.MAIN_HAND);player.setShiftKeyDown(false);
            ItemStack extracted=ItemStack.EMPTY;
            for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(blank.getItem())){extracted=player.getInventory().getItem(i);break;}
            check(!extracted.isEmpty(),"native player unmould action "+prefix+tool);
            if(tool.equals("sword"))check(extracted.hasTag()&&extracted.getTag().contains("Polished")&&!extracted.getTag().getBoolean("Polished"),"unmoulding preserves required polishing");
        }
        for(String shape:shapes){
            var knap=(KnappingRecipe)recipe(server,"tfc:clay_knapping/"+shape+"_mold");
            player.getInventory().clearContent();ItemStack clay=new ItemStack(Items.CLAY_BALL,5);player.setItemInHand(InteractionHand.MAIN_HAND,clay);
            KnappingType type=KnappingType.get(player);check(type==knap.getKnappingType(),"five vanilla clay balls open native mould knapping "+shape);
            var menu=KnappingContainer.create(clay,type,InteractionHand.MAIN_HAND,player.getInventory().selected,player.getInventory(),77);
            var pattern=knap.getPattern();
            for(int y=0;y<5;y++)for(int x=0;x<5;x++)if(x<pattern.getWidth()&&y<pattern.getHeight()&&!pattern.get(x,y))menu.onButtonPress(y*5+x,null);
            check(knap.matches(new KnappingContainer.Query(menu),level),"native player clicks produce mould pattern "+shape);
            check(menu.getSlot(0).getItem().is(item("tfc:ceramic/unfired_"+shape+"_mold")),"unfired mould appears in output "+shape);
            menu.removed(player);check(clay.isEmpty(),"completed mould consumes five clay "+shape);
            var firing=recipe(server,"tfc:heating/"+shape+"_mold");
            check(firing.getResultItem(level.registryAccess()).is(item("tfc:ceramic/"+shape+"_mold")),"firing yields usable mould "+shape);
        }
        for(var r:server.getRecipeManager().getRecipes()){
            String serializer=ForgeRegistries.RECIPE_SERIALIZERS.getKey(r.getSerializer()).toString();
            check(!Set.of("overgeared:crafting_cast","overgeared:cast_smelting","overgeared:cast_blasting").contains(serializer),"no retired cast recipe "+r.getId());
        }
    }
    static void legacy(ServerPlayer player){
        player.getInventory().clearContent();ItemStack old=new ItemStack(item("overgeared:clay_tool_cast"));
        var tag=old.getOrCreateTag();tag.putString("ToolType","pickaxe");tag.getCompound("Materials");
        var materials=new net.minecraft.nbt.CompoundTag();materials.putDouble("iron",27);tag.put("Materials",materials);
        player.getInventory().setItem(10,old);check(LegacyMolds.migrate(player,10,old),"old filled mould migrates");
        check(player.getInventory().getItem(10).is(item("tfc:ceramic/pickaxe_head_mold")),"shape retained during migration");
        int iron=0;for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(stack.is(Items.IRON_INGOT))iron+=stack.getCount();}
        check(iron==3,"old 27-nugget fill refunds exactly 3 ingots");
        check(!LegacyMolds.migrate(player,10,player.getInventory().getItem(10)),"migration cannot refund twice");
        old=new ItemStack(item("overgeared:clay_tool_cast"));old.getOrCreateTag().putString("ToolType","hammer");
        ItemStack finished=new ItemStack(item("overgeared:steel_hammer_head"));finished.getOrCreateTag().putString("ForgingQuality","excellent");
        old.getOrCreateTag().put("Output",finished.save(new net.minecraft.nbt.CompoundTag()));player.getInventory().setItem(11,old);
        check(LegacyMolds.migrate(player,11,old),"ready legacy output migrates");
        check(player.getInventory().contains(finished),"finished head properties retained");
    }
    static void ammunition(MinecraftServer server,ServerPlayer player){
        var level=server.overworld();
        var polishGrid=new TransientCraftingContainer(player.inventoryMenu,3,3);
        polishGrid.setItem(0,new ItemStack(item("tfc_muskets:scrap")));polishGrid.setItem(1,new ItemStack(item("create:sand_paper")));
        var polish=(CraftingRecipe)recipe(server,"tfc_muskets:bullet");
        check(polish.matches(polishGrid,level)&&polish.assemble(polishGrid,level.registryAccess()).is(item("tfc_muskets:bullet")),"manual sandpaper gives an actual bullet");
        check(!polish.getRemainingItems(polishGrid).get(1).isEmpty(),"manual polishing wears rather than consumes whole sandpaper");
        var handler=new net.minecraftforge.items.ItemStackHandler(1);handler.setStackInSlot(0,new ItemStack(item("tfc_muskets:scrap")));
        var wrapper=new net.minecraftforge.items.wrapper.RecipeWrapper(handler);
        @SuppressWarnings("unchecked") Recipe<net.minecraftforge.items.wrapper.RecipeWrapper> mill=(Recipe<net.minecraftforge.items.wrapper.RecipeWrapper>)recipe(server,"tfc_muskets:polishing/bullet");
        check(mill.matches(wrapper,level)&&mill.assemble(wrapper,level.registryAccess()).is(item("tfc_muskets:bullet")),"enabled Create millstone route produces a bullet");
        var quern=(net.dries007.tfc.common.recipes.QuernRecipe)recipe(server,"tfc_muskets:quern/bullet");
        check(quern.matches(new net.dries007.tfc.common.recipes.inventory.ItemStackInventory(new ItemStack(item("tfc_muskets:scrap"))),level)
            &&quern.getResultItem(level.registryAccess()).is(item("tfc_muskets:bullet")),"TFC quern also finishes bullets for the existing Create drive");
        for(String metal:List.of("wrought_iron","cast_iron","lead")){
            var recipe=recipe(server,"tfc_muskets:scrap_"+metal);
            @SuppressWarnings("unchecked") Recipe<net.minecraft.world.inventory.CraftingContainer> craft=(Recipe<net.minecraft.world.inventory.CraftingContainer>)recipe;
            var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);
            String rod=metal.equals("lead")?"empirecraft_tfc:lead_rod":"tfc:metal/rod/"+metal;
            grid.setItem(0,new ItemStack(item(rod)));grid.setItem(1,new ItemStack(item("tfc:stone/hammer/igneous_intrusive")));
            grid.setItem(2,new ItemStack(item("tfc:metal/chisel/copper")));
            check(craft.matches(grid,level),"all three metal rods work for scrap "+metal);
            ItemStack out=craft.assemble(grid,level.registryAccess());
            check(out.is(item("tfc_muskets:scrap"))&&out.getCount()==5,"50 metal units give five bullet blanks "+metal);
            var remaining=craft.getRemainingItems(grid);check(!remaining.get(1).isEmpty()&&!remaining.get(2).isEmpty(),"both tools survive ammunition work");
        }
        var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);grid.setItem(0,new ItemStack(Items.GUNPOWDER));
        var pinch=(CraftingRecipe)recipe(server,"tfc_muskets:gunpowder_pinch");
        check(pinch.matches(grid,level)&&pinch.assemble(grid,level.registryAccess()).getCount()==9,"one powder gives nine measured charges");
        grid=new TransientCraftingContainer(player.inventoryMenu,3,3);for(int i=0;i<9;i++)grid.setItem(i,new ItemStack(item("tfc_muskets:gunpowder_pinch")));
        var powder=(CraftingRecipe)recipe(server,"tfc_muskets:gunpowder");
        check(powder.matches(grid,level)&&powder.assemble(grid,level.registryAccess()).getCount()==1,"nine charges return exactly one powder");
        grid=new TransientCraftingContainer(player.inventoryMenu,3,3);grid.setItem(0,new ItemStack(item("tfc_muskets:bullet")));grid.setItem(1,new ItemStack(item("tfc_muskets:gunpowder_pinch")));
        var cartridge=(CraftingRecipe)recipe(server,"musketmod:cartridge");check(!cartridge.matches(grid,level),"cartridge still needs paper");
        grid.setItem(2,new ItemStack(Items.PAPER));check(cartridge.matches(grid,level)&&cartridge.assemble(grid,level.registryAccess()).getCount()==1,"one bullet, charge and paper give one cartridge");
        for(String id:List.of("musketmod:musket","musketmod:musket_with_scope","musketmod:musket_with_bayonet","musketmod:pistol","musketmod:blunderbuss","medieval_boomsticks:arquebus","medieval_boomsticks:handgonne")){
            recipe(server,id);
            var spec=json("empirecraft-tfc-policy.json").getAsJsonObject().getAsJsonObject("overrides").getAsJsonObject(id);
            for(var ingredient:spec.getAsJsonObject("key").entrySet())
                check(Ingredient.fromJson(ingredient.getValue()).getItems().length>0,"gun component is available "+id+" "+ingredient.getKey());
        }
    }
    static void handbook(MinecraftServer server,ServerPlayer player){
        ResourceLocation id=new ResourceLocation("empirecraft_guidebook:handbook");
        var book=vazkii.patchouli.common.book.BookRegistry.INSTANCE.books.get(id);
        check(book!=null,"Patchouli discovers handbook from lowcode mod");
        check(book.getBookItem().getOrCreateTag().getString("patchouli:book").equals(id.toString()),"native handbook item points to correct book");
        var r=recipe(server,"empirecraft_guidebook:handbook");
        var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);grid.setItem(0,new ItemStack(Items.PAPER));
        @SuppressWarnings("unchecked") Recipe<net.minecraft.world.inventory.CraftingContainer> craft=(Recipe<net.minecraft.world.inventory.CraftingContainer>)r;
        check(craft.matches(grid,server.overworld()),"one paper crafts handbook");
        check(craft.assemble(grid,server.overworld().registryAccess()).getOrCreateTag().getString("patchouli:book").equals(id.toString()),"crafting preserves Patchouli identity");
        JsonObject catalog;
        // Forge isolates named mod modules: Class.getResource on Integration cannot
        // read a different mod's root resource. Inspect the actual installed book JAR.
        try(var zip=new java.util.zip.ZipFile("mods/empirecraft-handbook-1.0.0.jar");
            var reader=new InputStreamReader(zip.getInputStream(zip.getEntry("empirecraft-handbook-catalog.json")),StandardCharsets.UTF_8)){
            catalog=JsonParser.parseReader(reader).getAsJsonObject();
        }catch(IOException ex){throw new UncheckedIOException(ex);}
        check(catalog.getAsJsonArray("categories").size()==25,"25 handbook chapters");
        check(catalog.getAsJsonArray("entries").size()>=320,"detailed guide coverage");
        for(var row:catalog.getAsJsonArray("categories"))item(row.getAsJsonObject().get("icon").getAsString());
        for(var row:catalog.getAsJsonArray("entries")){
            item(row.getAsJsonObject().get("icon").getAsString());
            for(var rid:row.getAsJsonObject().getAsJsonArray("recipes"))recipe(server,rid.getAsString());
        }
    }
    static void passengers(){
        var remote=new org.valkyrienskies.mod.common.util.EntityDraggingInformation();
        remote.setLastShipStoodOn(87L);remote.setLastShipStoodOnServerWriteOnly(87L);
        for(int tick=0;tick<400;tick++){
            remote.setTicksSinceStoodOnShip(remote.getTicksSinceStoodOnShip()+1);
            if(tick%5==0)remote.setTicksSinceLastServerPacket(0);
            check(remote.isEntityBeingDraggedByAShip(),"observer keeps same-ship passenger across repeated packets "+tick);
        }
        remote.setLastShipStoodOnServerWriteOnly(null);remote.setLastShipStoodOn(null);remote.setTicksSinceLastServerPacket(0);
        check(!remote.isEntityBeingDraggedByAShip(),"disembarking still detaches passenger");
        var server=new org.valkyrienskies.mod.common.util.EntityDraggingInformation();server.setLastShipStoodOn(87L);
        for(int tick=0;tick<400;tick++){
            server.setTicksSinceStoodOnShip(server.getTicksSinceStoodOnShip()+1);
            if(tick%5==0)server.setServerRelativePlayerPosition(new org.joml.Vector3d(28672000.5,65,12288000.5));
            check(server.isEntityBeingDraggedByAShip(),"server renews same-ship passenger "+tick);
        }
        server.setTicksSinceStoodOnShip(25);check(!server.isEntityBeingDraggedByAShip(),"lost updates retain native detach timeout");
    }
}
