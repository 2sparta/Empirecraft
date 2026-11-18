package ua.empirecraft.armory;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

/** Read-only JEI descriptions of the existing server workshop, never executable crafting recipes. */
public record GunsmithManual(int selection,boolean researched,ItemStack output,List<ItemStack> materials,List<Ingredient> tools,int operations) {
    public ItemStack blueprint(){return stack("empirecraft_forging:"+(selection==6?"wheellock_blueprint":"flintlock_blueprint"),1);}
    private static Object definition(String name)throws ReflectiveOperationException{var field=Class.forName("ua.empirecraft.forging.Gunsmith").getDeclaredField(name);field.setAccessible(true);return field.get(null);}
    private static ItemStack stack(String id,int count){var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));if(item==null||item==Items.AIR)throw new IllegalStateException("Missing gunsmith item "+id);return new ItemStack(item,count);}
    public static List<GunsmithManual> all(){
        try{
            // These are the actual paid costs and tool sequence; copying recipes here would drift.
            var outputs=(String[])definition("OUTPUT");var costs=(String[][])definition("COSTS");var easy=(String[])definition("EASY");var tags=(String[])definition("TOOLS");
            var result=new ArrayList<GunsmithManual>();
            for(int i=0;i<outputs.length;i++){result.add(view(i,false,outputs[i],costs[i],tags));if(i==3||i==6)result.add(view(i,true,outputs[i],i==6?(String[])definition("EASY_WHEEL"):easy,tags));}
            return List.copyOf(result);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Gunsmith workshop definitions changed; update its JEI adapter",e);}
    }
    private static GunsmithManual view(int selection,boolean learned,String output,String[] costs,String[] tags){
        var counts=new LinkedHashMap<String,Integer>();for(var id:costs)counts.merge(id,1,Integer::sum);
        // Starting replaces one held paper. Research's paid papers already include that sheet.
        counts.putIfAbsent("minecraft:paper",1);
        var materials=new ArrayList<ItemStack>();counts.forEach((id,count)->materials.add(stack(id,count)));
        var tools=Arrays.stream(tags).map(id->Ingredient.of(TagKey.create(Registries.ITEM,new ResourceLocation(id)))).toList();
        return new GunsmithManual(selection,learned,stack(output,1),List.copyOf(materials),tools,ua.empirecraft.forging.GunsmithRules.steps(selection,learned));
    }
}
