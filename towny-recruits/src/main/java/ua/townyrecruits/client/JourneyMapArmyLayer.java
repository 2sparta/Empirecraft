package ua.townyrecruits.client;

import java.util.*;
import java.util.function.Function;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.*;
import journeymap.api.v2.client.model.*;
import journeymap.api.v2.common.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import ua.townyrecruits.TownyRecruits;
import ua.townyrecruits.map.ArmyMapData.*;

/** Moving overlays live in our own layer; no saved waypoints or Towny/fleet overlays are modified. */
public final class JourneyMapArmyLayer implements ArmyMapClient.Layer {
    public interface Icons { MapImage unit(Unit unit); MapImage flag(Style style,Relation relation); void clear(); }
    private final IClientAPI api;private final Icons icons;private final Function<String,String> text;
    private final Map<String,MarkerOverlay> markers=new HashMap<>();
    public JourneyMapArmyLayer(IClientAPI api,Icons icons,Function<String,String> text){this.api=api;this.icons=icons;this.text=text;}
    private String word(String key){return text.apply("map.townyrecruits."+key);}
    @Override public void update(Snapshot snapshot) {
        Set<String> keep=new HashSet<>();Map<UUID,List<Unit>> regiments=new HashMap<>();
        ResourceKey<Level> dimension=ResourceKey.m_135785_(Registries.f_256858_,snapshot.dimension());
        try {
            for(Unit u:snapshot.units()) {
                String label=word("relation."+u.relation().name().toLowerCase(Locale.ROOT))+" · "+word("kind."+u.kind().name().toLowerCase(Locale.ROOT));
                if(!u.nation().isEmpty())label+=" · "+u.nation();if(!u.group().isEmpty())label+=" · "+u.group();
                if(!u.commander().isEmpty())label+=" · "+word("commander")+": "+u.commander();if(u.routing())label+=" · "+word("routing");
                marker("unit/"+u.id(),BlockPos.m_274446_(u.position()),dimension,icons.unit(u),label,"",90,keep);
                if(u.regiment()!=null)regiments.computeIfAbsent(u.regiment(),id->new ArrayList<>()).add(u);
            }
            for(Style style:snapshot.styles()) {
                List<Unit> units=regiments.get(style.regiment());if(units==null||units.isEmpty())continue;
                Unit anchor=flagAnchor(units);String label=style.name()+" · "+word("visible")+": "+units.size()+" · "+word(anchor.bearer()?"bearer":"regiment");
                marker("regiment/"+style.regiment(),BlockPos.m_274446_(anchor.position()),dimension,icons.flag(style,anchor.relation()),label,style.name(),100,keep);
            }
        } catch(Exception error){TownyRecruits.LOG.debug("Army map overlay update failed",error);}
        for(var it=markers.entrySet().iterator();it.hasNext();) {var e=it.next();if(!keep.contains(e.getKey())){api.remove(e.getValue());it.remove();}}
    }
    public static Unit flagAnchor(List<Unit> units) {
        var bearer=units.stream().filter(Unit::bearer).min(Comparator.comparing(Unit::id));if(bearer.isPresent())return bearer.get();
        // Pick an actual soldier in the densest 32-block cell. A regiment split across
        // the map must not acquire a flag at a fictitious midpoint between its units.
        Map<String,List<Unit>> cells=new TreeMap<>();
        for(Unit u:units)cells.computeIfAbsent((int)Math.floor(u.position().f_82479_/32)+":"+(int)Math.floor(u.position().f_82481_/32),id->new ArrayList<>()).add(u);
        return cells.values().stream().max(Comparator.comparingInt(List::size)).orElseThrow().stream().min(Comparator.comparing(Unit::id)).orElseThrow();
    }
    private void marker(String key,BlockPos position,ResourceKey<Level> dimension,MapImage image,String title,String label,int order,Set<String> keep)throws Exception {
        MarkerOverlay marker=markers.computeIfAbsent(key,id->new MarkerOverlay("townyrecruits",position,image));
        marker.setPoint(position).setIcon(image);marker.setDimension(dimension).setTitle(title).setLabel(label).setDisplayOrder(order)
            .setOverlayGroupName(word("layer")).setActiveUIs(Context.UI.Fullscreen,Context.UI.Minimap)
            .setTextProperties(new TextProperties().setActiveUIs(Context.UI.Fullscreen).setColor(0xffffff).setScale(0.7f).setOffsetY(-15).setBackgroundOpacity(0.7f));
        marker.flagForRerender();api.show(marker);keep.add(key);
    }
    @Override public void clear(){api.removeAll("townyrecruits");markers.clear();icons.clear();}
    @Override public void reload(){clear();}
}
