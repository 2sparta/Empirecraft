package net.stirdrem.overgeared.empirecraft;

import com.google.gson.*;
import java.util.*;

/** Runs before deserialization so external datapacks cannot reference removed native IDs. */
public final class RecipePolicy {
    public static final Set<String> REMOVED = Set.of("overgeared:nether_alloy_furnace", "overgeared:casting_furnace",
        "overgeared:diamond_upgrade_smithing_template", "overgeared:netherite_alloy", "overgeared:heated_netherite_alloy",
        "overgeared:diamond_arrow", "overgeared:diamond_shard", "overgeared:nether_tool_cast");
    public static final Set<String> TYPES = Set.of("overgeared:casting", "overgeared:nether_alloy_smelting", "overgeared:shaped_nether_alloy_smelting");
    private RecipePolicy() {}
    public static boolean references(JsonElement value) {
        if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())return REMOVED.contains(value.getAsString());
        if(value.isJsonArray())for(var child:value.getAsJsonArray())if(references(child))return true;
        if(value.isJsonObject())for(var entry:value.getAsJsonObject().entrySet())if(references(entry.getValue()))return true;
        return false;
    }
    public static void filter(Map<?,JsonElement> recipes) {
        var iterator=recipes.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(!entry.getValue().isJsonObject())continue;
            var data=entry.getValue().getAsJsonObject();
            String type=data.has("type")&&data.get("type").isJsonPrimitive()?data.get("type").getAsString():"";
            if(TYPES.contains(type)){iterator.remove();continue;}
            if(type.equals("overgeared:item_to_tooltype")&&data.has("item")&&data.get("item").isJsonArray()) {
                var copy=data.deepCopy();var kept=new JsonArray();
                for(var ingredient:copy.getAsJsonArray("item"))if(!references(ingredient))kept.add(ingredient);
                if(kept.isEmpty()){iterator.remove();continue;}
                copy.add("item",kept);data=copy;entry.setValue(copy);
            }
            if(references(data))iterator.remove();
        }
    }
}
