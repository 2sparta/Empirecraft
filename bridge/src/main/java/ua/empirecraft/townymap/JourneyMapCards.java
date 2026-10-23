package ua.empirecraft.townymap;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Optional client half. The server resolves claims and opens its read-only inventory cards. */
@JourneyMapPlugin(apiVersion="2.0.0")
public final class JourneyMapCards implements IClientPlugin {
    private long lastClick;
    @Override public String getModId(){return EmpireTownyMap.MOD_ID;}
    @Override public void initialize(IClientAPI api){FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(getModId(),event->{
        if(event.getStage()!=FullscreenMapEvent.Stage.PRE||event.getButton()!=0||!Screen.hasShiftDown())return;Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getConnection()==null||!mc.level.dimension().equals(event.getLevel()))return;
        long now=System.nanoTime();if(now-lastClick<500_000_000L)return;lastClick=now;int x=event.getLocation().getX(),z=event.getLocation().getZ();event.cancel();
        mc.execute(()->{if(mc.getConnection()!=null){mc.setScreen(null);mc.getConnection().sendCommand("realmmap inspect "+x+" "+z);}});
    });}
}
