package ua.empirecraft.tfc.client;
import mezz.jei.api.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import ua.empirecraft.tfc.*;

@JeiPlugin
public final class JeiIntegration implements IModPlugin {
    @Override public ResourceLocation getPluginUid(){return new ResourceLocation(Integration.ID,"selected_systems");}
    @Override public void onRuntimeAvailable(IJeiRuntime runtime){
        var manager=runtime.getIngredientManager();
        var hidden=manager.getAllIngredients(VanillaTypes.ITEM_STACK).stream().filter(s->
            RecipePolicy.forbidden(Integration.id(s.getItem()))||RecipePolicy.ALIASES.containsKey(Integration.id(s.getItem()))).toList();
        manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,hidden);
    }
}
