package ua.empirecraft.wildlife;

import java.util.*;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

@JeiPlugin
public final class JeiFilter implements IModPlugin {
    @Override public ResourceLocation getPluginUid(){return new ResourceLocation(WildlifeMod.ID,"hidden");}
    @Override public void onRuntimeAvailable(IJeiRuntime runtime){
        var hidden=new ArrayList<ItemStack>();
        for(String id:WildlifeMod.policy.hidden){var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));if(item!=null)hidden.add(new ItemStack(item));}
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,hidden);
    }
}
