package ua.empirecraft.siegeworks;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;
/** Read-only live blueprint view. Counts are the CLEAN-quality baseline, without crafting shortcuts. */
public record Plan(String id,ItemStack result,ItemStack blueprint,List<Input> materials,int stages) {
 public record Input(Ingredient ingredient,int count){}
 public static Map<?,?> definitions(){try{return (Map<?,?>)Class.forName("me.mss1r.axiomata.blueprint.api.definition.BlueprintDefinitions").getMethod("allById").invoke(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 static Object get(Object object,String name)throws ReflectiveOperationException{return object.getClass().getMethod(name).invoke(object);}
 public static List<Plan> all(){try{List<Plan> result=new ArrayList<>();Set<String> seen=new HashSet<>();for(var entry:definitions().entrySet()){
  String id=entry.getKey().toString();if(!id.startsWith("siegeworks:"))continue;Object def=entry.getValue(),output=get(def,"result");var item=(ResourceLocation)get(output,"item");if(!seen.add(item.toString()))continue;
  var materials=new LinkedHashMap<String,Integer>();var stages=(List<?>)get(def,"stages");for(var stage:stages)for(var material:(List<?>)get(stage,"materials"))materials.merge((String)get(material,"key"),(Integer)get(material,"count"),Integer::sum);
  List<Input> inputs=new ArrayList<>();for(var in:materials.entrySet()){String key=in.getKey();Ingredient ingredient=key.startsWith("#")?Ingredient.of(TagKey.create(Registries.ITEM,new ResourceLocation(key.substring(1)))):Ingredient.of(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(new ResourceLocation(key))));inputs.add(new Input(ingredient,in.getValue()));}
  var blueprint=(ItemStack)Class.forName("me.mss1r.axiomata.blueprint.api.BlueprintStacks").getMethod("create",String.class).invoke(null,id);var quality=Class.forName("me.mss1r.axiomata.blueprint.api.construction.BuildQuality");Object clean=Enum.valueOf((Class)quality,"CLEAN");Class.forName("me.mss1r.axiomata.blueprint.item.BlueprintItem").getMethod("stampQuality",ItemStack.class,String.class,quality).invoke(null,blueprint,"",clean);
  result.add(new Plan(id,new ItemStack(Objects.requireNonNull(ForgeRegistries.ITEMS.getValue(item)),(Integer)get(output,"count")),blueprint,List.copyOf(inputs),stages.size()));
 }return List.copyOf(result);}catch(ReflectiveOperationException e){throw new IllegalStateException("Axiomata blueprint API changed",e);}}
}
