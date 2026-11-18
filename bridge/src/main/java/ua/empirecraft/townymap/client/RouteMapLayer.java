package ua.empirecraft.townymap.client;

import java.util.*;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.*;
import journeymap.api.v2.client.model.*;
import journeymap.api.v2.common.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.Level;

/** The existing Recruits route is drawn directly over JourneyMap; no second terrain cache exists. */
final class RouteMapLayer {
    private static IClientAPI api;
    private static final List<Displayable> overlays=new ArrayList<>();
    private static boolean enabled=true;
    static void bind(IClientAPI client){api=client;}
    static void enabled(boolean value){enabled=value;refresh();}
    @SuppressWarnings("unchecked") static void refresh(){
        clear();if(api==null||!enabled||RecruitsMapTools.selected()==null||ClientAccess.player()==null)return;
        Object level=ClientAccess.field(ClientAccess.client(),"f_91073_");
        var dimension=(ResourceKey<Level>)ClientAccess.call(level,"m_46472_");
        var points=RecruitsMapTools.points();
        try {
            for(int i=0;i<points.size();i++) {
                var point=points.get(i);var at=RecruitsMapTools.position(point);
                var icon=new MapImage(new ResourceLocation("empirecraft_townymap","textures/map/route.png"),16,16)
                        .setDisplayWidth(10).setDisplayHeight(10).centerAnchors();
                var marker=new MarkerOverlay("empirecraft_townymap",at,icon);
                marker.setDimension(dimension).setTitle("Точка "+(i+1)+" · очікування "+RecruitsMapTools.waitSeconds(point)+" с")
                        .setLabel(Integer.toString(i+1)).setActiveUIs(Context.UI.Fullscreen,Context.UI.Minimap).setOverlayGroupName("Маршрути Recruits");
                api.show(marker);overlays.add(marker);
                if(i>0) {
                    var previous=RecruitsMapTools.position(points.get(i-1));
                    double dx=at.getX()-previous.getX(),dz=at.getZ()-previous.getZ(),length=Math.hypot(dx,dz);
                    if(length<.1)continue;double ox=-dz/length,oz=dx/length;
                    var polygon=new MapPolygon(List.of(new BlockPos((int)Math.round(previous.getX()+ox),64,(int)Math.round(previous.getZ()+oz)),
                            new BlockPos((int)Math.round(at.getX()+ox),64,(int)Math.round(at.getZ()+oz)),
                            new BlockPos((int)Math.round(at.getX()-ox),64,(int)Math.round(at.getZ()-oz)),
                            new BlockPos((int)Math.round(previous.getX()-ox),64,(int)Math.round(previous.getZ()-oz))));
                    var line=new PolygonOverlay("empirecraft_townymap",dimension,new ShapeProperties().setStrokeColor(0xe0bc74)
                            .setStrokeWidth(1).setStrokeOpacity(.85f).setFillColor(0xe0bc74).setFillOpacity(.65f),polygon);
                    line.setTitle(RecruitsMapTools.name(RecruitsMapTools.selected())).setActiveUIs(Context.UI.Fullscreen,Context.UI.Minimap)
                            .setOverlayGroupName("Маршрути Recruits");api.show(line);overlays.add(line);
                }
            }
        }catch(Exception failure){System.getLogger("empirecraft_townymap").log(System.Logger.Level.WARNING,"Could not draw route",failure);}
    }
    static void clear(){if(api!=null)overlays.forEach(api::remove);overlays.clear();}
}
