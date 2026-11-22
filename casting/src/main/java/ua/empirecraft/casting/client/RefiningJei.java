package ua.empirecraft.casting.client;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
@JeiPlugin
public final class RefiningJei implements IModPlugin {
 public ResourceLocation getPluginUid(){return new ResourceLocation("empirecraft_casting:refining");}
 public void registerRecipes(IRecipeRegistration registration){
  for(String id:new String[]{"tfc:metal/ingot/steel","tfc:metal/ingot/pig_iron","tfc:powder/flux"})registration.addIngredientInfo(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id))),VanillaTypes.ITEM_STACK,Component.translatable("empirecraft_casting.refining"));
 }
}
