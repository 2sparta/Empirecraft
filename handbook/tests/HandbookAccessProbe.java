package ua.empirecraft.handbook.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Runs only in a disposable generated Mohist world, with the installed recipe scripts. */
@Mod("empirecraft_handbook_probe")
public final class HandbookAccessProbe {
    private static int checks;
    private static void check(boolean value,String message) {
        checks++;
        if(!value)throw new AssertionError(message);
    }
    private static boolean handbook(ItemStack stack) {
        return !stack.isEmpty() && new ResourceLocation("patchouli:guide_book").equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))
            && stack.hasTag() && "empirecraft_guidebook:handbook".equals(stack.getTag().getString("patchouli:book"));
    }
    public HandbookAccessProbe() {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event)->{
            var server=event.getServer();
            try {
                var player=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.fromString("adee9b3e-2ea1-4b21-873f-137c7e683638"),"HandbookProbe"));
                var manager=server.getRecipeManager();
                check(manager.byKey(new ResourceLocation("empirecraft_guidebook:handbook")).isPresent(),"recipe registered");
                check(vazkii.patchouli.common.book.BookRegistry.INSTANCE.books.containsKey(new ResourceLocation("empirecraft_guidebook:handbook")),"server registers book identity");
                for(String unlock:new String[]{"handbook","paper","book","paper_finishing"})
                    check(server.getAdvancements().getAdvancement(new ResourceLocation("empirecraft_guidebook:recipes/"+unlock))!=null,"material unlock loaded "+unlock);
                for(String nativeRecipe:new String[]{"crafting/papyrus_strips","barrel/soaked_papyrus_strip","loom/unrefined_paper","scraping/paper"})
                    check(manager.byKey(new ResourceLocation("tfc:"+nativeRecipe)).isPresent(),"native paper chain retained "+nativeRecipe);
                for(int row=0;row<3;row++) {
                    var paper=new TransientCraftingContainer(player.inventoryMenu,3,3);
                    for(int col=0;col<3;col++)paper.setItem(row*3+col,new ItemStack(Items.SUGAR_CANE));
                    var found=manager.getRecipeFor(RecipeType.CRAFTING,paper,server.overworld()).orElseThrow();
                    var result=found.assemble(paper,server.registryAccess());
                    check(result.is(Items.PAPER)&&result.getCount()==3,"three cane give three paper in row "+row);
                }
                for(int size:new int[]{2,3}) {
                    var book=new TransientCraftingContainer(player.inventoryMenu,size,size);
                    for(int i=0;i<3;i++)book.setItem(i,new ItemStack(Items.PAPER));
                    book.setItem(size*size-1,new ItemStack(Items.LEATHER));
                    var found=manager.getRecipeFor(RecipeType.CRAFTING,book,server.overworld()).orElseThrow();
                    check(found.assemble(book,server.registryAccess()).is(Items.BOOK),"ordinary book in "+size+" grid");
                }
                for(String knifeId:new String[]{"tfc:stone/knife/igneous_intrusive","farmersdelight:iron_knife"}) {
                    var knife=ForgeRegistries.ITEMS.getValue(new ResourceLocation(knifeId));
                    check(knife!=null&&knife!=Items.AIR,"knife exists "+knifeId);
                    var grid=new TransientCraftingContainer(player.inventoryMenu,2,2);
                    grid.setItem(0,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfc:unrefined_paper"))));
                    grid.setItem(3,new ItemStack(knife));
                    var found=manager.getRecipeFor(RecipeType.CRAFTING,grid,server.overworld()).orElseThrow();
                    check(found.getId().toString().equals("empirecraft_guidebook:paper_from_unrefined"),"TFC paper finishing selected "+knifeId);
                    check(found.assemble(grid,server.registryAccess()).is(Items.PAPER),"TFC paper becomes canonical paper");
                    check(!found.isSpecial()&&found.getIngredients().size()==2,"finishing is visible as a normal crafting recipe");
                    var remaining=found.getRemainingItems(grid).get(3);
                    check(remaining.is(knife)&&remaining.getDamageValue()==1,"knife remains and loses one durability");
                    grid.setItem(3,new ItemStack(Items.STICK));
                    check(manager.getRecipeFor(RecipeType.CRAFTING,grid,server.overworld()).isEmpty(),"stick cannot replace knife");
                }
                for(int size:new int[]{2,3}) {
                    for(int slot=0;slot<size*size;slot++) {
                        var grid=new TransientCraftingContainer(player.inventoryMenu,size,size);
                        grid.setItem(slot,new ItemStack(Items.PAPER));
                        var found=manager.getRecipeFor(RecipeType.CRAFTING,grid,server.overworld()).orElseThrow();
                        check(found.getId().toString().equals("empirecraft_guidebook:handbook"),"recipe selection "+size+" slot "+slot+": "+found.getId());
                        check(handbook(found.assemble(grid,server.registryAccess())),"actual selected result retains book identity");
                    }
                }
                var source=server.createCommandSourceStack().withEntity(player).withPermission(4);
                var dispatcher=server.getCommands().getDispatcher();
                String command="give @s patchouli:guide_book{\"patchouli:book\":\"empirecraft_guidebook:handbook\"} 1";
                var parsed=dispatcher.parse(command,source);
                check(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty(),"vanilla give parses NBT");
                check(dispatcher.execute(parsed)==1,"vanilla give executes");
                check(player.getInventory().items.stream().anyMatch(HandbookAccessProbe::handbook),"command gives identified handbook");
                var unlock=dispatcher.parse("recipe give @s empirecraft_guidebook:handbook",source);
                check(unlock.getExceptions().isEmpty()&&!unlock.getReader().canRead(),"recipe command recognizes handbook");
                check(dispatcher.execute(unlock)==1,"recipe command unlocks handbook");
                System.out.println("HANDBOOK_ACCESS_PASS checks="+checks+" grids=2x2,3x3 positions=13 command=give,recipe paper=cane,TFC knives=TFC,FD book=vanilla");
            }catch(Throwable error) {
                System.out.println("HANDBOOK_ACCESS_FAIL");error.printStackTrace();
            }
            server.halt(false);
        });
    }
}
