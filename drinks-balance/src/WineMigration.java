package net.satisfy.vinery.core.util;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Keep old worlds readable after removing the two native item/block registrations. */
@Mod.EventBusSubscriber(modid="vinery",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class WineMigration {
    private static boolean removed(ResourceLocation id) {
        String path=id.m_135815_();
        return path.equals("chorus_wine") || path.equals("clark_wine");
    }
    @SubscribeEvent public static void missing(MissingMappingsEvent event) {
        ResourceLocation replacement=new ResourceLocation("vinery","red_wine");
        for (var entry:event.getMappings(ForgeRegistries.Keys.ITEMS,"vinery"))
            if(removed(entry.getKey())) entry.remap(ForgeRegistries.ITEMS.getValue(replacement));
        for (var entry:event.getMappings(ForgeRegistries.Keys.BLOCKS,"vinery"))
            if(removed(entry.getKey())) entry.remap(ForgeRegistries.BLOCKS.getValue(replacement));
    }
}
