package ua.empirecraft.earth;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Immutable, data-driven geographic queries. GeoJSON coordinates are longitude, latitude. */
public final class Geography {
    public record Point(double lon, double lat) {}
    public record Climate(double mean, double amplitude, double rain, String id, boolean approximate) {
        public double temperature(double civilMonth, double latitude) {
            return mean + amplitude * Math.cos((civilMonth - (latitude < 0 ? 0 : 6)) * Math.PI / 6);
        }
    }
    public record Feature(String id, String layer, String label, int priority, double[][][] rings, JsonObject values) {
        public String text(String key, String fallback) { return values.has(key) ? values.get(key).getAsString() : fallback; }
        public double number(String key, double fallback) { return values.has(key) ? values.get(key).getAsDouble() : fallback; }
        public boolean has(String key, String value) {
            if (!values.has(key)) return false;
            for (var entry : values.getAsJsonArray(key)) if (entry.getAsString().equals(value)) return true;
            return false;
        }
        public boolean contains(Point p) {
            if (!inRing(rings[0], p)) return false;
            for (int i=1;i<rings.length;i++) if (inRing(rings[i],p)) return false;
            return true;
        }
    }
    public final double blocksPerDegree, offsetX, offsetZ, seaLevel, metresPerBlock;
    public final boolean voidMissingChunks;
    public final Set<String> dimensions;
    public final List<Feature> features;
    public final JsonObject plants, animals;
    public Geography(Reader reader) {
        var root=JsonParser.parseReader(reader).getAsJsonObject();
        if(!root.has("schema_version")||root.get("schema_version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported Earth schema");
        var p=root.getAsJsonObject("projection");
        blocksPerDegree=positive(p,"blocks_per_degree"); offsetX=p.get("offset_x").getAsDouble(); offsetZ=p.get("offset_z").getAsDouble();
        seaLevel=p.get("sea_level").getAsDouble(); metresPerBlock=positive(p,"metres_per_height_block");
        if (!Double.isFinite(offsetX)||!Double.isFinite(offsetZ)||!Double.isFinite(seaLevel)) throw new IllegalArgumentException("Nonfinite projection");
        voidMissingChunks=root.get("void_missing_chunks").getAsBoolean();
        Set<String> dims=new HashSet<>(); root.getAsJsonArray("dimensions").forEach(v->dims.add(v.getAsString())); dimensions=Set.copyOf(dims);
        List<Feature> rows=new ArrayList<>(); Set<String> ids=new HashSet<>();
        for(var element:root.getAsJsonArray("features")) {
            var f=element.getAsJsonObject(); String id=f.get("id").getAsString();
            String layer=f.get("layer").getAsString();
            if(!ids.add(layer+":"+id))throw new IllegalArgumentException("Duplicate geographic ID: "+layer+":"+id);
            if(!Set.of("climate","ecology","geology","marine","deposit").contains(layer))throw new IllegalArgumentException("Unknown layer "+layer);
            double[][][] rings=new Gson().fromJson(f.get("rings"),double[][][].class);
            if(rings.length==0)throw new IllegalArgumentException("Empty polygon "+id);
            for(var ring:rings) {
                if(ring.length<4 || !Arrays.equals(ring[0],ring[ring.length-1]))throw new IllegalArgumentException("Unclosed ring "+id);
                for(var xy:ring)if(xy.length!=2||!Double.isFinite(xy[0])||!Double.isFinite(xy[1])||Math.abs(xy[0])>180||Math.abs(xy[1])>90)throw new IllegalArgumentException("Invalid coordinate "+id);
            }
            var values=f.getAsJsonObject("values");
            if(layer.equals("climate")){
                range(values,"mean_c",-100,80);range(values,"amplitude_c",0,60);range(values,"annual_rain_mm",0,15000);
                if(values.has("monthly_rain_mm")){
                    var months=values.getAsJsonArray("monthly_rain_mm");if(months.size()!=12)throw new IllegalArgumentException("Expected 12 rainfall months "+id);
                    for(var value:months)if(!Double.isFinite(value.getAsDouble())||value.getAsDouble()<0)throw new IllegalArgumentException("Invalid monthly rainfall "+id);
                }
            }
            if(layer.equals("geology")&&values.getAsJsonArray("rocks").size()!=3)throw new IllegalArgumentException("Expected 3 rock layers "+id);
            rows.add(new Feature(id,layer,f.get("label").getAsString(),f.get("priority").getAsInt(),rings,values));
        }
        // Same priority uses ID order, never filesystem/visit order.
        rows.sort(Comparator.comparingInt(Feature::priority).reversed().thenComparing(Feature::id)); features=List.copyOf(rows);
        plants=root.getAsJsonObject("plants"); animals=root.getAsJsonObject("aquatic_animals");
    }
    private static void range(JsonObject o,String key,double min,double max){double n=o.get(key).getAsDouble();if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Invalid "+key);}
    public double monthlyRainfall(Point p,double month){
        var f=at("climate",p);if(f==null||!f.values.has("monthly_rain_mm"))return climate(p).rain/12;
        var values=f.values.getAsJsonArray("monthly_rain_mm");double m=month-Math.floor(month/12)*12;int i=(int)Math.floor(m);double t=m-i;
        return values.get(i).getAsDouble()*(1-t)+values.get((i+1)%12).getAsDouble()*t;
    }
    private static double positive(JsonObject o,String key){double n=o.get(key).getAsDouble();if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException(key);return n;}
    public Point coordinates(double x,double z){return new Point((x-offsetX)/blocksPerDegree,-(z-offsetZ)/blocksPerDegree);}
    public double x(double lon){return offsetX+lon*blocksPerDegree;}
    public double z(double lat){return offsetZ-lat*blocksPerDegree;}
    public boolean inside(Point p){return Math.abs(p.lon)<=180&&Math.abs(p.lat)<=90;}
    public Feature at(String layer,Point p){if(!inside(p))return null;for(var f:features)if(f.layer.equals(layer)&&f.contains(p))return f;return null;}
    public Climate climate(Point p){
        var f=at("climate",p);
        if(f!=null)return new Climate(f.number("mean_c",12),f.number("amplitude_c",12),f.number("annual_rain_mm",700),f.id,true);
        double a=Math.abs(p.lat);
        // Explicitly approximate fallback, not a measured Köppen or weather dataset.
        return new Climate(Math.max(-28,27-a*.57),Math.min(24,a*.34),a<12?2200:a<25?1100:a<35?350:a<60?750:300,"latitude_fallback",true);
    }
    public double temperature(double x,double y,double z,double month){var p=coordinates(x,z);return climate(p).temperature(month,p.lat)-Math.max(0,y-seaLevel)*metresPerBlock*.0065;}
    public static int localMonth(int civilMonth,double latitude){return Math.floorMod(civilMonth+(latitude<0?6:0),12);}
    public String zone(Point p){var f=at("ecology",p);if(f!=null)return f.text("zone",f.id);double a=Math.abs(p.lat);return a<10?"rainforest":a<23?"savanna":a<35?"subtropical":a<55?"mixed_forest":a<65?"taiga":a<75?"tundra":"polar_desert";}
    public String soil(Point p){var f=at("ecology",p);return f==null?"loam":f.text("tfc_soil","loam");}
    public String soilClass(Point p){var f=at("ecology",p);return f==null?"unmapped":f.text("soil_class","unmapped");}
    public boolean oreAllowed(Point p,String ore){for(var f:features)if(f.layer.equals("deposit")&&f.contains(p)&&f.has("ores",ore))return true;return false;}
    public boolean plantAllowed(String id,Point p,double temperature,int month){
        if(!plants.has(id))return true;
        var r=plants.getAsJsonObject(id);
        if(temperature<r.get("min_c").getAsDouble()||temperature>r.get("max_c").getAsDouble())return false;
        double rain=climate(p).rain;
        if(rain<r.get("min_rain_mm").getAsDouble()||rain>r.get("max_rain_mm").getAsDouble())return false;
        var months=r.getAsJsonArray("local_months");
        if(Math.abs(p.lat)<10 && r.has("equatorial_year_round") && r.get("equatorial_year_round").getAsBoolean())return true;
        for(var m:months)if(m.getAsInt()==localMonth(month,p.lat))return true;
        return false;
    }
    public boolean aquaticAllowed(String id,Point p,String habitat,double depth){
        if(!animals.has(id))return true;
        var rule=animals.getAsJsonObject(id);
        boolean matches=rule.get("habitat").getAsString().equals(habitat);
        if(rule.has("habitats"))for(var entry:rule.getAsJsonArray("habitats"))if(entry.getAsString().equals(habitat))matches=true;
        if(!matches)return false;
        if(depth<rule.get("min_depth").getAsDouble()||depth>rule.get("max_depth").getAsDouble())return false;
        var basin=at("marine",p);
        if(habitat.equals("fresh"))return true;
        if(basin==null)return false; // Do not silently treat unknown water as an ocean.
        if(basin.id.equals("black_sea")&&depth>150)return false; // Only configured aquatic species, never underground land mobs.
        for(var name:rule.getAsJsonArray("basins"))if(name.getAsString().equals(basin.id))return true;
        return false;
    }
    private static boolean inRing(double[][] ring,Point p){
        boolean in=false;
        for(int i=0,j=ring.length-1;i<ring.length;j=i++){
            double ax=ring[j][0],ay=ring[j][1],bx=ring[i][0],by=ring[i][1];
            double cross=(p.lon-ax)*(by-ay)-(p.lat-ay)*(bx-ax);
            if(Math.abs(cross)<1e-10 && p.lon>=Math.min(ax,bx)&&p.lon<=Math.max(ax,bx)&&p.lat>=Math.min(ay,by)&&p.lat<=Math.max(ay,by))return true;
            if((ay>p.lat)!=(by>p.lat)&&p.lon<(bx-ax)*(p.lat-ay)/(by-ay)+ax)in=!in;
        }
        return in;
    }
}
