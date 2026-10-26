package ua.empirecraft.forging.client;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import ua.empirecraft.forging.EquipmentContent;

@JeiPlugin
public final class EquipmentJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() { return new ResourceLocation("empirecraft_forging", "equipment"); }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        var manager = runtime.getIngredientManager();
        var removed = manager.getAllIngredients(VanillaTypes.ITEM_STACK).stream()
            .filter(stack -> EquipmentContent.removed(stack.m_41720_()) || EquipmentContent.potionArrow(stack.m_41720_(), stack.m_41783_())).toList();
        manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, removed);
    }
}
