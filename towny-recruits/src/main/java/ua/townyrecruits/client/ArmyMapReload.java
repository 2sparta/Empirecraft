package ua.townyrecruits.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="townyrecruits",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ArmyMapReload {
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)manager->ArmyMapClient.reload());}
}
