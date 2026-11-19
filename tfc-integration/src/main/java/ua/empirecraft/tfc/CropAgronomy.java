package ua.empirecraft.tfc;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity;
import net.dries007.tfc.common.blocks.crop.CropHelpers;
import net.dries007.tfc.util.Fertilizer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.tfc.CropProfiles.Profile;
import ua.empirecraft.tfc.RegionalSoilPalette.Soil;

public final class CropAgronomy {
    private static final float NUTRIENT_COST = 0.04f;

    private record Root(BlockPos pos, BlockState state, Soil soil, FarmlandBlockEntity farmland) {
        float nutrient(ServerLevel level, Profile profile) {
            if (farmland != null) return farmland.getNutrient(profile.nutrient());
            var soilData = OneTimeTerrain.rootFertilizer(level, pos);
            return soilData == null ? 0 : soilData.nutrient(pos, state.getBlock(), profile.nutrient());
        }

        void consume(ServerLevel level, Profile profile) {
            if (farmland != null) {
                float value = farmland.getNutrient(profile.nutrient());
                if (value > 0) farmland.setNutrient(profile.nutrient(), Math.max(0, value - NUTRIENT_COST));
                return;
            }
            var soilData = OneTimeTerrain.rootFertilizer(level, pos);
            if (soilData != null && soilData.consume(pos, state.getBlock(), profile.nutrient(), NUTRIENT_COST)) {
                level.getChunkAt(pos).setUnsaved(true);
            }
        }
    }

    private CropAgronomy() {}

    /** Cover game random ticks, including species that never fire Forge's crop events. */
    public static void randomTick(BlockState before, ServerLevel level, BlockPos pos, RandomSource random) {
        var profile = CropProfiles.of(before.getBlock());
        if (profile == null) {
            before.randomTick(level, pos, random);
            return;
        }
        var root = root(level, pos, profile);
        if (root.soil == Soil.OTHER) {
            before.randomTick(level, pos, random);
            return;
        }
        if (!profile.accepts(root.soil)) return;
        float reserve = root.nutrient(level, profile) + (root.soil == Soil.CHERNOZEM ? 0.14f : 0);
        float chance = profile.growthChance(root.soil, reserve);
        if (chance < 1f && random.nextFloat() >= chance) return;
        var above = pos.above();
        BlockState upperBefore = level.getBlockState(above);
        before.randomTick(level, pos, random);
        if (level.getBlockState(pos) != before || level.getBlockState(above) != upperBefore) root.consume(level, profile);
    }

