package ua.empirecraft.townymap.client;

import java.util.*;

/** Tracks waypoint ownership across delayed JourneyMap deletion callbacks. */
public final class FleetMarkerBook<T> {
    public record Entry<T>(String guid,String signature,T point) {}
    private final Map<Long,Entry<T>> marks=new HashMap<>();
    private final Set<Long> hidden=new HashSet<>();
    public Entry<T> get(long ship){return marks.get(ship);}
    public boolean hidden(long ship){return hidden.contains(ship);}
    public void put(long ship,String guid,String signature,T point){marks.put(ship,new Entry<>(guid,signature,point));}
    public boolean deleted(long ship,String guid){
        var current=marks.get(ship);if(current==null || !current.guid().equals(guid))return false;
        marks.remove(ship);hidden.add(ship);return true;
    }
    public List<T> retain(Set<Long> incoming){
        List<T> removed=new ArrayList<>();
        for(var it=marks.entrySet().iterator();it.hasNext();){var entry=it.next();if(!incoming.contains(entry.getKey())){removed.add(entry.getValue().point());it.remove();}}
        hidden.removeIf(id->!incoming.contains(id));return removed;
    }
    public List<T> clear(){var removed=marks.values().stream().map(Entry::point).toList();marks.clear();hidden.clear();return removed;}
}
