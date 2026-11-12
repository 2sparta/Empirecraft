package ua.empirecraft.tfc;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Runs before deserialization, so retired recipes cannot crash loading. */
public final class RecipePolicy {
    private static final JsonObject DATA;
    public static final Set<String> FORBIDDEN;
    public static final Map<String,String> ALIASES;
    private static final Set<String> GUNS;
    static {
        try (var in=RecipePolicy.class.getResourceAsStream("/empirecraft-tfc-policy.json")) {
            if (in==null) throw new IllegalStateException("Missing integration policy");
            DATA=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(IOException ex) { throw new ExceptionInInitializerError(ex); }
        Set<String> banned=new HashSet<>(); DATA.getAsJsonArray("forbidden").forEach(v->banned.add(v.getAsString()));
        FORBIDDEN=Set.copyOf(banned);
        Map<String,String> mapping=new HashMap<>(); DATA.getAsJsonObject("aliases").entrySet().forEach(e->mapping.put(e.getKey(),e.getValue().getAsString()));
        ALIASES=Map.copyOf(mapping);
        Set<String> guns=new HashSet<>(); DATA.getAsJsonArray("guns").forEach(v->guns.add(v.getAsString())); GUNS=Set.copyOf(guns);
    }
    private RecipePolicy() {}
    public static boolean forbidden(String id) { return FORBIDDEN.contains(id); }
    public static Set<String> overrideIds(){return Collections.unmodifiableSet(DATA.getAsJsonObject("overrides").keySet());}
    public static boolean contains(JsonElement data,Set<String> ids) {
        if (data.isJsonPrimitive() && data.getAsJsonPrimitive().isString()) return ids.contains(data.getAsString());
        if (data.isJsonArray()) for (JsonElement child:data.getAsJsonArray()) if(contains(child,ids))return true;
        if (data.isJsonObject()) for(var entry:data.getAsJsonObject().entrySet()) if(contains(entry.getValue(),ids))return true;
        return false;
    }
    public static JsonElement canonical(JsonElement data) {
        if(data.isJsonPrimitive() && data.getAsJsonPrimitive().isString())return new JsonPrimitive(ALIASES.getOrDefault(data.getAsString(),data.getAsString()));
        if(data.isJsonArray()) { JsonArray a=new JsonArray();data.getAsJsonArray().forEach(v->a.add(canonical(v)));return a; }
        if(data.isJsonObject()) { JsonObject a=new JsonObject();data.getAsJsonObject().entrySet().forEach(e->a.add(e.getKey(),canonical(e.getValue())));return a; }
        return data.deepCopy();
    }
    public static <K> void rewrite(Map<K,JsonElement> recipes,Function<String,K> key) {
        for(var iterator=recipes.entrySet().iterator();iterator.hasNext();) {
            var entry=iterator.next();JsonElement data=entry.getValue();
            if(!data.isJsonObject())continue;
            JsonObject object=data.getAsJsonObject();String type=object.has("type")?object.get("type").getAsString():"";
            boolean resultForbidden=object.has("result")&&contains(object.get("result"),Set.of("minecraft:fishing_rod"));
            boolean firearm=object.has("result")&&contains(object.get("result"),GUNS);
            boolean oldStone=object.has("result")&&contains(object.get("result"),Set.of("minecraft:stone_axe","minecraft:stone_pickaxe","minecraft:stone_shovel","minecraft:stone_hoe","minecraft:stone_sword"));
            if(contains(data,FORBIDDEN)||Set.of("tfc:anvil","tfc:welding","tfc:collapse","tfc:landslide","overgeared:rock_knapping").contains(type)
                    ||resultForbidden||firearm||oldStone)iterator.remove();
            else entry.setValue(canonical(data));
        }
        for(var entry:DATA.getAsJsonObject("overrides").entrySet()) recipes.put(key.apply(entry.getKey()),entry.getValue().deepCopy());
    }
}
