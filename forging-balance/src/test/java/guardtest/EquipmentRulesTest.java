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
        var recipes = new LinkedHashMap<ResourceLocation, JsonElement>();
        recipes.put(new ResourceLocation("test", "steel"), JsonParser.parseString("{\"type\":\"overgeared:forging\",\"result\":{\"item\":\"overgeared:steel_chestplate\"}}"));
        recipes.put(new ResourceLocation("test", "types"), JsonParser.parseString("{\"type\":\"overgeared:item_to_tooltype\",\"tooltype\":\"armor\",\"item\":[{\"item\":\"overgeared:steel_boots\"},{\"item\":\"overgeared:copper_boots\"}]}"));
        recipes.put(new ResourceLocation("test", "arrows"), JsonParser.parseString("{\"type\":\"overgeared:fletching\",\"result\":{\"item\":\"overgeared:iron_arrow\"},\"result_tipped\":{\"item\":\"minecraft:tipped_arrow\"},\"result_lingering\":{\"item\":\"overgeared:lingering_arrow\"}}"));
        for (String name : EquipmentRecipes.MAXIMILIAN) recipes.put(new ResourceLocation("magistuarmory", name), JsonParser.parseString("{\"type\":\"minecraft:smithing_transform\"}"));
        EquipmentRecipes.rewrite(recipes);
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
        System.out.println("PASS five equipment qualities, base/unknown quality, durability rounding, recipe removal, mixed tooltype preservation, potion variants, four Maximilian minigames and repeated reload");
    }
}
