package ua.empirecraft.townymap.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.townymap.*;

/** Kept client-only so a dedicated server never loads Minecraft's screen classes. */
@Mod.EventBusSubscriber(modid=EmpireTownyMap.MOD_ID,value=Dist.CLIENT)
public final class MapModeButton {
    public static void selected(MapMode mode){StrategyMapUi.selected(mode);}
    @SubscribeEvent public static void opened(ScreenEvent.Init.Post event){
        StrategyMapUi.opened(ClientAccess.call(event,"getScreen"));
    }
}
