package guardtest;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.dries007.tfc.util.Fertilizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.earth.EarthMod;
import ua.empirecraft.tfc.Chernozem;
import ua.empirecraft.tfc.OneTimeTerrain;
import ua.empirecraft.tfc.RegionalSoilPalette;
import ua.empirecraft.tfc.RegionalSoilPalette.Soil;

@Mod("empirecraft_agriculture_test")
public final class AgricultureChecks {
    public AgricultureChecks() {
        MinecraftForge.EVENT_BUS.addListener(AgricultureChecks::check);
    }

    private static void check(ServerStartedEvent event) {
        var server = event.getServer();
        try {
            var level = server.overworld();
            var geo = EarthMod.data;
            for (String[] sample : new String[][] {
                    {"35", "48.5", "CHERNOZEM"}, {"31", "51.5", "SANDY_LOAM"}, {"24.4", "48.2", "SILTY_LOAM"},
                    {"-102", "42", "CHERNOZEM"}, {"-59", "-34", "CHERNOZEM"}, {"15", "24", "SANDY_LOAM"},
                    {"-60", "-5", "SILTY_LOAM"}, {"20", "0", "SILTY_LOAM"}, {"90", "62", "SANDY_LOAM"}
            }) {
                double lon = Double.parseDouble(sample[0]), lat = Double.parseDouble(sample[1]);
                var point = new ua.empirecraft.earth.Geography.Point(lon, lat);
                var climate = geo.climate(point);
                int expected = 0;
                for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) {
                    var result = RegionalSoilPalette.select(9054, (int)geo.x(lon)+x, (int)geo.z(lat)+z,
                        geo.soilClass(point), geo.zone(point), climate.mean(), climate.rain(), false, false);
                    if (result == Soil.valueOf(sample[2])) expected++;
                }
                require(expected > 7000, "Predominant soil at " + lon + "," + lat + ": " + expected);
            }

            // The runtime must resolve every advertised crop, including transformed/rope stages.
            var profileStream = Chernozem.class.getResourceAsStream("/empirecraft-crop-profiles.json");
            require(profileStream != null, "crop profile resource");
            try (var reader = new InputStreamReader(profileStream, StandardCharsets.UTF_8)) {
                for (var group : JsonParser.parseReader(reader).getAsJsonArray()) {
                    for (var crop : group.getAsJsonObject().getAsJsonArray("blocks")) {
                        require(ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(crop.getAsString())),
                            "Missing crop registry ID " + crop.getAsString());
                    }
                }
            }

            var base = new BlockPos((int)geo.x(35), 65, (int)geo.z(48.5));
            var chunk = level.getChunkAt(base);
            int bottomX = chunk.getPos().getMinBlockX(), bottomZ = chunk.getPos().getMinBlockZ();
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                var pos = new BlockPos(bottomX + x, 65, bottomZ + z);
                level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(pos.below(13), Blocks.ROOTED_DIRT.defaultBlockState());
            }
            require(OneTimeTerrain.process(level, chunk), "First regional conversion");
            require(!OneTimeTerrain.process(level, chunk), "One-time chunk conversion");
            int black = 0;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                var pos = new BlockPos(bottomX + x, 65, bottomZ + z);
                require(!level.getBlockState(pos).is(Blocks.GRASS_BLOCK), "Surface grass migrated");
                require(!level.getBlockState(pos.below()).is(Blocks.DIRT), "Upper dirt migrated");
                require(!level.getBlockState(pos.below(13)).is(Blocks.ROOTED_DIRT), "Deep dirt migrated");
                if (level.getBlockState(pos).is(Chernozem.block(SoilBlockType.GRASS))) black++;
            }
            require(black > 110, "Chernozem in Ukrainian steppe sample: " + black);
            var field = Chernozem.block(SoilBlockType.FARMLAND);
            var potatoes = Blocks.POTATOES.defaultBlockState();
            level.setBlockAndUpdate(base, field.defaultBlockState());
            require(level.getBlockEntity(base) instanceof FarmlandBlockEntity, "Chernozem has native farmland entity");
            var farmland = (FarmlandBlockEntity)level.getBlockEntity(base);
            require(farmland.getType().isValid(level.getBlockState(base)), "Farmland type accepts chernozem on chunk load");
            require(potatoes.canSurvive(level, base.above()), "Potatoes can grow on chernozem farmland");
            level.setBlockAndUpdate(base.above(), potatoes);

            var compost = ForgeRegistries.ITEMS.getValue(new ResourceLocation("farmersdelight:organic_compost"));
            require(Fertilizer.get(new ItemStack(compost)) != null, "Organic compost loads as TFC fertilizer");
            var player = FakePlayerFactory.getMinecraft(level);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(compost));
            var pos = base.above();
            var click = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            MinecraftForge.EVENT_BUS.post(click);
            require(click.isCanceled() && farmland.getNutrient(NutrientType.POTASSIUM) > 0, "Fertilizer applied to potato roots");

            System.out.println("AGRICULTURE_RUNTIME_PASS regional_Mohist_migration farmland_fertilizer profile_registry");
        } catch (Throwable failure) {
            System.err.println("AGRICULTURE_RUNTIME_FAIL");
            failure.printStackTrace();
        } finally {
            server.halt(false);
        }
    }

    private static void require(boolean expected, String message) {
        if (!expected) throw new AssertionError(message);
    }
}
