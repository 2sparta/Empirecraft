package ua.empirecraft.townymap;

import java.util.*;

/** One authenticated optical observation. Coordinates, velocity and heraldry age together. */
public record ShipObservation(long ship, String dimension, double x, double y, double z,
                              double heading, double speed, long observed, String name,
                              String country, String town, String flagMode, String flag) {
    public ShipObservation {
        if (ship < 0 || !Double.isFinite(x+y+z+heading+speed) || Math.abs(x)>30_000_000 || Math.abs(z)>30_000_000
                || Math.abs(y)>30_000_000 || speed<0 || speed>1_000_000 || observed<0
                || dimension==null || dimension.length()>256 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Invalid ship observation");
        heading = ((heading % 360) + 360) % 360;
        name = text(name,256); country = text(country,256); town = text(town,256);
        flagMode = text(flagMode,24); flag = text(flag,16384);
    }
    private static String text(String value,int maximum) {
        if (value==null || value.length()>maximum) throw new IllegalArgumentException("Invalid observation text");
        return value;
    }
    public double knots() { return speed * 1.943844492; }
    public static final class Log {
        public static final int LIMIT = 128;
        private final Map<Long,ShipObservation> sightings = new HashMap<>();
        public boolean observe(ShipObservation next) {
            var previous = sightings.get(next.ship());
            if (previous!=null && previous.observed()>next.observed()) return false;
            if (next.equals(previous)) return false;
            sightings.put(next.ship(),next);
            if (sightings.size()>LIMIT) sightings.remove(sightings.values().stream()
                    .min(Comparator.comparingLong(ShipObservation::observed).thenComparingLong(ShipObservation::ship)).orElseThrow().ship());
            return true;
        }
        public ShipObservation get(long ship) { return sightings.get(ship); }
        public List<ShipObservation> snapshot() { return sightings.values().stream()
                .sorted(Comparator.comparingLong(ShipObservation::observed).reversed().thenComparingLong(ShipObservation::ship)).toList(); }
        public static Log restore(List<ShipObservation> rows) {
            if (rows==null || rows.size()>LIMIT) throw new IllegalArgumentException("Oversized ship history");
            var log = new Log();
            for (var row : rows) if (row==null || log.sightings.putIfAbsent(row.ship(),row)!=null)
                throw new IllegalArgumentException("Duplicate ship history");
            return log;
        }
    }
    /** A complete view replaces the old one atomically; stale or duplicate pages cannot resurrect old contacts. */
    public static final class Pages {
        private long completed=Long.MIN_VALUE,pending=Long.MIN_VALUE;
        private int next,total;
        private final List<ShipObservation> rows=new ArrayList<>();
        public Optional<List<ShipObservation>> accept(long batch,int part,int count,List<ShipObservation> page) {
            if(page==null||page.size()>16||count<1||count>8||part<0||part>=count)throw new IllegalArgumentException("Invalid ship page");
            if(batch<=completed||batch<pending)return Optional.empty();
            if(part==0&&batch>pending){pending=batch;next=0;total=count;rows.clear();}
            if(batch!=pending||part!=next||count!=total)return Optional.empty();
            rows.addAll(page);next++;
            if(next!=total)return Optional.empty();
            var snapshot=Log.restore(rows).snapshot();completed=batch;rows.clear();return Optional.of(snapshot);
        }
        public void clear(){completed=Long.MIN_VALUE;pending=Long.MIN_VALUE;next=0;total=0;rows.clear();}
    }
}