    static void place(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var profile = CropProfiles.of(event.getPlacedBlock().getBlock());
        if (profile == null || profile.accepts(root(level, event.getPos(), profile).soil)) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            player.displayClientMessage(Component.translatable("agriculture.empirecraft_tfc.unsuitable",
                names(profile.preferred()), names(profile.tolerated())).withStyle(ChatFormatting.GOLD), true);
        }
    }

    static void bonemeal(BonemealEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var profile = CropProfiles.of(event.getBlock().getBlock());
        if (profile != null && !profile.accepts(root(level, event.getPos(), profile).soil)) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getUseBlock() == Event.Result.DENY
                || event.getUseItem() == Event.Result.DENY) return;
        var player = event.getEntity();
        var stack = event.getItemStack();
        boolean inspect = player.isShiftKeyDown() && stack.isEmpty();
        if (!inspect && stack.isEmpty()) return;
        Fertilizer fertilizer = inspect ? null : Fertilizer.get(stack);
        if (!inspect && fertilizer == null) return;

        var pos = event.getPos();
        var profile = CropProfiles.of(level.getBlockState(pos).getBlock());
        boolean clickedPlant = profile != null;
        if (!clickedPlant) {
            pos = pos.above();
            profile = CropProfiles.of(level.getBlockState(pos).getBlock());
        }
        if (profile == null) return;
        var root = root(level, pos, profile);
        if (inspect) {
            var nutrient = Component.translatable("agriculture.empirecraft_tfc." + profile.nutrient().name().toLowerCase(Locale.ROOT));
            player.displayClientMessage(Component.translatable("agriculture.empirecraft_tfc.inspect",
                name(root.soil), names(profile.preferred()), names(profile.tolerated()), nutrient,
                Math.round(root.nutrient(level, profile) * 100)), true);
            return;
        }
        if (root.soil == Soil.OTHER || !profile.accepts(root.soil) || !level.mayInteract(player, root.pos)) return;

        // Fertilizing a crop on another block should never modify an adjacent plot through its protection.
        if (root.farmland != null) {
            if (clickedPlant && root.pos.equals(event.getPos().below())
                    && CropHelpers.useFertilizer(level, player, event.getHand(), root.pos)) handled(event);
            return;
        }
        if (!clickedPlant && !event.getPos().equals(root.pos)) return;
        if (clickedPlant && !root.pos.equals(event.getPos().below())) return;
        var soilData = OneTimeTerrain.rootFertilizer(level, root.pos);
        if (soilData == null || !soilData.apply(root.pos, root.state.getBlock(), fertilizer)) return;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.getChunkAt(root.pos).setUnsaved(true);
        level.levelEvent(1505, pos, 0);
        handled(event);
    }

    static void breakSoil(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var soilData = OneTimeTerrain.rootFertilizer(level, event.getPos());
        if (soilData != null && soilData.remove(event.getPos())) level.getChunkAt(event.getPos()).setUnsaved(true);
    }

    private static void handled(PlayerInteractEvent.RightClickBlock event) {
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static Root root(LevelReader level, BlockPos crop, Profile profile) {
        BlockPos pos = crop.below();
        BlockState state = level.getBlockState(pos);
        // Multi-block rice, tomato, sunflower, grape and coffee stages draw from the same root.
        for (int depth = 0; depth < 6 && CropProfiles.of(state.getBlock()) == profile; depth++) {
            pos = pos.below();
            state = level.getBlockState(pos);
        }
        var farmland = level.getBlockEntity(pos) instanceof FarmlandBlockEntity entity ? entity : null;
        return new Root(pos, state, soil(state), farmland);
    }

    private static Soil soil(BlockState state) {
        if (state.is(BlockTags.LOGS)) return Soil.WOOD;
        if (state.is(BlockTags.SAND)) return Soil.SAND;
        if (state.is(Blocks.CLAY) || state.is(Blocks.MUD)) return Soil.CLAY;
        if (state.is(Blocks.PODZOL)) return Soil.SANDY_LOAM;
        if (state.is(Blocks.MYCELIUM)) return Soil.PEAT;
        if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.FARMLAND)) return Soil.LOAM;

        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return Soil.OTHER;
        String path = id.getPath();
        if (id.getNamespace().equals("tfc") || id.getNamespace().equals(Integration.ID)) {
            if (path.startsWith("clay/") || path.startsWith("clay_grass/") || path.startsWith("mud/")
                    || path.contains("kaolin_clay")) return Soil.CLAY;
            if (path.equals("peat") || path.equals("peat_grass")) return Soil.PEAT;
            if (path.startsWith("sand/")) return Soil.SAND;
            if (path.startsWith("dirt/") || path.startsWith("grass/") || path.startsWith("farmland/")
                    || path.startsWith("rooted_dirt/")) {
                return switch (path.substring(path.indexOf('/') + 1)) {
                    case "chernozem" -> Soil.CHERNOZEM;
                    case "sandy_loam" -> Soil.SANDY_LOAM;
                    case "silty_loam" -> Soil.SILTY_LOAM;
                    case "silt" -> Soil.SILT;
                    default -> Soil.LOAM;
                };
            }
        }
        if (id.getNamespace().equals("farmersdelight") && (path.equals("rich_soil") || path.equals("rich_soil_farmland"))) {
            return Soil.CHERNOZEM;
        }
        if (id.getNamespace().equals("vinery") && path.equals("grapevine_pot")) return Soil.SANDY_LOAM;
        if (id.getNamespace().equals("supplementaries") && path.contains("planter")) return Soil.LOAM;
        if (state.is(BlockTags.DIRT)) return Soil.LOAM;
        return Soil.OTHER;
    }

    private static Component name(Soil soil) {
        return Component.translatable("agriculture.empirecraft_tfc.soil." + soil.id());
    }

    private static Component names(Set<Soil> soils) {
        var values = new ArrayList<>(soils);
        values.sort(null);
        var result = Component.empty();
        for (var soil : values) {
            if (!result.getSiblings().isEmpty()) result.append(", ");
            result.append(name(soil));
        }
        return result;
    }
}
