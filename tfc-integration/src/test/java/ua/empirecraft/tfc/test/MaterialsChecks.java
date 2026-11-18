package ua.empirecraft.tfc.test;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.dries007.tfc.common.capabilities.glass.GlassOperation;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.recipes.*;
import net.dries007.tfc.common.recipes.inventory.ItemStackInventory;
import ua.empirecraft.tfc.*;

/** Native acquisition, real V1 ForgeCaps migration, protection and disk persistence. */
final class MaterialsChecks {
    private static int checks;
    private static final Capability<OneTimeTerrain.State> CAP=CapabilityManager.get(new CapabilityToken<>(){});
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static OneTimeTerrain.State state(LevelChunk chunk){return chunk.getCapability(CAP).orElseThrow(()->new AssertionError("terrain capability"));}
    private static String id(Block b){return Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(b)).toString();}
    private static ItemStack item(String id){return new ItemStack(CurrentUpdateChecks.item(id));}
    static void recipes(MinecraftServer server,ServerPlayer player){
        String[][] powders={{"minecraft:raw_iron","hematite"},{"minecraft:raw_copper","native_copper"},
            {"minecraft:raw_gold","native_gold"},{"antiquelegacy:raw_tin","cassiterite"}};
        for(var pair:powders){
            var r=(QuernRecipe)CurrentUpdateChecks.recipe(server,"empirecraft_tfc:quern/pigment_"+pair[1]);
            var inv=new ItemStackInventory(item(pair[0]));
            check(r.matches(inv,server.overworld()),"canonical ore grinds into glass colourant "+pair[0]);
            ItemStack out=r.assemble(inv,server.registryAccess());
            check(out.is(item("tfc:powder/"+pair[1]).getItem())&&out.getCount()==5,"native pigment yield "+pair[1]);
            check(GlassOperation.getByPowder(out)!=null,"colourant accepted by native glass operation "+pair[1]);
        }
        var kelp=(HeatingRecipe)CurrentUpdateChecks.recipe(server,"empirecraft_tfc:heating/soda_ash_from_vanilla_kelp");
        var input=item("minecraft:dried_kelp");
        check(HeatCapability.get(input)!=null,"vanilla kelp receives actual TFC heat capability");
        check(!kelp.isValidTemperature(499)&&kelp.isValidTemperature(501),"kelp ash requires 500 C");
        ItemStack ash=kelp.assemble(new ItemStackInventory(input),server.registryAccess());
        check(ash.is(item("tfc:powder/soda_ash").getItem())&&ash.getCount()==3,"native heating produces three soda ash");
        check(GlassOperation.getByPowder(item("minecraft:charcoal"))==GlassOperation.GRAPHITE,"real Mixin substitutes charcoal for retired graphite");
        check(GlassOperation.POWDERS.get().get(Items.CHARCOAL)==GlassOperation.GRAPHITE,"JEI's actual operation map shows charcoal");
        check(GlassOperation.POWDERS.get().keySet().stream().noneMatch(i->Integration.id(i).equals("tfc:powder/graphite")),"retired graphite is absent from glass ingredient display");
        var bowl=new net.dries007.tfc.common.blockentities.BowlBlockEntity(BlockPos.ZERO,Blocks.AIR.defaultBlockState());
        check(item("minecraft:charcoal").is(TagKey.create(Registries.ITEM,new ResourceLocation("tfc:powders"))),"charcoal is admitted by powder-bowl tag");
        check(bowl.isItemValid(0,item("minecraft:charcoal")),"native powder-bowl inventory admits charcoal");
        for(var pair:new String[][]{{"silica","minecraft:sand"},{"silica","tfc:sand/white"},{"hematitic","minecraft:red_sand"},
                                    {"olivine","tfc:sand/green"},{"volcanic","tfc:sand/black"}}){
            var craft=(CraftingRecipe)CurrentUpdateChecks.recipe(server,"tfc:crafting/"+pair[0]+"_glass_batch");
            for(String potash:List.of("tfc:powder/soda_ash","tfc:powder/saltpeter")){
                var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);
                for(int i=0;i<4;i++)grid.setItem(i,item(pair[1]));
                grid.setItem(4,item(potash));grid.setItem(5,item("tfc:powder/lime"));
                check(craft.matches(grid,server.overworld()),"all four batches craft with available potash "+pair[0]+potash);
                ItemStack out=craft.assemble(grid,server.registryAccess());
                check(out.is(item("tfc:"+pair[0]+"_glass_batch").getItem())&&out.getCount()==4,"native glass batch quantity "+pair[0]);
            }
        }
        var grid=new TransientCraftingContainer(player.inventoryMenu,3,3);
        grid.setItem(0,item("tfc:white_kaolin_clay"));grid.setItem(1,item("tfc:powder/hematite"));
        var pink=(CraftingRecipe)CurrentUpdateChecks.recipe(server,"tfc:crafting/pink_kaolin_clay");
        check(pink.matches(grid,server.overworld()),"new canonical-iron pigment unlocks pink kaolin recipe");
        grid.setItem(0,pink.assemble(grid,server.registryAccess()));
        var red=(CraftingRecipe)CurrentUpdateChecks.recipe(server,"tfc:crafting/red_kaolin_clay");
        check(red.matches(grid,server.overworld())&&red.assemble(grid,server.registryAccess()).is(item("tfc:red_kaolin_clay").getItem()),"pink kaolin becomes red through actual native recipe");
        System.out.println("MATERIAL_RECIPES_NATIVE_PASS assertions="+checks);
    }
    private static void prepare(LevelChunk chunk){
        int ox=chunk.getPos().getMinBlockX(),oz=chunk.getPos().getMinBlockZ();
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            for(int y=-60;y<79;y++)chunk.setBlockState(new BlockPos(ox+x,y,oz+z),Blocks.STONE.defaultBlockState(),false);
            for(int y=79;y<=82;y++)chunk.setBlockState(new BlockPos(ox+x,y,oz+z),
                (x>=10?Blocks.SAND:y==82?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),false);
        }
        chunk.setBlockState(new BlockPos(ox,83,oz),Blocks.WATER.defaultBlockState(),false);
    }
    private static Set<String> groups(MaterialPalette.Plan p){
        Set<String> result=new HashSet<>(List.of("R:"+p.upper(),"R:"+p.middle(),"R:"+p.lower(),"D:"+p.soil()));
        if(p.surface()==MaterialPalette.Surface.CLAY)result.add("C:"+p.soil());
        if(p.surface()==MaterialPalette.Surface.KAOLIN)result.add("K");
        if(p.surface()==MaterialPalette.Surface.PEAT)result.add("P");return result;
    }
    static void terrain(MinecraftServer server){
        ServerLevel level=server.overworld();Set<String> wanted=new HashSet<>(),seen=new HashSet<>();
        for(String r:MaterialPalette.ROCKS)wanted.add("R:"+r);
        for(String s:MaterialPalette.SOILS){wanted.add("D:"+s);wanted.add("C:"+s);}wanted.addAll(List.of("K","P"));
        long seed=level.getSeed()^((long)level.dimension().location().hashCode()<<32);
        int fixtures=0;
        for(int candidate=0;candidate<20000&&!wanted.isEmpty();candidate++){
            int cx=100+candidate%80,cz=100+candidate/80;
            float temperature=level.getBiome(new BlockPos(cx*16+8,64,cz*16+8)).value().getBaseTemperature();
            var plan=MaterialPalette.plan(seed,cx,cz,temperature,true);Set<String> groups=groups(plan);
            if(Collections.disjoint(groups,wanted))continue;
            var chunk=level.getChunk(cx,cz);prepare(chunk);fixtures++;
            // Exact old persisted schema, with depleted ore pass already complete.
            var old=new CompoundTag();old.putInt("Version",1);old.putBoolean("Stones",true);old.putBoolean("Ores",true);
            state(chunk).deserializeNBT(old);
            int ox=cx*16,oz=cz*16;
            BlockPos ore=new BlockPos(ox+8,70,oz+8),hole=new BlockPos(ox+9,70,oz+8),built=new BlockPos(ox+8,38,oz+8);
            chunk.setBlockState(ore,Blocks.DIAMOND_ORE.defaultBlockState(),false);
            chunk.setBlockState(hole,Blocks.AIR.defaultBlockState(),false);
            chunk.setBlockState(built,Blocks.STONE_BRICKS.defaultBlockState(),false);
            check(OneTimeTerrain.process(level,chunk),"V1 completed chunk receives independent material pass");
            check(state(chunk).ores&&state(chunk).stones&&state(chunk).materials==1,"migration preserves old flags and completes materials");
            check(chunk.getBlockState(ore).is(Blocks.DIAMOND_ORE)&&chunk.getBlockState(hole).isAir()&&chunk.getBlockState(built).is(Blocks.STONE_BRICKS),"existing ores, excavations and masonry survive geology");
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=-60;y<=84;y++){
                String id=id(chunk.getBlockState(new BlockPos(ox+x,y,oz+z)).getBlock());if(id.startsWith("tfc:"))seen.add(id);
                check(!id.startsWith("tfc:ore/")&&!id.equals("antiquelegacy:tin_ore"),"completed legacy ore pass is never replayed");
            }
            check(!seen.contains("tfc:sand/green") && !seen.contains("tfc:sand/yellow") && !seen.contains("tfc:sand/red"),
                "test site generates neither Hawaiian green sand nor retired duplicates");
            check(!OneTimeTerrain.process(level,chunk),"material pass is idempotent");
            wanted.removeAll(groups);
            check(fixtures<=40,"bounded representative fixture count");
        }
        check(wanted.isEmpty(),"representative fixtures exercise every material family "+wanted);
        for(String rock:MaterialPalette.ROCKS)check(seen.contains("tfc:rock/raw/"+rock),"native raw rock generated "+rock);
        for(String soil:MaterialPalette.SOILS)for(String type:List.of("dirt/","grass/","clay/","clay_grass/"))
            check(seen.contains("tfc:"+type+soil),"native soil generated "+type+soil);
        for(String path:List.of("red_kaolin_clay","pink_kaolin_clay","white_kaolin_clay","kaolin_clay_grass","peat","peat_grass"))
            check(seen.contains("tfc:"+path),"native surface resource generated "+path);
        var saved=level.getChunk(100,100); // Always first selected fixture.
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=-60;y<=84;y++){
            BlockPos p=new BlockPos(1600+x,y,1600+z);
            if(id(saved.getBlockState(p).getBlock()).startsWith("tfc:"))saved.setBlockState(p,Blocks.AIR.defaultBlockState(),false);
        }
        saved.setUnsaved(true);check(!OneTimeTerrain.process(level,saved),"harvested materials stay absent before restart");
        var protectedChunk=level.getChunk(99,99);prepare(protectedChunk);
        check(NaturalMaterials.generate(level,protectedChunk,p->false)==0,"denied wilderness columns receive zero changes");
        double border=level.getWorldBorder().getSize();level.getWorldBorder().setSize(128);
        check(!OneTimeTerrain.process(level,protectedChunk)&&state(protectedChunk).materials==0,"outside border remains untouched and pending");
        level.getWorldBorder().setSize(border);
        var future=new OneTimeTerrain.State();var tag=state(saved).serializeNBT();tag.putInt("Materials",99);future.deserializeNBT(tag);
        check(future.materials==99&&future.ores&&future.stones,"future material completion is never downgraded");
        System.out.println("MATERIAL_TERRAIN_NATIVE_PASS fixtures="+fixtures+" resources="+seen.size()+" assertions="+checks);
    }
    static void restart(MinecraftServer server){
        var chunk=server.overworld().getChunk(100,100);var state=state(chunk);
        check(state.ores&&state.stones&&state.materials==1,"old flags and material flag survive actual disk reload");
        check(!OneTimeTerrain.process(server.overworld(),chunk),"disk reload does not regenerate harvested geology");
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=-60;y<=84;y++)
            check(!id(chunk.getBlockState(new BlockPos(1600+x,y,1600+z)).getBlock()).startsWith("tfc:"),"depleted material stays depleted on restart");
        System.out.println("MATERIAL_RESTART_NATIVE_PASS assertions="+checks);
    }
}
