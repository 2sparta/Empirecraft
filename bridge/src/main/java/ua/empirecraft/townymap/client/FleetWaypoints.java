package ua.empirecraft.townymap.client;

import com.google.gson.*;
import java.util.*;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.event.CommonEventRegistry;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import journeymap.api.v2.common.event.common.WaypointEvent;
import journeymap.api.v2.common.waypoint.*;
import net.minecraft.core.BlockPos;
import ua.empirecraft.townymap.FleetNetwork;

/** A moving observation updates one native JourneyMap GUID instead of deleting the user's marker. */
public final class FleetWaypoints {
    private static final String MOD="empirecraft_townymap";
    private static IClientAPI api;
    private static final FleetMarkerBook<Waypoint> MARKS=new FleetMarkerBook<>();
    private static Waypoint sale;
    private FleetWaypoints(){}
    public static void initialize(IClientAPI client){
        api=client;clear();
        for(Waypoint old:List.copyOf(api.getWaypoints(MOD))){String role=old.getCustomData("empirecraft_fleet_role");if("ally".equals(role)||"offer".equals(role))api.removeWaypoint(MOD,old);}
        CommonEventRegistry.WAYPOINT_EVENT.subscribe(MOD,event->{
            if(event.context!=WaypointEvent.Context.DELETED || !MOD.equals(event.waypoint.getModId()))return;
            String role=event.waypoint.getCustomData("empirecraft_fleet_role"),id=event.waypoint.getCustomData("empirecraft_ship");
            if(!"ally".equals(role)||id==null)return;
            try{long ship=Long.parseLong(id);if(MARKS.deleted(ship,event.waypoint.getGuid()))FleetNetwork.CHANNEL.sendToServer(new FleetNetwork.Request("","map-hide","","",List.of(id),"",1,"map"));}catch(NumberFormatException ignored){}
        });
        FullscreenEventRegistry.WAYPOINT_POPUP_MENU_EVENT.subscribe(MOD,event->{
            var point=event.getWaypoint();if(!MOD.equals(point.getModId())||point.getCustomData("empirecraft_fleet_role")==null)return;
            String date=point.getDescription();if(date!=null&&!date.isBlank())event.getPopupMenu().addMenuItem(date,position->{});
        });
    }
    public static String description(long observed){return "Останнє спостереження: "+java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(java.time.ZoneId.of("Europe/Kyiv")).format(java.time.Instant.ofEpochMilli(observed))+" (Київ)";}
    private static BlockPos position(JsonObject row){return new BlockPos((int)Math.floor(row.get("x").getAsDouble()),(int)Math.floor(row.get("y").getAsDouble()),(int)Math.floor(row.get("z").getAsDouble()));}
    private static void update(Waypoint point,JsonObject row,boolean offer){
        point.setName((offer?"Продаж · ":"Флот · ")+row.get("name").getAsString());point.setBlockPos(position(row));
        String dimension=row.get("dimension").getAsString();point.setDimensions(List.of(dimension));point.setPrimaryDimension(dimension);
        if(row.has("observed"))point.setDescription(description(row.get("observed").getAsLong()));
    }
    private static Waypoint create(JsonObject row,boolean offer){
        var point=WaypointFactory.createWaypoint(MOD,position(row),row.get("name").getAsString(),row.get("dimension").getAsString(),false);
        point.setColor(offer?0xe6ae4f:0x64c4d8);point.setShowBeacon(false);point.setShowInWorld(false);point.setShowOnMap(true);
        point.setCustomData("empirecraft_ship",row.get("ship").getAsString());point.setCustomData("empirecraft_fleet_role",offer?"offer":"ally");update(point,row,offer);return point;
    }
    public static void marks(JsonArray rows){
        if(api==null)return;Set<Long> incoming=new HashSet<>();
        for(JsonElement element:rows){var row=element.getAsJsonObject();long id=row.get("ship").getAsLong();incoming.add(id);if(MARKS.hidden(id))continue;
            var old=MARKS.get(id);String signature=row.toString();if(old!=null&&signature.equals(old.signature()))continue;
            Waypoint point=old==null?create(row,false):old.point();update(point,row,false);MARKS.put(id,point.getGuid(),signature,point);api.addWaypoint(MOD,point);
        }
        for(Waypoint old:MARKS.retain(incoming))api.removeWaypoint(MOD,old);
    }
    public static void look(JsonObject row){if(api==null)return;if(sale!=null)api.removeWaypoint(MOD,sale);sale=create(row,true);api.addWaypoint(MOD,sale);}
    public static void clear(){var old=MARKS.clear();if(api!=null){for(Waypoint point:old)api.removeWaypoint(MOD,point);if(sale!=null)api.removeWaypoint(MOD,sale);}sale=null;}
}
