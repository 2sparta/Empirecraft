package guardtest;

import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.function.Predicate;
import ua.empirecraft.tfc.Chernozem;
import ua.empirecraft.tfc.OneTimeTerrain;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.earth.EarthMod;

@Mod("empirecraft_soil_test")
@Mod.EventBusSubscriber(modid = "empirecraft_soil_test")
public final class SoilMigrationProbe {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void run(ServerStartedEvent event) {
        var server = event.getServer();
        server.execute(() -> {
            try {
                var level = server.overworld();
                var blackGrass = Chernozem.block(SoilBlockType.GRASS);
                var blackDirt = Chernozem.block(SoilBlockType.DIRT);
                var field = Chernozem.block(SoilBlockType.FARMLAND);
                var convert = Class.forName("ua.empirecraft.tfc.RegionalSoils")
                    .getDeclaredMethod("convert", ServerLevel.class, LevelChunk.class, Predicate.class, int.class);
                convert.setAccessible(true);
                var cap = CapabilityManager.get(new CapabilityToken<OneTimeTerrain.State>() {});
                for (var sample : new Object[][] {
                    {new BlockPos(263, 65, -9536), "western_europe_lowlands"},
                    {new BlockPos(13627, 71, -7646), "amudarya_alluvium"},
                    {new BlockPos(1871, 63, -11089), "jutland_sandy_plains"},
                }) {
                    var pos = (BlockPos) sample[0];
                    var chunk = level.getChunkAt(pos);
                    require(EarthMod.data.at("ecology", EarthMod.data.coordinates(pos.getX(), pos.getZ())).id().equals(sample[1]),
                        "Server merged the region into its existing JSON");
                    var state = chunk.getCapability(cap).orElseThrow(IllegalStateException::new);
                    OneTimeTerrain.process(level, chunk);
                    int completedMaterials = state.materials;
                    var saved = new CompoundTag();
                    saved.putBoolean("Ores", true);
                    saved.putBoolean("Stones", true);
                    saved.putInt("Materials", completedMaterials);
                    saved.putInt("Soils", 1);
                    state.deserializeNBT(saved);
                    level.setBlockAndUpdate(pos, blackGrass.defaultBlockState());
                    for (int d = 1; d <= 6; d++) level.setBlockAndUpdate(pos.below(d), blackDirt.defaultBlockState());
                    var cultivated = pos.east();
                    level.setBlockAndUpdate(cultivated, field.defaultBlockState());
                    var structure = pos.west();
                    level.setBlockAndUpdate(structure, Blocks.STONE_BRICKS.defaultBlockState());
                    level.setBlockAndUpdate(structure.below(), blackDirt.defaultBlockState());

                    convert.invoke(null, level, chunk, (Predicate<BlockPos>) ignored -> false, 1);
                    require(level.getBlockState(pos).is(blackGrass), "Protected column was not modified");
                    require(OneTimeTerrain.process(level, chunk), "Legacy chunk revisited");
                    require(!OneTimeTerrain.process(level, chunk), "One-time version gate");
                    require(state.soils == 3 && state.ores && state.stones
                        && state.materials == completedMaterials, "Independent persisted generation flags");
                    require(!level.getBlockState(pos).is(blackGrass), "Surface changed at " + pos);
                    require(!level.getBlockState(pos.below(4)).is(blackDirt), "Upper soil changed");
                    require(level.getBlockState(pos.below(5)).is(blackDirt), "Deep soil retained");
                    require(level.getBlockState(cultivated).is(field), "Farm retained");
                    require(level.getBlockState(structure.below()).is(blackDirt), "Soil under structure retained");
                }
                System.out.println("SOIL_MIGRATION_PASS positions=3 repeated_pass=false soil_version=" + 3);
            } catch (Throwable error) {
                System.out.println("SOIL_MIGRATION_FAIL " + error);
                error.printStackTrace();
            } finally {
                server.halt(false);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
