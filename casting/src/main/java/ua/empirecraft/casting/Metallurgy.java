package ua.empirecraft.casting;
import java.util.*;import java.util.function.Function;import java.io.*;import java.nio.charset.StandardCharsets;
import com.google.gson.*;import net.minecraft.world.item.ItemStack;import net.minecraftforge.items.*;
public final class Metallurgy {
 private static final JsonObject DATA=load();
 private static final java.util.regex.Pattern RETIRED=java.util.regex.Pattern.compile(".*(?:/|:)(?:high_carbon_|weak_)?(?:red_steel|blue_steel|black_bronze)(?:/.*)?");
 private static JsonObject load(){try(var in=Metallurgy.class.getResourceAsStream("/metallurgy.json")){return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
 public static boolean retired(String id){return id!=null&&((RETIRED.matcher(id).matches()||FoodPolicy.removed(id))||id.equals("overgeared:crude_steel")||id.equals("overgeared:heated_crude_steel")||Thermal.legacy(id));}
 private static boolean contains(JsonElement value){if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString())return retired(value.getAsString());if(value.isJsonArray())for(var child:value.getAsJsonArray())if(contains(child))return true;if(value.isJsonObject())for(var e:value.getAsJsonObject().entrySet())if(contains(e.getValue()))return true;return false;}
 private static String result(JsonObject value){var output=value.has("result")?value.get("result"):value.get("output");if(output==null)return "";if(output.isJsonObject()&&output.getAsJsonObject().has("stack"))output=output.getAsJsonObject().get("stack");if(output.isJsonPrimitive())return output.getAsString();if(output.isJsonObject()&&output.getAsJsonObject().has("item"))return output.getAsJsonObject().get("item").getAsString();return "";}
 public static <K> void rewrite(Map<K,JsonElement> recipes,Function<String,K> key){
  var assembly=new HashSet<String>();DATA.getAsJsonArray("assembly_outputs").forEach(v->assembly.add(v.getAsString()));
  var forged=new HashSet<String>();DATA.getAsJsonArray("forged_outputs").forEach(v->forged.add(v.getAsString()));
  recipes.entrySet().removeIf(e->{var value=Thermal.canonical(e.getValue());e.setValue(value);if(contains(value))return true;if(!value.isJsonObject())return false;var recipe=value.getAsJsonObject();String out=result(recipe),type=recipe.has("type")?recipe.get("type").getAsString():"";
   return assembly.contains(out)||forged.contains(out)&&type.equals("create:cutting")||e.getKey().toString().equals("empirecraft_tfc:refining/metal/ingot/high_carbon_steel");});
  for(var e:DATA.getAsJsonObject("recipes").entrySet())recipes.put(key.apply(e.getKey()),e.getValue().deepCopy());
  // Canonicalize surviving legacy heat recipes after integration policy and remove old cooling.
  recipes.entrySet().removeIf(e->e.getValue().isJsonObject()&&(e.getValue().getAsJsonObject().has("type")&&e.getValue().getAsJsonObject().get("type").getAsString().equals("overgeared:cooling")||Set.of("medieval_boomsticks:arquebus","medieval_boomsticks:round_ball").contains(result(e.getValue().getAsJsonObject()))));
  for(var e:recipes.entrySet())e.setValue(Thermal.canonical(e.getValue()));
  try(var in=Metallurgy.class.getResourceAsStream("/thermal-recipes.json")){var thermal=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();for(var e:thermal.entrySet())recipes.put(key.apply(e.getKey()),e.getValue().deepCopy());}catch(IOException e){throw new IllegalStateException(e);}
  recipes.entrySet().removeIf(e->e.getValue().isJsonObject()&&Set.of("medieval_boomsticks:javelin","medieval_boomsticks:iron_throwing_knife","musketmod:cartridge").contains(result(e.getValue().getAsJsonObject())));
  try(var in=Metallurgy.class.getResourceAsStream("/content-recipes.json")){var content=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();for(var e:content.entrySet())recipes.put(key.apply(e.getKey()),e.getValue().deepCopy());}catch(IOException e){throw new IllegalStateException(e);}
  recipes.entrySet().removeIf(e->e.getKey().toString().startsWith("sulfur_gunpowder:"));
  FoodPolicy.rewrite(recipes);
 }
 public static final class Port implements IItemHandler {
  private final IItemHandler inventory;private final boolean extract;public Port(IItemHandler inventory,boolean extract){this.inventory=inventory;this.extract=extract;}
  public int getSlots(){return inventory.getSlots();}public ItemStack getStackInSlot(int slot){return inventory.getStackInSlot(slot);}
  public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return inventory.insertItem(slot,stack,simulate);}
  public ItemStack extractItem(int slot,int amount,boolean simulate){return extract&&slot==9?inventory.extractItem(slot,amount,simulate):ItemStack.EMPTY;}
  public int getSlotLimit(int slot){return inventory.getSlotLimit(slot);}public boolean isItemValid(int slot,ItemStack stack){return inventory.isItemValid(slot,stack);}
 }
}
