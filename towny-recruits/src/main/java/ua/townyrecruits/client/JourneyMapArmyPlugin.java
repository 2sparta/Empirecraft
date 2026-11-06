package ua.townyrecruits.client;

import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.client.*;

/** JourneyMap discovers this class only when installed; Forge never loads it as an event subscriber. */
@JourneyMapPlugin(apiVersion="2.0.0")
public final class JourneyMapArmyPlugin implements IClientPlugin {
    @Override public String getModId(){return "townyrecruits";}
    @Override public void initialize(IClientAPI api){ArmyMapClient.bind(new JourneyMapArmyLayer(api,new ArmyMapTextures(),key->net.minecraft.network.chat.Component.m_237115_(key).getString()));}
}
