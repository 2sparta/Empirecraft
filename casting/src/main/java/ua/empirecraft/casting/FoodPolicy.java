package ua.empirecraft.casting;
import com.google.gson.*;import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;
/** Shared by initial recipe loading and the final KubeJS recipe pass. */
public final class FoodPolicy {
 private static final JsonObject DATA=load();
 private static JsonObject load(){try(var in=FoodPolicy.class.getResourceAsStream("/food-policy.json")){return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 public static boolean duplicateTool(String id){return id!=null&&(id.matches("tfc:metal/(?:pickaxe|axe|shovel|hoe)/wrought_iron"));}
 public static boolean removed(String id){return id!=null&&(duplicateTool(id)||id.matches("tfc:.*(?:red_steel|blue_steel|black_bronze|bismuth_bronze).*")||id.matches("tfc:(?:metal/(?:unfinished_)?(?:helmet|chestplate|greaves|boots)/.*|(?:chainmail|leather)_(?:helmet|chestplate|leggings|boots))")||armor(id)||id.equals("create:dough")||id.equals("farmersdelight:wheat_dough"));}
 public static java.util.List<String> retiredItemIds(){return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKeys().stream().map(Object::toString).filter(FoodPolicy::removed).toList();}
 private static boolean armor(String id){if(!id.startsWith("tfc:"))return false;var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(id));return item instanceof net.minecraft.world.item.ArmorItem||item instanceof net.minecraft.world.item.HorseArmorItem;}
 private static boolean forbidden(JsonElement e){if(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString())return removed(e.getAsString());if(e.isJsonArray())for(var v:e.getAsJsonArray())if(forbidden(v))return true;if(e.isJsonObject())for(var v:e.getAsJsonObject().entrySet())if(forbidden(v.getValue()))return true;return false;}
 private static JsonElement input(JsonElement e){
  if(e.isJsonArray()){var a=new JsonArray();for(var v:e.getAsJsonArray())a.add(input(v));return a;}
  if(!e.isJsonObject())return e.deepCopy();var o=e.getAsJsonObject();
  if(o.has("item")&&o.size()==1){String id=o.get("item").getAsString();var groups=DATA.getAsJsonObject("ingredients");if(groups.has(id))return groups.get(id).deepCopy();}
  if(o.has("tag")&&o.size()==1&&Set.of("forge:dough","forge:dough/wheat","forge:flour","forge:flour/wheat").contains(o.get("tag").getAsString())){var tag=new JsonObject();tag.addProperty("tag",o.get("tag").getAsString().contains("dough")?"empirecraft_casting:food/dough":"empirecraft_casting:food/flour");return tag;}
  var result=new JsonObject();for(var v:o.entrySet())result.add(v.getKey(),input(v.getValue()));return result;
 }
 private static JsonElement outputs(JsonElement e){if(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString()&&e.getAsString().equals("create:wheat_flour"))return new JsonPrimitive("tfc:food/wheat_flour");if(e.isJsonArray()){var a=new JsonArray();for(var v:e.getAsJsonArray())a.add(outputs(v));return a;}if(e.isJsonObject()){var o=new JsonObject();for(var v:e.getAsJsonObject().entrySet())o.add(v.getKey(),outputs(v.getValue()));return o;}return e.deepCopy();}
 public static JsonObject recipe(JsonObject json){
  String type=json.has("type")?json.get("type").getAsString():"";if(type.equals("farmersdelight:dough"))return null;
  var result=CrucibleMaterials.canonical(json).getAsJsonObject();if(CrucibleMaterials.furnaceBypass(result))return null;
  if(type.equals("overgeared:item_to_tooltype")&&result.has("item")&&result.get("item").isJsonArray()){var kept=new JsonArray();for(var item:result.getAsJsonArray("item"))if(!forbidden(item))kept.add(item);if(kept.isEmpty())return null;result.add("item",kept);}

  for(String field:List.of("result","results","output","result_item"))if(result.has(field)){if(forbidden(result.get(field)))return null;result.add(field,outputs(result.get(field)));}
  // Native TFC grain-specific dough/food recipes retain their original identities.
  if(!type.startsWith("tfc:"))for(String field:List.of("ingredients","ingredient","key","input","inputs","base","addition"))if(result.has(field))result.add(field,input(result.get(field)));
  if(forbidden(result))return null;return result;
 }
 public static <K> void rewrite(Map<K,JsonElement> recipes){recipes.entrySet().removeIf(e->{if(!e.getValue().isJsonObject())return false;var value=recipe(e.getValue().getAsJsonObject());if(value==null)return true;e.setValue(value);return false;});}
}
