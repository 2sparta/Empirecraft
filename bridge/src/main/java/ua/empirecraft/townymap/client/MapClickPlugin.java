package ua.empirecraft.townymap.client;

import journeymap.api.v2.client.*;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Optional client installation of the same bridge JAR enables Shift-click estate cards. */
@JourneyMapPlugin(apiVersion="2.0.0")
public final class MapClickPlugin implements IClientPlugin {
    private long lastClick;
    @Override public String getModId(){return "empirecraft_townymap";}
    @Override public void initialize(IClientAPI api){
        new FleetMapPlugin().initialize(api);
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(getModId(),event->{
            if(event.getStage()!=FullscreenMapEvent.Stage.PRE || event.getButton()!=0 || !Screen.hasShiftDown())return;
            Minecraft mc=Minecraft.getInstance();if(mc.player==null || mc.getConnection()==null || System.currentTimeMillis()-lastClick<500)return;
            lastClick=System.currentTimeMillis();var position=event.getLocation();
            String dimension=event.getLevel().location().toString();
            MapMemoryScreen.open(dimension,position.getX(),position.getZ());
            event.cancel();
        });
    }
}
