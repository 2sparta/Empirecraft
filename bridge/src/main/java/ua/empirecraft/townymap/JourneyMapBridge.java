package ua.empirecraft.townymap;

import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.IServerPlugin;
import net.minecraftforge.common.MinecraftForge;

/** Discovered by JourneyMap on the dedicated server; no client addon is required. */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapBridge implements IServerPlugin {
    @Override
    public void initialize(IServerAPI api) {
        MinecraftForge.EVENT_BUS.register(new BridgeService(api.getOverlayApi()));
        EmpireTownyMap.LOG.info("Towny JourneyMap server overlay bridge initialized");
    }

    @Override
    public String getModId() { return EmpireTownyMap.MOD_ID; }
}
