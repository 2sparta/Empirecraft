package ua.empirecraft.wildlife;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pure queries shared by event gates, geographic candidates and offline regression tests. */
public final class Policy {
    public final JsonObject json;
    public final Geography earth;
    public final Set<String> removed, hidden;
    private final Map<String,Geography.Feature> regions=new HashMap<>();
    public Policy(Reader rules, Reader geography) {
        json=JsonParser.parseReader(rules).getAsJsonObject();
        if(json.get("schema_version").getAsInt()!=1)throw new IllegalArgumentException("Wildlife schema");
        earth=new Geography(geography);
        for(var entry:json.getAsJsonObject("regions").entrySet()) {
            double[][] ring=new Gson().fromJson(entry.getValue(),double[][].class);
            regions.put(entry.getKey(),new Geography.Feature(entry.getKey(),"ecology",entry.getKey(),0,new double[][][]{ring},new JsonObject()));
        }
        Set<String> cut=new HashSet<>(json.getAsJsonObject("removed").keySet());
        json.getAsJsonArray("other_removed").forEach(v->cut.add(v.getAsString()));
        removed=Set.copyOf(cut);
        Set<String> items=new HashSet<>();json.getAsJsonArray("hidden_items").forEach(v->items.add(v.getAsString()));hidden=Set.copyOf(items);
        for(var entry:animals().entrySet()) {
            if(removed.contains(entry.getKey()))throw new IllegalArgumentException("Kept and removed: "+entry.getKey());
            var row=entry.getValue().getAsJsonObject();
            for(var name:row.getAsJsonArray("regions"))if(!json.getAsJsonObject("regions").has(name.getAsString()))throw new IllegalArgumentException("Unknown range "+name);
            if(row.get("min").getAsInt()<1||row.get("max").getAsInt()<row.get("min").getAsInt()||row.get("max").getAsInt()>8||row.get("weight").getAsInt()<1)throw new IllegalArgumentException("Invalid group "+entry.getKey());
        }
    }
    public JsonObject animals(){return json.getAsJsonObject("animals");}
    public boolean removed(String id){return removed.contains(id);}
    public boolean managed(String id){return animals().has(id);}
    private static boolean contains(JsonArray values,String value){for(var v:values)if(v.getAsString().equals(value))return true;return false;}
    public boolean allowed(String id,String dimension,double x,double y,double z,String habitat,double depth) {
        if(removed(id))return false;
        if(!managed(id))return true;
        // No unrestricted fallback in another dimension: these are Earth wildlife profiles.
        if(!earth.dimensions.contains(dimension))return false;
        var point=earth.coordinates(x,z);if(!earth.inside(point))return false;
        var row=animals().getAsJsonObject(id);var ranges=row.getAsJsonArray("regions");
        if(row.has("min_elevation")&&y<row.get("min_elevation").getAsDouble())return false;
        if(!ranges.isEmpty()) {
            boolean match=false;
            for(var name:ranges) {
                if(regions.get(name.getAsString()).contains(point)){match=true;break;}
            }
            if(!match)return false;
        }
        String required=row.get("habitat").getAsString();
        if(required.equals("land")&&!habitat.equals("land"))return false;
        if(required.equals("marine")&&!habitat.equals("marine"))return false;
        if(required.equals("fresh")&&!habitat.equals("fresh")&&!habitat.equals("riverbank"))return false;
        if(required.equals("wetland")&&!Set.of("fresh","riverbank","wetland").contains(habitat))return false;
        if(required.equals("coast")&&!habitat.equals("coast"))return false;
        if(required.equals("marine")) {
            var basin=earth.at("marine",point);
            if(basin==null||Set.of("black_sea","azov","caspian","baltic").contains(basin.id()))return false;
            if(depth<row.get("min_depth").getAsDouble()||depth>row.get("max_depth").getAsDouble())return false;
            if(Set.of("alexsmobs:hammerhead_shark","alexsmobs:flying_fish").contains(id)&&Math.abs(point.lat())>40)return false;
        }
        var zones=row.getAsJsonArray("zones");
        return zones.isEmpty()||contains(zones,earth.zone(point));
    }
    public static Reader resource(String name){
        var in=Objects.requireNonNull(Policy.class.getResourceAsStream("/"+name),name);
        return new InputStreamReader(in,StandardCharsets.UTF_8);
    }
}
