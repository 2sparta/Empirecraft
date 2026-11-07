package ua.empirecraft.leaves.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.leaves.SeasonalLeaves;

@Mod.EventBusSubscriber(modid=SeasonalLeaves.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModelEvents {
    @SubscribeEvent public static void baked(ModelEvent.BakingCompleted event) {ClientSeasons.clearModels();}
}
