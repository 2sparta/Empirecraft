package guardtest;

import java.util.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import ua.empirecraft.forging.*;

public final class EquipmentRulesTest {
    private static void near(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000001) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        String[] names = {"poor", "well", "expert", "perfect", "master", "", "unknown"};
        double[] armor = {-1.5, -0.5, 0, 0.5, 1, 0, 0};
        double[] weapon = {-1.5, -0.5, 0, 0.5, 2, 0, 0};
        double[] durability = {0.25, 2.0/3.0, 1, 2, 4, 1, 1};
        for (int i = 0; i < names.length; ++i) {
            near(armor[i], QualityStats.bonus(names[i], false));
            near(weapon[i], QualityStats.bonus(names[i], true));
            near(durability[i], QualityStats.durability(names[i]));
            near(10 + weapon[i], RangedQuality.damage(10, weapon[i]));
            // A 300-durability item gives 75/200/300/600/1200 uses, with native integer truncation.
            near(Math.floor(300 * durability[i] + 0.00001), (int)(300 * QualityStats.durability(names[i])));
        }
        near(0, RangedQuality.damage(1, -1.5));
        near(4, ArmorBalance.bonus(9));
        near(4, ArmorBalance.bonus(10));
        near(2, ArmorBalance.bonus(8));
        near(1.5, ArmorBalance.bonus(7));
        near(1, ArmorBalance.bonus(6));
        near(1, ArmorBalance.bonus(3));
        var recipes = new LinkedHashMap<ResourceLocation, JsonElement>();
        assert KnightItems.REPLACEMENTS.size() == 41;
        assert KnightItems.REPLACEMENTS.get("magistuarmory:steel_plate").equals("overgeared:steel_plate");
        assert KnightItems.REPLACEMENTS.get("magistuarmory:diamond_claymore").equals("magistuarmory:steel_claymore");
        assert KnightItems.REPLACEMENTS.get("magistuarmory:netherite_heavywarhammer").equals("magistuarmory:steel_heavywarhammer");
        for (String kept : List.of("magistuarmory:small_steel_plate", "magistuarmory:steel_claymore", "magistuarmory:knight_chestplate", "magistuarmory:diamond_buckler")) assert !KnightItems.retired(kept);
        assert KnightItems.damage(100, 400, 200) == 50;
        assert KnightItems.damage(400, 400, 200) == 199;
        recipes.put(new ResourceLocation("test", "plate"), JsonParser.parseString("{\"type\":\"minecraft:crafting_shaped\",\"result\":{\"item\":\"magistuarmory:steel_plate\",\"count\":2}}"));
        recipes.put(new ResourceLocation("test", "knight"), JsonParser.parseString("{\"type\":\"overgeared:forging\",\"key\":{\"P\":{\"item\":\"magistuarmory:steel_plate\"}},\"result\":{\"item\":\"magistuarmory:knight_chestplate\"}}"));
        recipes.put(new ResourceLocation("test", "diamond"), JsonParser.parseString("{\"type\":\"minecraft:crafting_shaped\",\"result\":{\"item\":\"magistuarmory:diamond_claymore\"}}"));
        recipes.put(new ResourceLocation("test", "netherite"), JsonParser.parseString("{\"type\":\"minecraft:smithing_transform\",\"result\":{\"item\":\"magistuarmory:netherite_heavywarhammer\"}}"));
        recipes.put(new ResourceLocation("test", "steel"), JsonParser.parseString("{\"type\":\"overgeared:forging\",\"result\":{\"item\":\"overgeared:steel_chestplate\"}}"));
        recipes.put(new ResourceLocation("test", "types"), JsonParser.parseString("{\"type\":\"overgeared:item_to_tooltype\",\"tooltype\":\"armor\",\"item\":[{\"item\":\"overgeared:steel_boots\"},{\"item\":\"overgeared:copper_boots\"}]}"));
        recipes.put(new ResourceLocation("test", "arrows"), JsonParser.parseString("{\"type\":\"overgeared:fletching\",\"result\":{\"item\":\"overgeared:iron_arrow\"},\"result_tipped\":{\"item\":\"minecraft:tipped_arrow\"},\"result_lingering\":{\"item\":\"overgeared:lingering_arrow\"}}"));
        for (String name : EquipmentRecipes.MAXIMILIAN) recipes.put(new ResourceLocation("magistuarmory", name), JsonParser.parseString("{\"type\":\"minecraft:smithing_transform\"}"));
        EquipmentRecipes.rewrite(recipes);
        assert !recipes.containsKey(new ResourceLocation("test", "plate"));
        assert !recipes.containsKey(new ResourceLocation("test", "diamond"));
        assert !recipes.containsKey(new ResourceLocation("test", "netherite"));
        assert recipes.get(new ResourceLocation("test", "knight")).getAsJsonObject().getAsJsonObject("key").getAsJsonObject("P").get("item").getAsString().equals("overgeared:steel_plate");
        assert !recipes.containsKey(new ResourceLocation("test", "steel"));
        var types = recipes.get(new ResourceLocation("test", "types")).getAsJsonObject().getAsJsonArray("item");
        assert types.size() == 1 && types.get(0).getAsJsonObject().get("item").getAsString().equals("overgeared:copper_boots");
        var arrows = recipes.get(new ResourceLocation("test", "arrows")).getAsJsonObject();
        assert !arrows.has("result_tipped") && !arrows.has("result_lingering") && arrows.getAsJsonObject("result").get("item").getAsString().equals("overgeared:iron_arrow");
        for (String name : EquipmentRecipes.MAXIMILIAN) {
            var data = recipes.get(new ResourceLocation("magistuarmory", name)).getAsJsonObject();
            assert data.get("type").getAsString().equals("overgeared:forging");
            assert data.get("has_quality").getAsBoolean() && data.get("needs_minigame").getAsBoolean();
            assert data.get("tier").getAsString().equals("iron");
            assert data.getAsJsonArray("pattern").toString().equals("[\"P P\",\"PKP\",\"PBP\"]");
            assert data.getAsJsonObject("key").getAsJsonObject("K").get("transfer_nbt").getAsBoolean();
            assert data.getAsJsonObject("key").getAsJsonObject("B").get("item").getAsString().equals("overgeared:steel_block");
        }
        var before = new LinkedHashMap<>(recipes);
        EquipmentRecipes.rewrite(recipes);
        assert before.equals(recipes) : "reload is not idempotent";
        if (args.length > 0) nativeKnights(args[0]);
        System.out.println("PASS five equipment qualities, base/unknown quality, durability rounding, recipe removal, mixed tooltype preservation, potion variants, four Maximilian minigames and repeated reload");
    }
    private static void nativeKnights(String path) {
        int retired = 0, plateCrafts = 0;boolean nativeOverlay=false;
        var nativeRecipes = new LinkedHashMap<ResourceLocation, JsonElement>();
        try (var jar = new java.util.zip.ZipFile(path)) {
            nativeOverlay=jar.getEntry("META-INF/empirecraft-native-content.json")!=null;
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (!entry.getName().matches("data/[^/]+/recipes/.+\\.json")) continue;
                // The pinned upstream JAR ships this truncated, unrelated Japanese recipe.
                // Minecraft skips it during JSON resource preparation as well.
                if (entry.getName().equals("data/epic_knights__japanese_armory/recipes/sai_2.json")) continue;
                String name = entry.getName().substring(5).replace("/recipes/", ":").replace(".json", "");
                try (var reader = new java.io.InputStreamReader(jar.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
                    var data = JsonParser.parseReader(reader).getAsJsonObject();
                    if (KnightItems.retiredOutput(data)) {
                        retired++;
                        if (data.getAsJsonObject("result").get("item").getAsString().equals(KnightItems.PLATE)) plateCrafts++;
                    }
                    nativeRecipes.put(new ResourceLocation(name), data);
                }
            }
            for (var id : KnightItems.REPLACEMENTS.keySet()) {
                String asset = "assets/" + id.replace(':', '/') + ".json";
                asset = asset.replace("magistuarmory/", "magistuarmory/models/item/");
                assert (jar.getEntry(asset)!=null)==(!nativeOverlay || !id.startsWith("magistuarmory:netherite_")) : "Retired model does not match native overlay: " + id;
            }
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        assert retired == (nativeOverlay?21:41) && plateCrafts == 1 : retired + " retired native recipes";
        EquipmentRecipes.rewrite(nativeRecipes);
        for (var data : nativeRecipes.values()) {
            assert !EquipmentRecipes.forbidden(data);
            assert !data.toString().contains("\"magistuarmory:steel_plate\"");
        }
        assert nativeRecipes.containsKey(new ResourceLocation("magistuarmory", "knight_chestplate"));
        assert nativeRecipes.containsKey(new ResourceLocation("magistuarmory", "steel_buckler"));
        assert !nativeRecipes.containsKey(new ResourceLocation("magistuarmory", "diamond_buckler"));
        assert !nativeRecipes.containsKey(new ResourceLocation("magistuarmory", "netherite_buckler"));
        System.out.println("PASS pinned Epic Knights JAR: all 41 retired item models and recipes, plate inputs unified, knight armor and steel shields retained; native diamond/netherite shield removal preserved");
    }
}


