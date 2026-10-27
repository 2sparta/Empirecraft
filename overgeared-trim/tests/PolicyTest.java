import com.google.gson.*;
import java.util.*;
import net.stirdrem.overgeared.empirecraft.RecipePolicy;

public final class PolicyTest {
    public static void main(String[] args) {
        Map<String,JsonElement> recipes=new HashMap<>();
        recipes.put("keep",JsonParser.parseString("{\"type\":\"overgeared:alloy_smelting\",\"result\":{\"item\":\"overgeared:steel_ingot\"}}"));
        recipes.put("external",JsonParser.parseString("{\"type\":\"overgeared:fletching\",\"result\":{\"item\":\"overgeared:diamond_arrow\"}}"));
        recipes.put("machine",JsonParser.parseString("{\"type\":\"overgeared:casting\",\"result\":{\"item\":\"minecraft:iron_ingot\"}}"));
        recipes.put("nested",JsonParser.parseString("{\"type\":\"forge:conditional\",\"recipes\":[{\"recipe\":{\"result\":\"overgeared:netherite_alloy\"}}]}"));
        recipes.put("mixed",JsonParser.parseString("{\"type\":\"overgeared:item_to_tooltype\",\"item\":[{\"item\":\"overgeared:nether_tool_cast\"},{\"item\":\"overgeared:clay_tool_cast\"}]}"));
        var original=recipes.get("keep");RecipePolicy.filter(recipes);
        if(!recipes.keySet().equals(Set.of("keep","mixed"))||recipes.get("keep")!=original)throw new AssertionError(recipes);
        if(recipes.get("mixed").getAsJsonObject().getAsJsonArray("item").size()!=1)throw new AssertionError("Retained tool alternative");
        RecipePolicy.filter(recipes);
        if(recipes.size()!=2)throw new AssertionError("Reload must be idempotent");
        System.out.println("PASS recipe filter: external and nested recipes, mixed alternatives, retained alloying, repeated reload");
    }
}
