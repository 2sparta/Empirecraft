package ua.empirecraft.menuart;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** These are interface controls, so they should not clutter the crafting browser. */
@JeiPlugin
public final class MenuJei implements IModPlugin {
    @Override public ResourceLocation getPluginUid() {
        return new ResourceLocation(MenuArt.ID, "controls");
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        var icons = MenuArt.ICONS.values().stream().map(item -> new ItemStack(item.get())).toList();
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, icons);
    }
}
