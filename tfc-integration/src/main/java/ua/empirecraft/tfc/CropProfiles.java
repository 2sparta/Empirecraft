package ua.empirecraft.tfc;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonParser;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.tfc.RegionalSoilPalette.Soil;

final class CropProfiles {
    record Profile(NutrientType nutrient, Set<Soil> preferred, Set<Soil> tolerated) {
        boolean accepts(Soil soil) {
            return soil == Soil.OTHER || preferred.contains(soil) || tolerated.contains(soil);
        }

        float growthChance(Soil soil, float nutrientLevel) {
            if (!accepts(soil)) return 0;
            float soilFactor = preferred.contains(soil) || soil == Soil.OTHER ? 1f : 0.72f;
            return soilFactor * (0.58f + 0.42f * Math.min(1f, nutrientLevel * 2.5f));
        }
    }

    private static final Profile FIELD = new Profile(NutrientType.NITROGEN,
        Set.of(Soil.CHERNOZEM, Soil.LOAM, Soil.SILTY_LOAM), Set.of(Soil.SANDY_LOAM, Soil.SILT));
    private static final Profile ORCHARD = new Profile(NutrientType.PHOSPHOROUS,
        Set.of(Soil.LOAM, Soil.SILTY_LOAM), Set.of(Soil.CHERNOZEM, Soil.SANDY_LOAM, Soil.SILT));
    private static final Map<ResourceLocation, Profile> PROFILES = read();
    private static final Map<Block, Profile> CACHE = new java.util.IdentityHashMap<>();

    private CropProfiles() {}

    static Profile of(Block block) {
        // Consumer paths run on the server game thread, including single-player.
        if (!CACHE.containsKey(block)) CACHE.put(block, resolve(block));
        return CACHE.get(block);
    }

    private static Profile resolve(Block block) {
        var id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null || id.getNamespace().equals("tfc")) return null;
        Profile profile = PROFILES.get(id);
        if (profile != null) return profile;
        if (block instanceof CropBlock) return FIELD;
        if (block instanceof SaplingBlock || (!id.getNamespace().equals("minecraft")
                && block instanceof BushBlock && block instanceof BonemealableBlock)) return ORCHARD;
        return null;
    }

    private static Map<ResourceLocation, Profile> read() {
        var stream = CropProfiles.class.getResourceAsStream("/empirecraft-crop-profiles.json");
        if (stream == null) throw new IllegalStateException("Missing crop profiles");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<ResourceLocation, Profile> result = new HashMap<>();
            for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                var row = entry.getAsJsonObject();
                Set<Soil> preferred = EnumSet.noneOf(Soil.class);
                Set<Soil> tolerated = EnumSet.noneOf(Soil.class);
                row.getAsJsonArray("preferred").forEach(value -> preferred.add(Soil.valueOf(value.getAsString())));
                row.getAsJsonArray("tolerated").forEach(value -> tolerated.add(Soil.valueOf(value.getAsString())));
                var profile = new Profile(NutrientType.valueOf(row.get("nutrient").getAsString()),
                    Set.copyOf(preferred), Set.copyOf(tolerated));
                for (var value : row.getAsJsonArray("blocks")) {
                    var id = new ResourceLocation(value.getAsString());
                    if (result.put(id, profile) != null) throw new IllegalArgumentException("Duplicate crop " + id);
                }
            }
            return Map.copyOf(result);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not read crop profiles", error);
        }
    }
}
