package ua.empirecraft.spawn;
import java.util.Set;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Accept intentional deletions in old worlds; never remap them to another item or block. */
@Mod.EventBusSubscriber(modid="spawn",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class RemovedMappings {
    public static final Set<String> IDS=Set.of(
        "spawn:sunstone","spawn:moonstone","spawn:heart_of_the_sun","spawn:heliostone","spawn:heliostone_wall","spawn:heliostone_slab","spawn:heliostone_stairs","spawn:unstable_heliostone","spawn:magmatic_splatter","spawn:blasted_sand","spawn:blasted_sandstone","spawn:chiseled_blasted_sandstone","spawn:blasted_sandstone_wall","spawn:blasted_sandstone_slab","spawn:blasted_sandstone_stairs","spawn:cut_blasted_sandstone","spawn:cut_blasted_sandstone_slab","spawn:smooth_blasted_sandstone","spawn:smooth_blasted_sandstone_slab","spawn:smooth_blasted_sandstone_stairs","spawn:zombified_flower","spawn:barnacle","spawn:barnacle_cluster",
        "spawn:blue_footed_boots","spawn:firekeeper_spawn_egg","spawn:stranded_spawn_egg","spawn:flukeshroom_spawn_egg","spawn:barbed_spawn_egg","spawn:stickbug_spawn_egg","spawn:baby_flukeshroom_bucket","spawn:suspicious_stick",
        "spawn:stranded","spawn:flukeshroom","spawn:barbed","spawn:barb","spawn:stickbug","spawn:firekeeper","spawn:volcanic_island","spawn:volcanic_island_piece","spawn:stranded_spawns");
    @SubscribeEvent @SuppressWarnings({"rawtypes","unchecked"})
    public static void missing(MissingMappingsEvent event){
        for(Object value:event.getAllMappings((net.minecraft.resources.ResourceKey)event.getKey())){
            MissingMappingsEvent.Mapping mapping=(MissingMappingsEvent.Mapping)value;
            if(IDS.contains(mapping.getKey().toString()))mapping.ignore();
        }
    }
    private RemovedMappings(){}
}
