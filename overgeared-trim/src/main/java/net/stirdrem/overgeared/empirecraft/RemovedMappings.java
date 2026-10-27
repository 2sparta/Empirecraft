package net.stirdrem.overgeared.empirecraft;

import java.util.Set;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Deleted IDs in old saves become air; no replacement equipment is introduced. */
@Mod.EventBusSubscriber(modid="overgeared",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class RemovedMappings {
    @SubscribeEvent
    public static void missing(MissingMappingsEvent event) {
        for(var mapping:event.getAllMappings(ForgeRegistries.Keys.ITEMS))
            if(RecipePolicy.REMOVED.contains(mapping.getKey().toString()))mapping.remap(Items.f_41852_);
        for(var mapping:event.getAllMappings(ForgeRegistries.Keys.BLOCKS))
            if(RecipePolicy.REMOVED.contains(mapping.getKey().toString()))mapping.remap(Blocks.f_50016_);
        for(var mapping:event.getAllMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES))
            if(Set.of("overgeared:casting_furnace_be","overgeared:nether_alloy_furnace_be").contains(mapping.getKey().toString()))mapping.ignore();
        for(var mapping:event.getAllMappings(ForgeRegistries.Keys.MENU_TYPES))
            if(Set.of("overgeared:casting_furnace","overgeared:nether_alloy_smelter_menu").contains(mapping.getKey().toString()))mapping.ignore();
    }
}
