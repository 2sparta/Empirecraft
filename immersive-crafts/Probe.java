package ua.empirecraft.immersivecrafts.test;

import com.otectus.immersivejewelry.blockentity.LapidaryWheelBlockEntity;
import com.otectus.immersivejewelry.workstation.WheelOperations;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherData;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.station.BenchStationLogic;
import com.otectus.immersivetanning.station.RackStationLogic;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import milkucha.trmt.block.ErodedSandBlock;
import ua.empirecraft.trmt.RegionalTrails;
import ua.empirecraft.trmt.RememberedPath;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("empirecraft_immersive_probe")
public final class Probe {
    public Probe() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, Probe::checkWorld);
    }

    private static ResourceLocation id(String id) { return new ResourceLocation(id); }
    private static Item item(String id) { return ForgeRegistries.ITEMS.getValue(id(id)); }
    private static ItemStack stack(String id) { return new ItemStack(item(id)); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkWorld(ServerStartedEvent event) {
        var server = event.getServer();
        try {
            var level = server.overworld();
            var player = FakePlayerFactory.getMinecraft(level);
            BlockPos position = new BlockPos(level.getSharedSpawnPos().getX(), 110, level.getSharedSpawnPos().getZ());
            String[] gems = {"ruby", "sapphire", "topaz", "opal", "pyrite"};
            for (int index = 0; index < gems.length; index++) {
                String gem = gems[index];
                BlockPos pos = position.offset(index * 4, 0, 0);
                var block = ForgeRegistries.BLOCKS.getValue(id("immersive_jewelry:lapidary_wheel"));
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                var wheel = (LapidaryWheelBlockEntity) level.getBlockEntity(pos);
                var source = stack("tfc:ore/" + gem);
                require(WheelOperations.mount(player, wheel, source), "Wheel must mount " + gem);
                require(WheelOperations.stoneData(wheel).gem().equals(id("immersive_jewelry:" + gem)), "Wrong gem identity " + gem);
                require(source.isEmpty(), "Wheel did not consume the source");
            }
            String[][] hides = {{"tfc:large_raw_hide", "large"}, {"tfc:small_scraped_hide", "small"},
                                {"naturalist:fur", "small"}, {"alexsmobs:kangaroo_hide", "medium"}};
            for (int index = 0; index < hides.length; index++) {
                BlockPos pos = position.offset(index * 4, 0, 6);
                level.setBlockAndUpdate(pos, ForgeRegistries.BLOCKS.getValue(id("immersive_tanning:tanners_rack")).defaultBlockState());
                var rack = (TannersRackBlockEntity) level.getBlockEntity(pos);
                var hide = stack(hides[index][0]);
                require(RackStationLogic.mount(player, rack, hide), "Rack must mount " + hides[index][0]);
                require(hide.isEmpty(), "Rack did not consume hide");
            }
            var manager = server.getRecipeManager();
            for (String legacy : List.of("tfc:ruby_cut", "tfc:barrel/tannin", "tfc:barrel/small_leather",
                    "tfc:scraping/medium_soaked_hide", "tfc:leather_knapping/boots", "minecraft:leather_boots")) {
                require(manager.byKey(id(legacy)).isEmpty(), "Old route remains: " + legacy);
            }
            require(manager.byKey(id("tfc:crafting/gem_saw")).isPresent(), "Glassworking gem saw");
            require(manager.byKey(id("tfc:quern/ruby")).isPresent(), "Glass color powder");
            require(manager.byKey(id("tfc:crafting/parchment")).isPresent(), "Parchment remains obtainable");
            require(manager.getRecipes().stream().anyMatch(recipe -> recipe.getType().toString().contains("assembly")),
                    "Metal armory recipes remain");

            var boots = TanningData.current().recipesProducing(item("magistuarmory:gambeson_boots")).stream().findFirst().orElseThrow();
            require(manager.byKey(boots.getId()).orElseThrow().getClass() == boots.getClass(), "Armory replaced the sewing recipe");
            require(TanningData.current().recipesProducing(item("thirtyyearswar:baroque_buff_coat")).size() > 0, "Buff coat bench route");
            require(LeatherData.get(stack("immersive_tanning:binding_leather")).isEmpty(), "Binding strips cannot replace a full sheet");
            require(LeatherData.get(stack("medieval_boomsticks:finished_leather")).orElseThrow().finish() == Finish.HARNESS,
                    "Finished leather from another mod");
            var benchPos = position.offset(0, 0, 12);
            level.setBlockAndUpdate(benchPos, ForgeRegistries.BLOCKS.getValue(id("immersive_tanning:leatherworkers_bench")).defaultBlockState());
            var bench = (LeatherworkersBenchBlockEntity) level.getBlockEntity(benchPos);
            player.getInventory().add(new ItemStack(item("medieval_boomsticks:finished_leather"), boots.leather()));
            boots.auxiliary().forEach(aux -> player.getInventory().add(aux.ingredient().getItems()[0].copyWithCount(aux.count())));
            require(BenchStationLogic.prepare(player, bench, boots.getId(), Finish.HARNESS, null),
                    "Actual bench must start Epic Knights boots from other-mod leather");
            require(bench.job() != null, "Sewing workpiece started");
            var vanillaSand = ForgeRegistries.BLOCKS.getValue(id("trmt:eroded_sand")).defaultBlockState();
            int sands = 0;
            for (var entry : RegionalTrails.PATHS.entrySet()) {
                var sourceId = ForgeRegistries.BLOCKS.getKey(entry.getKey());
                var path = entry.getValue();
                if (!sourceId.getNamespace().equals("tfc") || !path.kind.equals("sand")) continue;
                BlockPos pos = position.offset(sands++ * 2, 0, 18);
                level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(pos, entry.getKey().defaultBlockState());
                level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
                for (int stage = 0; stage < 5; stage++) {
                    RegionalTrails.step(level, pos, 1000, Direction.NORTH);
                    var state = level.getBlockState(pos);
                    var vanilla = vanillaSand.setValue(ErodedSandBlock.STAGE, stage);
                    require(state.is(path) && state.getValue(RememberedPath.WEAR) == stage, "Sand stage " + stage);
                    require(state.getCollisionShape(level, pos).bounds().equals(
                            vanilla.getCollisionShape(level, pos).bounds()), "Vanilla collision parity at " + stage);
                    require(state.getShape(level, pos).bounds().equals(vanilla.getShape(level, pos).bounds()),
                            "Vanilla outline parity at " + stage);
                }
            }
            require(sands == 7, "All seven TFC sand colors");
            System.out.println("IMMERSIVE_CRAFTS_PASS gems=5 mounts=4 sewing=true legacyDisabled=true sands=" + sands);
        } catch (Throwable error) {
            System.out.println("IMMERSIVE_CRAFTS_FAIL");
            error.printStackTrace();
        } finally {
            server.halt(false);
        }
    }
}
