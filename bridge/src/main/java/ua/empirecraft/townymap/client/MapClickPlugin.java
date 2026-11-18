package ua.empirecraft.townymap.client;

import journeymap.api.v2.client.*;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.FullscreenEventRegistry;

/** The client bridge adds ordinary left-click country cards to JourneyMap. */
@JourneyMapPlugin(apiVersion="2.0.0")
public final class MapClickPlugin implements IClientPlugin {
    @Override public String getModId(){return "empirecraft_townymap";}
    @Override public void initialize(IClientAPI api){
        new FleetMapPlugin().initialize(api);
        StrategyMapUi.bind(api);
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(getModId(),event->{
            if(event.getStage()!=FullscreenMapEvent.Stage.PRE || event.getButton()!=0)return;
            if(ClientAccess.player()==null)return;
            var position=event.getLocation();
            String dimension=event.getLevel().location().toString();
            StrategyMapUi.mapClick(dimension,position.getX(),position.getZ());
        });
    }
}
