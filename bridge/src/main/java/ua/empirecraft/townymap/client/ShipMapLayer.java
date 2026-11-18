package ua.empirecraft.townymap.client;

import java.util.*;
import com.google.gson.*;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.model.*;
import journeymap.api.v2.common.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import ua.empirecraft.townymap.ShipObservation;

/** Optical contacts are moving arrows; stale arrows freeze in place and visibly fade. */
public final class ShipMapLayer {
    private static final String MOD="empirecraft_townymap";
    private static IClientAPI api;
    private static final Map<Long,MarkerOverlay> markers=new HashMap<>();
    private static List<ShipObservation> observations=List.of();
    private static JsonArray friendly=new JsonArray();
    private static boolean enabled=true;
    public static void bind(IClientAPI client){api=client;}
    public static void observations(List<ShipObservation> rows){observations=List.copyOf(rows);refresh();}
    public static void friendly(JsonArray rows){friendly=rows.deepCopy();refresh();}
    public static void enabled(boolean value){enabled=value;refresh();}
    public static List<ShipObservation> observations(){return observations;}
    public static String direction(double heading){return List.of("Пн","ПнСх","Сх","ПдСх","Пд","ПдЗх","Зх","ПнЗх").get(Math.floorMod((int)Math.round(heading/45),8));}
    public static String flag(String mode){return switch(mode){case "STATE"->"державний";case "PEACEFUL"->"мирний";case "PIRATE"->"піратський";default->"не визначено";};}
    public static String motion(ShipObservation row){return String.format(Locale.ROOT,"%s %.0f° · %.1f м/с · %.1f вуз.",direction(row.heading()),row.heading(),row.speed(),row.knots());}
    public static void refresh(){
        if(api==null)return;var keep=new HashSet<Long>();
        if(enabled)try{
            for(var row:observations){
                boolean fresh=System.currentTimeMillis()-row.observed()<2500;
                int color=row.flagMode().equals("PIRATE")?0xd57b6d:row.flagMode().equals("PEACEFUL")?0xd9ded2:0x74c1d1;
                String title=row.name()+" · "+row.country()+"\nПрапор: "+flag(row.flagMode())+"\n"+motion(row)+"\n"
                        +(fresh?"Видимий · ":"Останні відомі дані · ")+FleetWaypoints.description(row.observed());
                show(row.ship(),row.dimension(),row.x(),row.y(),row.z(),row.heading(),color,fresh?1f:.48f,
                        title,row.name()+" · "+String.format(Locale.ROOT,"%.1f м/с",row.speed()),keep);
            }
            for(var value:friendly){var row=value.getAsJsonObject();long id=row.get("ship").getAsLong();if(keep.contains(id))continue;
                show(id,row.get("dimension").getAsString(),row.get("x").getAsDouble(),row.get("y").getAsDouble(),row.get("z").getAsDouble(),
                        0,0x75bda8,.65f,row.get("name").getAsString()+"\nСоюзний флот · курс не розвідано",row.get("name").getAsString(),keep);
            }
        }catch(Exception error){System.getLogger(MOD).log(System.Logger.Level.WARNING,"Could not draw ship observations",error);}
        for(var it=markers.entrySet().iterator();it.hasNext();){var row=it.next();if(!keep.contains(row.getKey())){api.remove(row.getValue());it.remove();}}
    }
    private static void show(long id,String dimension,double x,double y,double z,double heading,int color,float opacity,
                             String title,String label,Set<Long> keep)throws Exception {
        var icon=new MapImage(new ResourceLocation(MOD,"textures/map/ship.png"),32,32).setDisplayWidth(18).setDisplayHeight(18)
                .centerAnchors().setRotation((int)Math.round(heading)).setColor(color).setOpacity(opacity);
        var position=new BlockPos((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));
        var marker=markers.computeIfAbsent(id,key->new MarkerOverlay(MOD,position,icon));
        marker.setPoint(position).setIcon(icon);marker.setDimension(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(dimension)))
                .setTitle(title).setLabel(label).setDisplayOrder(110).setOverlayGroupName("Кораблі · особиста розвідка")
                .setActiveUIs(Context.UI.Fullscreen,Context.UI.Minimap)
                .setTextProperties(new TextProperties().setActiveUIs(Context.UI.Fullscreen).setColor(color).setScale(.65f)
                        .setOffsetY(-17).setBackgroundOpacity(.7f));
        marker.flagForRerender();api.show(marker);keep.add(id);
    }
    public static void clear(){if(api!=null)markers.values().forEach(api::remove);markers.clear();observations=List.of();friendly=new JsonArray();}
}
