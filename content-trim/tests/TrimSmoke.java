package ua.empirecraft.trimtest;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("empirecraft_trim_test")
public final class TrimSmoke {
    static boolean baseline = Boolean.getBoolean("trim.baseline");
    static net.minecraft.server.MinecraftServer server;
    int ticks;
    static final JsonObject removed;
    static { try(var r = new InputStreamReader(TrimSmoke.class.getResourceAsStream("/expected.json"))) {
        removed = JsonParser.parseReader(r).getAsJsonObject();
    } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    public TrimSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    static void require(boolean ok, String why) { if(!ok) throw new AssertionError(why); }
    static ResourceLocation id(String value) { return new ResourceLocation(value); }
    static ItemStack item(String value) { return new ItemStack(ForgeRegistries.ITEMS.getValue(id(value))); }
    static void registry() {
        for (String kind : List.of("shields", "chipped", "extras")) for (var row : removed.getAsJsonArray(kind)) {
            String value = row.getAsString();
            // MissingMappings deliberately leaves aliases for old saves. Check real native keys,
            // rather than containsKey(), which also accepts those migration aliases.
            require(ForgeRegistries.ITEMS.getKeys().contains(id(value)) == baseline, "Item registry " + value);
            if(!kind.equals("shields")) require(ForgeRegistries.BLOCKS.getKeys().contains(id(value)) == baseline, "Block registry " + value);
        }
        for (var row : removed.getAsJsonArray("chipped_block_only"))
            require(ForgeRegistries.BLOCKS.getKeys().contains(id(row.getAsString())) == baseline, "Wall torch registry " + row);
        for (var row : removed.getAsJsonArray("preserved_glowstone")) {
            require(ForgeRegistries.ITEMS.getKeys().contains(id(row.getAsString())), "Preserved glowstone item " + row);
            require(ForgeRegistries.BLOCKS.getKeys().contains(id(row.getAsString())), "Preserved glowstone block " + row);
        }
        for (String value : List.of("chipped:angry_stone", "chipped:obsidian_bricks", "chipped:acacia_torch",
                "chipped:glowstone_lantern", "magistuarmory:steel_buckler", "magistuarmory:diamond_pike"))
            require(ForgeRegistries.ITEMS.containsKey(id(value)), "Preserved item " + value);
        int blocks = 0, items = 0;
        for(var value:ForgeRegistries.BLOCKS.getKeys()) if(List.of("chipped","chippedexttras").contains(value.getNamespace())) blocks++;
        for(var value:ForgeRegistries.ITEMS.getKeys()) if(List.of("chipped","chippedexttras","magistuarmory").contains(value.getNamespace())) items++;
        System.out.println("TRIM_COUNTS baseline="+baseline+" blocks="+blocks+" items="+items);
    }
    private void started(ServerStartedEvent event) {
        server = event.getServer();
        try {
            registry();
            var level = server.overworld(); level.setDayTime(18000);
            var pos = new BlockPos(4,100,4); level.getChunkAt(pos);
            if(baseline) {
                level.setBlockAndUpdate(pos, ForgeRegistries.BLOCKS.getValue(id(removed.getAsJsonArray("chipped").get(0).getAsString())).defaultBlockState());
                level.setBlockAndUpdate(pos.east(), ForgeRegistries.BLOCKS.getValue(id(removed.getAsJsonArray("extras").get(0).getAsString())).defaultBlockState());
                level.setBlockAndUpdate(pos.south(), Blocks.CHEST.defaultBlockState());
                ((ChestBlockEntity)level.getBlockEntity(pos.south())).setItem(0, item("magistuarmory:diamond_buckler"));
                level.setBlockAndUpdate(pos.west(), ForgeRegistries.BLOCKS.getValue(id("chipped:angry_netherrack")).defaultBlockState());
                level.setBlockAndUpdate(pos.north(), ForgeRegistries.BLOCKS.getValue(id("chipped:crimson_crate")).defaultBlockState());
                ((ChestBlockEntity)level.getBlockEntity(pos.south())).setItem(1, item("chipped:nether_engraved_glowstone"));
            }
            for(var row : removed.getAsJsonArray("mobs")) {
                String name = row.getAsString();
                var mob = ForgeRegistries.ENTITY_TYPES.getValue(id(name)).create(level);
                mob.moveTo(8,100,8,0,0);
                if(mob instanceof Mob m) { m.setNoAi(true); m.setPersistenceRequired(); }
                require(level.addFreshEntity(mob) == baseline, "Entity join " + name);
            }
            var sheep = ForgeRegistries.ENTITY_TYPES.getValue(id("minecraft:sheep")).create(level);
            sheep.moveTo(9,100,9,0,0); require(level.addFreshEntity(sheep), "Sheep must remain");
            if(!baseline) {
                recipes();
                CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(),true,server.registryAccess());
                System.out.println("TRIM_CREATIVE_PASS");
            }
        } catch(Throwable e) { fail(e); }
    }
    static void recipes() {
        var menu = new AbstractContainerMenu(null, -1) {
            public ItemStack quickMoveStack(Player p, int slot) { return ItemStack.EMPTY; }
            public boolean stillValid(Player p) { return true; }
        };
        for(String plant : List.of("minecraft:kelp","minecraft:green_dye","minecraft:moss_block")) {
            var grid = new TransientCraftingContainer(menu, 3,3);
            grid.setItem(0, new ItemStack(Items.BONE_MEAL)); grid.setItem(3,item(plant));
            grid.setItem(4,PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.WATER)); grid.setItem(5,new ItemStack(Items.STRING));
            var found = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,server.overworld());
            require(found.isPresent() && found.get().getId().equals(id("empirecraft:slime_ball_from_plants")), "Slime alternative " + plant);
            require(found.get().assemble(grid,server.registryAccess()).is(Items.SLIME_BALL), "Slime result");
            grid.setItem(4,PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.AWKWARD));
            require(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,server.overworld()).isEmpty(),"Reject non-water bottle");
            grid.setItem(4,new ItemStack(Items.GLASS_BOTTLE));
            require(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,server.overworld()).isEmpty(),"Reject empty bottle");
            grid.setItem(4,PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.WATER)); grid.setItem(0,ItemStack.EMPTY);
            require(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,server.overworld()).isEmpty(),"Require bone meal");
        }
        System.out.println("TRIM_RECIPE_PASS alternatives=3 water-only=true");
    }
    void tick(TickEvent.ServerTickEvent event) {
        if(event.phase != TickEvent.Phase.END || server == null || ++ticks != 30) return;
        try {
            if(!baseline) {
                var level=server.overworld(); var pos=new BlockPos(4,100,4);
                require(level.getBlockState(pos).is(Blocks.STONE), "Old Chipped block migration");
                require(level.getBlockState(pos.east()).is(Blocks.STONE), "Old Extras block migration");
                require(level.getBlockState(pos.west()).is(Blocks.STONE), "Newly removed netherrack migration");
                require(level.getBlockState(pos.north()).is(Blocks.STONE), "Mixed palette crate migration");
                var chest=(ChestBlockEntity)level.getBlockEntity(pos.south());
                require(chest.getItem(0).getItem()==ForgeRegistries.ITEMS.getValue(id("magistuarmory:steel_buckler")),"Old shield migration");
                require(chest.getItem(1).getItem()==ForgeRegistries.ITEMS.getValue(id("chipped:nether_engraved_glowstone")),"Saved glowstone remains unchanged");
                for(var entity:level.getAllEntities()) {
                    String name=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString();
                    for(var row:removed.getAsJsonArray("mobs")) require(!row.getAsString().equals(name),"Saved forbidden entity " + name);
                }
                System.out.println("TRIM_MIGRATION_PASS blocks=4 shield=1 saved-mobs=15");
            }
            System.out.println("TRIM_SMOKE_PASS baseline="+baseline); server.halt(false);
        } catch(Throwable e) { fail(e); }
    }
    static void fail(Throwable e) { System.err.println("TRIM_SMOKE_FAIL"); e.printStackTrace(); if(server!=null)server.halt(false); }
}

