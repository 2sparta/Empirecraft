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
    private static IClientAPI api;private static boolean updating;private static int ticks;
    private static final Map<Long,Waypoint> MARKS=new HashMap<>();private static final Set<Long> HIDDEN=new HashSet<>();
    private static Waypoint sale;
    @Override public String getModId(){return EmpireTownyMap.MOD_ID;}
    @Override public void initialize(IClientAPI client){
        api=client;CommonEventRegistry.WAYPOINT_EVENT.subscribe(getModId(),event->{
            if(updating || event.context!=WaypointEvent.Context.DELETED || !getModId().equals(event.waypoint.getModId()))return;
            String role=event.waypoint.getCustomData("empirecraft_fleet_role"),id=event.waypoint.getCustomData("empirecraft_ship");
            if(!"ally".equals(role) || id==null)return;
            try{long ship=Long.parseLong(id);MARKS.remove(ship);HIDDEN.add(ship);FleetNetwork.CHANNEL.sendToServer(new FleetNetwork.Request("","map-hide","","",List.of(id),"",1,"map"));}catch(NumberFormatException ignored){}
        });
    }
    private static Waypoint create(JsonObject row,boolean offer){
        long id=row.get("ship").getAsLong();String name=row.get("name").getAsString();
        var point=WaypointFactory.createWaypoint(EmpireTownyMap.MOD_ID,BlockPos.containing(row.get("x").getAsDouble(),row.get("y").getAsDouble(),row.get("z").getAsDouble()),(offer?"Продаж · ":"Флот · ")+name,row.get("dimension").getAsString(),false);
        point.setColor(offer?0xe6ae4f:0x64c4d8);point.setShowBeacon(false);point.setShowInWorld(false);point.setShowOnMap(true);
        point.setCustomData("empirecraft_ship",Long.toString(id));point.setCustomData("empirecraft_fleet_role",offer?"offer":"ally");
        if(row.has("observed"))point.setDescription("Останнє спостереження: "+java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(java.time.ZoneId.of("Europe/Kyiv")).format(java.time.Instant.ofEpochMilli(row.get("observed").getAsLong())));
        return point;
    }
    public static void marks(JsonArray rows){
        if(api==null)return;Set<Long> incoming=new HashSet<>();updating=true;
        try{
            for(JsonElement element:rows){var row=element.getAsJsonObject();long id=row.get("ship").getAsLong();incoming.add(id);if(HIDDEN.contains(id))continue;
                Waypoint old=MARKS.get(id);String stamp=row.get("observed").getAsString();
                if(old!=null && stamp.equals(old.getCustomData("empirecraft_observed")))continue;
                if(old!=null)api.removeWaypoint(EmpireTownyMap.MOD_ID,old);Waypoint point=create(row,false);point.setCustomData("empirecraft_observed",stamp);MARKS.put(id,point);api.addWaypoint(EmpireTownyMap.MOD_ID,point);
            }
            for(var it=MARKS.entrySet().iterator();it.hasNext();){var entry=it.next();if(!incoming.contains(entry.getKey())){api.removeWaypoint(EmpireTownyMap.MOD_ID,entry.getValue());it.remove();}}
            HIDDEN.removeIf(id->!incoming.contains(id));
        }finally{updating=false;}
    }
    public static void look(JsonObject row){if(api==null)return;updating=true;try{if(sale!=null)api.removeWaypoint(EmpireTownyMap.MOD_ID,sale);sale=create(row,true);api.addWaypoint(EmpireTownyMap.MOD_ID,sale);}finally{updating=false;}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.getConnection()==null){if(api!=null && (!MARKS.isEmpty()||sale!=null)){updating=true;try{for(Waypoint mark:MARKS.values())api.removeWaypoint(EmpireTownyMap.MOD_ID,mark);if(sale!=null)api.removeWaypoint(EmpireTownyMap.MOD_ID,sale);}finally{updating=false;}}MARKS.clear();HIDDEN.clear();sale=null;return;}
        if(++ticks%20!=0 || !FleetNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection()))return;
        double vertical=mc.options.fov().get(),aspect=(double)mc.getWindow().getWidth()/Math.max(1,mc.getWindow().getHeight());
        double horizontal=Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(vertical*.5))*aspect));
        FleetNetwork.CHANNEL.sendToServer(new FleetNetwork.Request("","view-fov","","",List.of(Double.toString(horizontal),Double.toString(vertical)),"",1,"view"));
    }
}
