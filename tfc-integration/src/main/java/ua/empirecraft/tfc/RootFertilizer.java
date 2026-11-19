package ua.empirecraft.tfc;

import java.util.HashMap;
import java.util.Map;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
import net.dries007.tfc.util.Fertilizer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/** Stores amendments for perennial and wetland roots on blocks without a TFC farmland entity. */
final class RootFertilizer {
    private record Amendment(String block, float[] nutrients) {}
    private final Map<Long, Amendment> amendments = new HashMap<>();

    float nutrient(BlockPos root, Block soil, NutrientType nutrient) {
        var amendment = matching(root, soil);
        return amendment == null ? 0 : amendment.nutrients[nutrient.ordinal()];
    }

    boolean apply(BlockPos root, Block soil, Fertilizer fertilizer) {
        var old = matching(root, soil);
        float[] nutrients = old == null ? new float[NutrientType.values().length] : old.nutrients;
        boolean changed = false;
        for (var nutrient : NutrientType.values()) {
            int i = nutrient.ordinal();
            float next = Math.min(1, nutrients[i] + fertilizer.getNutrient(nutrient));
            changed |= next > nutrients[i];
            nutrients[i] = next;
        }
        if (changed && old == null) amendments.put(root.asLong(), new Amendment(id(soil), nutrients));
        return changed;
    }

    boolean consume(BlockPos root, Block soil, NutrientType nutrient, float cost) {
        var amendment = matching(root, soil);
        if (amendment == null || amendment.nutrients[nutrient.ordinal()] == 0) return false;
        float[] nutrients = amendment.nutrients;
        nutrients[nutrient.ordinal()] = Math.max(0, nutrients[nutrient.ordinal()] - cost);
        for (float value : nutrients) if (value > 0) return true;
        amendments.remove(root.asLong());
        return true;
    }

    boolean remove(BlockPos root) {
        return amendments.remove(root.asLong()) != null;
    }

    private Amendment matching(BlockPos root, Block soil) {
        var amendment = amendments.get(root.asLong());
        return amendment != null && amendment.block.equals(id(soil)) ? amendment : null;
    }

    private static String id(Block soil) {
        return ForgeRegistries.BLOCKS.getKey(soil).toString();
    }

    ListTag save() {
        var list = new ListTag();
        amendments.forEach((position, amendment) -> {
            var row = new CompoundTag();
            row.putLong("Pos", position);
            row.putString("Block", amendment.block);
            for (var nutrient : NutrientType.values()) {
                row.putFloat(nutrient.name(), amendment.nutrients[nutrient.ordinal()]);
            }
            list.add(row);
        });
        return list;
    }

    void load(CompoundTag tag) {
        amendments.clear();
        for (Tag entry : tag.getList("RootFertilizer", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag)entry;
            var nutrients = new float[NutrientType.values().length];
            for (var nutrient : NutrientType.values()) {
                float value = row.getFloat(nutrient.name());
                nutrients[nutrient.ordinal()] = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
            }
            amendments.put(row.getLong("Pos"), new Amendment(row.getString("Block"), nutrients));
        }
    }
}
