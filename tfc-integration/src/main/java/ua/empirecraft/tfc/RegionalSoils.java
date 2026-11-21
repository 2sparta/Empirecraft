package ua.empirecraft.tfc;

import java.util.Map;
import java.util.function.Predicate;
import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.ModList;
import ua.empirecraft.earth.EarthMod;
import ua.empirecraft.tfc.RegionalSoilPalette.Soil;

final class RegionalSoils {
    static final int VERSION = 2;
    private static final Map<Block, SoilBlockType> VANILLA = Map.of(
        Blocks.DIRT, SoilBlockType.DIRT,
        Blocks.GRASS_BLOCK, SoilBlockType.GRASS,
        Blocks.COARSE_DIRT, SoilBlockType.DIRT,
        Blocks.PODZOL, SoilBlockType.DIRT,
        Blocks.MYCELIUM, SoilBlockType.DIRT,
        Blocks.ROOTED_DIRT, SoilBlockType.ROOTED_DIRT,
        Blocks.DIRT_PATH, SoilBlockType.GRASS_PATH,
        Blocks.CLAY, SoilBlockType.CLAY,
        Blocks.MUD, SoilBlockType.MUD,
        Blocks.MUDDY_MANGROVE_ROOTS, SoilBlockType.MUDDY_ROOTS
    );

    private RegionalSoils() {}

    static void convert(ServerLevel level, LevelChunk chunk, Predicate<BlockPos> wilderness) {
        // Revisit legacy black dirt and turf only in enabled Earth dimensions.
        // Farmland, paths and other crafted variants retain their original blocks.
        boolean correctLegacy = ModList.get().isLoaded("empirecraft_earth") && EarthSampler.enabled(level);
        Block blackDirt = Chernozem.block(SoilBlockType.DIRT);
        Block blackGrass = Chernozem.block(SoilBlockType.GRASS);
        var sections = chunk.getSections();
        boolean[] candidates = new boolean[sections.length];
        boolean any = false;
        for (int index = 0; index < sections.length; ++index) {
            candidates[index] = sections[index].maybeHas(state -> VANILLA.containsKey(state.getBlock())
                || correctLegacy && (state.is(blackDirt) || state.is(blackGrass)));
            any |= candidates[index];
        }
        if (!any) return;

        var sample = sampler(level);
        int ox = chunk.getPos().getMinBlockX();
        int oz = chunk.getPos().getMinBlockZ();
        var pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = ox + lx, z = oz + lz;
            int surface = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz);
            var top = new BlockPos(x, surface, z);
            if (!level.getWorldBorder().isWithinBounds(top) || !wilderness.test(top.atY(64))) continue;
            var biome = chunk.getNoiseBiome(x >> 2, surface >> 2, z >> 2);
            boolean saturated = biome.is(BiomeTags.IS_RIVER)
                || (!biome.is(BiomeTags.IS_OCEAN) && chunk.getFluidState(top.above()).is(FluidTags.WATER));
            Soil soil = sample.at(top, saturated, biome.value().getBaseTemperature(), biome.value().hasPrecipitation());
            for (int section = 0; section < sections.length; ++section) {
                if (!candidates[section]) continue;
                int bottom = level.getMinBuildHeight() + section * 16;
                for (int dy = 0; dy < 16; ++dy) {
                    pos.set(x, bottom + dy, z);
                    BlockState before = sections[section].getBlockState(lx, dy, lz);
                    SoilBlockType type = VANILLA.get(before.getBlock());
                    if (type == null && correctLegacy && soil != Soil.CHERNOZEM && pos.getY() >= surface - 4
                            && pos.getY() <= surface) {
                        if (before.is(blackDirt)) type = SoilBlockType.DIRT;
                        if (before.is(blackGrass)) type = SoilBlockType.GRASS;
                    }
                    if (type == null || chunk.getBlockEntity(pos) != null) continue;
                    Soil texture = before.is(Blocks.PODZOL) ? Soil.SANDY_LOAM : soil;
                    if (texture == Soil.CHERNOZEM && (type == SoilBlockType.CLAY || type == SoilBlockType.MUD
                            || type == SoilBlockType.MUDDY_ROOTS)) texture = Soil.SILTY_LOAM;
                    var after = before.is(Blocks.MYCELIUM)
                        ? net.dries007.tfc.common.blocks.TFCBlocks.PEAT.get().defaultBlockState()
                        : Chernozem.block(type, texture).withPropertiesOf(before);
                    level.setBlock(pos, after, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    static Soil at(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos).value();
        return sampler(level).at(pos, level.getFluidState(pos.above()).is(FluidTags.WATER),
            biome.getBaseTemperature(), biome.hasPrecipitation());
    }

    private interface Sampler {
        Soil at(BlockPos pos, boolean wet, float biomeTemperature, boolean precipitation);
    }

    private static Sampler sampler(ServerLevel level) {
        long seed = level.getSeed() ^ ((long)level.dimension().location().hashCode() << 32);
        if (ModList.get().isLoaded("empirecraft_earth")) {
            var earth = EarthSampler.create(level, seed);
            if (earth != null) return earth;
        }
        return (pos, wet, temperature, precipitation) -> RegionalSoilPalette.select(seed, pos.getX(), pos.getZ(),
            "", temperature < 0.25f ? "taiga" : "", temperature * 20, precipitation ? 750 : 180,
            wet, pos.getY() > level.getSeaLevel() + 65);
    }

    private static final class EarthSampler {
        static boolean enabled(ServerLevel level) {
            return EarthMod.enabled(level);
        }

        static Sampler create(ServerLevel level, long seed) {
            if (!EarthMod.enabled(level)) return null;
            var geography = EarthMod.data;
            return (pos, wet, temperature, precipitation) -> {
                var point = geography.coordinates(pos.getX(), pos.getZ());
                var ecology = geography.at("ecology", point);
                var climate = geography.climate(point);
                String soilClass = ecology == null ? "" : ecology.text("soil_class", "");
                String zone = ecology == null ? geography.zone(point) : ecology.text("zone", ecology.id());
                return RegionalSoilPalette.select(seed, pos.getX(), pos.getZ(), soilClass, zone, climate.mean(), climate.rain(),
                    wet, (pos.getY() - geography.seaLevel) * geography.metresPerBlock > 130);
            };
        }
    }
}
