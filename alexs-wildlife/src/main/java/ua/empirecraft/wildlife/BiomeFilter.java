package ua.empirecraft.wildlife;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.registries.ForgeRegistries;
/** Called by the patched native Alex biome modifier after its own additions. */
public final class BiomeFilter {
    public static boolean antFeature(net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<?> context){
        var p=context.m_159777_();var level=context.m_159774_().m_6018_();var policy=WildlifeMod.policy;
        return policy.allowed("alexsmobs:leafcutter_ant",level.m_46472_().m_135782_().toString(),p.m_123341_()+8,70,p.m_123343_()+8,"land",0);
    }
    public static void prune(ModifiableBiomeInfo.BiomeInfo.Builder builder){
        for(var category:MobCategory.values())builder.getMobSpawnSettings().getSpawner(category).removeIf(row->WildlifeMod.policy.removed(ForgeRegistries.ENTITY_TYPES.getKey(row.f_48404_).toString()));
    }
}
