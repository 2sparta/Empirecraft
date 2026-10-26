package ua.empirecraft.forging;

import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;

/** Runs inside RecipeManager before deserialization, on every resource reload. */
public final class EquipmentRecipes {
    public static final List<String> MAXIMILIAN = List.of("maximilian_helmet_smithing", "maximilan_chestplate_smithing",
        "maximilan_leggings_smithing", "maximilan_boots_smithing");
    private EquipmentRecipes() {}
    public static void rewrite(Map<ResourceLocation, JsonElement> recipes) {
        var iterator = recipes.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject data = entry.getValue().getAsJsonObject().deepCopy();
            String type = data.has("type") ? data.get("type").getAsString() : "";
            if (type.equals("overgeared:item_to_tooltype") && data.has("item") && data.get("item").isJsonArray()) {
                JsonArray kept = new JsonArray();
                for (JsonElement ingredient : data.getAsJsonArray("item")) if (!forbidden(ingredient)) kept.add(ingredient);
                if (kept.isEmpty()) { iterator.remove(); continue; }
                data.add("item", kept);
            }
            if (type.equals("overgeared:fletching")) {
                data.remove("result_tipped"); data.remove("result_lingering");
                // A recipe requiring a potion is not a plain-arrow recipe.
                if (data.has("potion")) { iterator.remove(); continue; }
            }
            if (forbidden(data)) iterator.remove();
            else entry.setValue(data);
        }
        for (String recipe : MAXIMILIAN) {
            String path = "data/magistuarmory/recipes/" + recipe + ".json";
            try (var in = EquipmentRecipes.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) throw new IllegalStateException("Missing built-in recipe: " + path);
                var data = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                recipes.put(new ResourceLocation("magistuarmory", recipe), data);
            } catch (IOException error) { throw new UncheckedIOException(error); }
        }
    }
    public static boolean forbidden(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String id = value.getAsString();
            return EquipmentContent.STEEL_ARMOR.contains(id) || id.equals("overgeared:lingering_arrow") || id.equals("minecraft:tipped_arrow");
        }
        if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) if (forbidden(child)) return true;
        if (value.isJsonObject()) for (var child : value.getAsJsonObject().entrySet()) if (forbidden(child.getValue())) return true;
        return false;
    }
}
