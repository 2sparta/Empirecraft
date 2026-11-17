package ua.empirecraft.armory;

import java.util.*;
import java.util.function.IntFunction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** A read-only view of the exact supply slots and ingredient order used by assembly. */
public final class AssemblyPlan {
    public record Need(Ingredient ingredient,int required,int available,int ready) {
        public int missing(){return required-available;}
        public boolean complete(){return ready==required;}
    }
    public final List<Need> needs;
    public final boolean complete;
    private AssemblyPlan(List<Need> needs){this.needs=List.copyOf(needs);complete=needs.stream().allMatch(Need::complete);}
    public static AssemblyPlan inspect(AssemblyRecipe recipe,int stage,IntFunction<ItemStack> slots){
        var grouped=new LinkedHashMap<String,int[]>();var ingredients=new LinkedHashMap<String,Ingredient>();
        int[] used=new int[WorkshopEntity.SLOTS],prepared=new int[WorkshopEntity.SLOTS];
        for(var ingredient:recipe.stage(Math.max(0,Math.min(3,stage)))){
            String key=ingredient.toJson().toString();ingredients.putIfAbsent(key,ingredient);int[] count=grouped.computeIfAbsent(key,k->new int[3]);count[0]++;
            for(int i=WorkshopEntity.FIRST;i<WorkshopEntity.SLOTS;i++){var stack=slots.apply(i);if(stack.getCount()>used[i]&&ingredient.test(stack)){used[i]++;count[1]++;break;}}
            for(int i=WorkshopEntity.FIRST;i<WorkshopEntity.SLOTS;i++){var stack=slots.apply(i);if(stack.getCount()>prepared[i]&&ingredient.test(stack)&&Parts.ready(stack)){prepared[i]++;count[2]++;break;}}
        }
        var rows=new ArrayList<Need>();grouped.forEach((key,n)->rows.add(new Need(ingredients.get(key),n[0],n[1],n[2])));return new AssemblyPlan(rows);
    }
}
