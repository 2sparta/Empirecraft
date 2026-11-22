package ua.empirecraft.townymap.client;

import java.util.*;
import com.google.gson.JsonArray;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.model.*;
import journeymap.api.v2.common.Context;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import ua.empirecraft.townymap.ShipObservation;

/** Hull portraits rotate with the hull. The separate course arrow follows velocity, including astern. */
public final class ShipMapLayer {
    private static final String MOD="empirecraft_townymap";
    private static IClientAPI api;
    private static final Map<String,MarkerOverlay> markers=new HashMap<>();
    private static List<ShipObservation> saved=List.of(),live=List.of();
    private static long liveAt;
    private static boolean enabled=true;
    public static void bind(IClientAPI client){api=client;refresh();}
    public static void observations(List<ShipObservation> rows){saved=List.copyOf(rows);refresh();}
    public static void contacts(List<ShipObservation> rows){live=List.copyOf(rows);liveAt=System.nanoTime();refresh();}
    /** Allied fleet bookkeeping does not grant a live position outside rendering distance. */
    public static void friendly(JsonArray rows){}
    public static void enabled(boolean value){enabled=value;refresh();}
    public static List<ShipObservation> observations(){return saved;}
    public static String direction(double heading){return List.of("Пн","ПнСх","Сх","ПдСх","Пд","ПдЗх","Зх","ПнЗх").get(Math.floorMod((int)Math.round(heading/45),8));}
    public static String course(double heading){return List.of("↑","↗","→","↘","↓","↙","←","↖").get(Math.floorMod((int)Math.round(heading/45),8))+" "+direction(heading)+" "+Math.round(heading)+"°";}
    public static String flag(String mode){return switch(mode){case "STATE"->"державний";case "PEACEFUL"->"мирний";case "PIRATE"->"піратський";default->"не визначено";};}
    public static String motion(ShipObservation row){return row.speed()<=.05?"Стоїть · 0.0 м/с":String.format(Locale.ROOT,"%s %.0f° · %.1f м/с · %.1f вуз.",direction(row.heading()),row.heading(),row.speed(),row.knots());}
    private static boolean inRange(ShipObservation row){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!mc.level.dimension().location().toString().equals(row.dimension()))return false;
        double r=16.0*mc.options.getEffectiveRenderDistance();
        var ship=org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(mc.level).getLoadedShips().getById(row.ship());
        if(ship!=null){var b=ship.getWorldAABB();if(b!=null)return b.maxX()>=mc.player.getX()-r&&b.minX()<=mc.player.getX()+r&&b.maxZ()>=mc.player.getZ()-r&&b.minZ()<=mc.player.getZ()+r;}
        return Math.abs(row.x()-mc.player.getX())<=r&&Math.abs(row.z()-mc.player.getZ())<=r;
    }
    public static List<ShipObservation> view(){
        var rows=new LinkedHashMap<Long,ShipObservation>();for(var row:saved)rows.put(row.ship(),row);
        if(System.nanoTime()-liveAt<2_000_000_000L)for(var row:live)if(inRange(row))rows.put(row.ship(),row);
        return List.copyOf(rows.values());
    }
    public static void refresh(){
        if(Minecraft.getInstance().isPaused())liveAt=System.nanoTime();
        var rows=view();StrategyMapUi.reconView(rows);if(api==null)return;var keep=new HashSet<String>();
        var current=new HashSet<Long>();if(System.nanoTime()-liveAt<2_000_000_000L)for(var row:live)if(inRange(row))current.add(row.ship());
        if(enabled)for(var row:rows)try{
            boolean visible=current.contains(row.ship());int color=row.flagMode().equals("PIRATE")?0xd57b6d:row.flagMode().equals("PEACEFUL")?0xd9ded2:0x74c1d1;
            String title=row.name()+" · "+row.country()+"\nПрапор: "+flag(row.flagMode())+"\n"+motion(row)+"\n"+
                    (visible?"Поточний контакт у межах прорисовки":"Збережено через підзорну трубу")+"\n"+FleetWaypoints.description(row.observed());
            var image=ShipIconCache.image(row.ship(),row.icon());
            var icon=new MapImage(image==null?ShipIconCache.asset("ship_unknown"):image);
            icon.setDisplayWidth(28).setDisplayHeight(28).centerAnchors().setRotation(-(int)Math.round(row.hullYaw())).setOpacity(visible?1:.6f).setBlur(false);
            show("hull/"+row.ship(),row,icon,title,row.name()+" · "+(row.speed()>.05?course(row.heading())+" · ":"")+String.format(Locale.ROOT,"%.1f м/с",row.speed()),color,keep);
        }catch(Exception error){System.getLogger(MOD).log(System.Logger.Level.WARNING,"Could not draw ship contact",error);}
        for(var it=markers.entrySet().iterator();it.hasNext();){var entry=it.next();if(!keep.contains(entry.getKey())){api.remove(entry.getValue());it.remove();}}
    }
    private static void show(String id,ShipObservation row,MapImage icon,String title,String label,int color,Set<String> keep)throws Exception{
        var pos=BlockPos.containing(row.x(),row.y(),row.z());var marker=markers.computeIfAbsent(id,k->new MarkerOverlay(MOD,pos,icon));
        marker.setPoint(pos).setIcon(icon);marker.setDimension(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(row.dimension())))
                .setTitle(title).setLabel(label).setDisplayOrder(id.startsWith("course/")?111:110).setOverlayGroupName("Кораблі · особиста розвідка")
                .setActiveUIs(Context.UI.Fullscreen,Context.UI.Minimap).setTextProperties(new TextProperties().setActiveUIs(Context.UI.Fullscreen).setColor(color).setScale(.65f).setOffsetY(-23).setBackgroundOpacity(.7f));
        marker.flagForRerender();api.show(marker);keep.add(id);
    }
    public static void clear(){if(api!=null)markers.values().forEach(api::remove);markers.clear();saved=List.of();live=List.of();liveAt=0;}
}
