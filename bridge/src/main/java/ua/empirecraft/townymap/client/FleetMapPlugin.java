package ua.empirecraft.townymap.client;

import com.google.gson.*;
import java.util.*;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.client.*;
import journeymap.api.v2.common.event.CommonEventRegistry;
import journeymap.api.v2.common.event.common.WaypointEvent;
import journeymap.api.v2.common.waypoint.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ua.empirecraft.townymap.*;

/** Actual JourneyMap waypoints: normal Delete also persists the player's hide preference on the server. */
@Mod.EventBusSubscriber(modid=EmpireTownyMap.MOD_ID,value=Dist.CLIENT)
public final class FleetMapPlugin implements IClientPlugin {
    private static int ticks;
    @Override public String getModId(){return EmpireTownyMap.MOD_ID;}
    @Override public void initialize(IClientAPI client){FleetWaypoints.initialize(client);}
    public static void marks(JsonArray rows){FleetWaypoints.marks(rows);}
    public static void look(JsonObject row){FleetWaypoints.look(row);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.getConnection()==null){FleetWaypoints.clear();ticks=0;return;}
        if(++ticks%20!=0 || !FleetNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection()))return;
        double vertical=mc.options.fov().get(),aspect=(double)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight());
        double horizontal=Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(vertical*.5))*aspect));
        FleetNetwork.CHANNEL.sendToServer(new FleetNetwork.Request("","view-fov","","",List.of(Double.toString(horizontal),Double.toString(vertical)),"",1,"view"));
    }
}
