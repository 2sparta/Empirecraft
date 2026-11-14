package ua.empirecraft.casting;
import com.google.gson.JsonObject;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.item.ItemStack;import net.minecraft.world.item.crafting.*;import net.minecraft.world.inventory.CraftingContainer;import net.minecraft.core.RegistryAccess;import net.minecraft.network.FriendlyByteBuf;
public final class QualityRecipe extends ShapedRecipe {
 public QualityRecipe(ShapedRecipe base){super(base.getId(),base.getGroup(),base.category(),base.getWidth(),base.getHeight(),base.getIngredients(),base.getResultItem(RegistryAccess.EMPTY),base.showNotification());}
 @Override public RecipeSerializer<?> getSerializer(){return Casting.QUALITY.get();}
 @Override public ItemStack assemble(CraftingContainer grid,RegistryAccess access){var result=super.assemble(grid,access);String[] levels={"poor","well","expert","perfect","master"};int lowest=levels.length;for(int slot=0;slot<grid.getContainerSize();slot++){var stack=grid.getItem(slot);if(stack.hasTag()){String quality=stack.getTag().getString("ForgingQuality");for(int i=0;i<levels.length;i++)if(levels[i].equals(quality))lowest=Math.min(lowest,i);}}if(lowest<levels.length)result.getOrCreateTag().putString("ForgingQuality",levels[lowest]);return result;}
 public static final class Serializer implements RecipeSerializer<QualityRecipe>{
  private final ShapedRecipe.Serializer base=new ShapedRecipe.Serializer();
  @Override public QualityRecipe fromJson(ResourceLocation id,JsonObject json){return new QualityRecipe(base.fromJson(id,json));}
  @Override public QualityRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buffer){var recipe=base.fromNetwork(id,buffer);return recipe==null?null:new QualityRecipe(recipe);}
  @Override public void toNetwork(FriendlyByteBuf buffer,QualityRecipe recipe){base.toNetwork(buffer,recipe);}
 }
}
