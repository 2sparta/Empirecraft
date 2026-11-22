package ua.empirecraft.tfc;

import java.util.HashMap;
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

    static void convert(ServerLevel level, LevelChunk chunk, Predicate<BlockPos> wilderness, int previousVersion) {
        // For previously converted chunks, only revisit the contiguous surface profile in Earth.
        boolean convertVanilla = previousVersion == 0;
        Sampler earth = ModList.get().isLoaded("empirecraft_earth") ? EarthSampler.create(level, seed(level)) : null;
        if (!convertVanilla && earth == null) return;
        var sample = earth != null ? earth : vanillaSampler(level);
        var natural = earth != null ? SurfaceSoils.BLOCKS : Map.<Block, SoilBlockType>of();
        var sections = chunk.getSections();
        boolean[] candidates = new boolean[sections.length];
        boolean anyVanilla = false;
        if (convertVanilla) {
            for (int index = 0; index < sections.length; ++index) {
                candidates[index] = sections[index].maybeHas(state -> VANILLA.containsKey(state.getBlock()));
                anyVanilla |= candidates[index];
            }
        }
        if (!anyVanilla && natural.isEmpty()) return;

        int ox = chunk.getPos().getMinBlockX();
        int oz = chunk.getPos().getMinBlockZ();
        var pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = ox + lx, z = oz + lz;
            int surface = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz);
            var top = new BlockPos(x, surface, z);
            boolean surfaceSoil = natural.containsKey(chunk.getBlockState(top).getBlock());
            if (!anyVanilla && !surfaceSoil) continue;
            if (!level.getWorldBorder().isWithinBounds(top) || !wilderness.test(top.atY(64))) continue;
            var biome = chunk.getNoiseBiome(x >> 2, surface >> 2, z >> 2);
            boolean saturated = biome.is(BiomeTags.IS_RIVER)
                || (!biome.is(BiomeTags.IS_OCEAN) && chunk.getFluidState(top.above()).is(FluidTags.WATER));
            Soil soil = sample.at(top, saturated, biome.value().getBaseTemperature(), biome.value().hasPrecipitation());
            if (surfaceSoil) {
                for (int y = surface; y >= Math.max(level.getMinBuildHeight(), surface - 4); --y) {
                    pos.set(x, y, z);
                    BlockState before = chunk.getBlockState(pos);
                    SoilBlockType type = natural.get(before.getBlock());
                    if (type == null || chunk.getBlockEntity(pos) != null) break;
                    replace(level, pos, before, Chernozem.block(type, soil).withPropertiesOf(before));
                }
            }
            if (!anyVanilla) continue;
            for (int section = 0; section < sections.length; ++section) {
                if (!candidates[section]) continue;
                int bottom = level.getMinBuildHeight() + section * 16;
                for (int dy = 0; dy < 16; ++dy) {
                    pos.set(x, bottom + dy, z);
                    BlockState before = sections[section].getBlockState(lx, dy, lz);
                    SoilBlockType type = VANILLA.get(before.getBlock());
                    if (type == null || chunk.getBlockEntity(pos) != null) continue;
                    Soil texture = before.is(Blocks.PODZOL) ? Soil.SANDY_LOAM : soil;
                    if (texture == Soil.CHERNOZEM && (type == SoilBlockType.CLAY || type == SoilBlockType.MUD
                            || type == SoilBlockType.MUDDY_ROOTS)) texture = Soil.SILTY_LOAM;
                    var after = before.is(Blocks.MYCELIUM)
                        ? net.dries007.tfc.common.blocks.TFCBlocks.PEAT.get().defaultBlockState()
                        : Chernozem.block(type, texture).withPropertiesOf(before);
                    replace(level, pos, before, after);
                }
            }
        }
    }

    private static void replace(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (before != after) level.setBlock(pos, after, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static final class SurfaceSoils {
        static final Map<Block, SoilBlockType> BLOCKS = blocks();

        private static Map<Block, SoilBlockType> blocks() {
            var soils = new HashMap<Block, SoilBlockType>();
            soils.put(Blocks.DIRT, SoilBlockType.DIRT);
            soils.put(Blocks.GRASS_BLOCK, SoilBlockType.GRASS);
            for (var type : new SoilBlockType[]{SoilBlockType.DIRT, SoilBlockType.GRASS}) {
                soils.put(Chernozem.block(type), type);
                for (var texture : SoilBlockType.Variant.values()) soils.put(texture.getBlock(type).get(), type);
            }
            return Map.copyOf(soils);
        }
    }

    static Soil at(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos).value();
        long seed = seed(level);
        var earth = ModList.get().isLoaded("empirecraft_earth") ? EarthSampler.create(level, seed) : null;
        return (earth != null ? earth : vanillaSampler(level)).at(pos, level.getFluidState(pos.above()).is(FluidTags.WATER),
            biome.getBaseTemperature(), biome.hasPrecipitation());
    }

    private interface Sampler {
        Soil at(BlockPos pos, boolean wet, float biomeTemperature, boolean precipitation);
    }

    private static long seed(ServerLevel level) {
        return level.getSeed() ^ ((long) level.dimension().location().hashCode() << 32);
    }

    private static Sampler vanillaSampler(ServerLevel level) {
        long seed = seed(level);
        return (pos, wet, temperature, precipitation) -> RegionalSoilPalette.select(seed, pos.getX(), pos.getZ(),
            "", temperature < 0.25f ? "taiga" : "", temperature * 20, precipitation ? 750 : 180,
            wet, pos.getY() > level.getSeaLevel() + 65);
    }

    private static final class EarthSampler {
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
