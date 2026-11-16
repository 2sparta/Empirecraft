package ua.empirecraft.armory;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
/** Keep the native crafting grid and JEI category while carrying the complete armor state. */
public final class RestyleRecipes {
    private RestyleRecipes(){}
    public static boolean wrapped(Recipe<?> recipe){return recipe instanceof Shaped||recipe instanceof Shapeless;}
    public static Recipe<?> wrap(Recipe<?> recipe){return recipe instanceof ShapedRecipe r?new Shaped(r):recipe instanceof ShapelessRecipe r?new Shapeless(r):recipe;}
    public static boolean yelek(Item item){var key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);return key!=null&&key.getNamespace().equals("hamzabus_ottoman_mod")&&(key.getPath().equals("yelek_chestplate")||key.getPath().endsWith("_ottoman_yelek_chestplate"));}
    static ItemStack preserve(CraftingContainer grid,ItemStack result){
        for(int i=0;i<grid.getContainerSize();i++){var source=grid.getItem(i);if(source.getItem() instanceof ArmorItem){double wear=source.isDamageableItem()?(double)source.getDamageValue()/Math.max(1,source.getMaxDamage()):0;if(source.hasTag())result.setTag(source.getTag().copy());if(result.isDamageableItem())result.setDamageValue((int)Math.floor(wear*result.getMaxDamage()));break;}}return result;
    }
    public static final class Shaped extends ShapedRecipe {
        public Shaped(ShapedRecipe r){super(r.getId(),r.getGroup(),r.category(),r.getWidth(),r.getHeight(),r.getIngredients(),r.getResultItem(RegistryAccess.EMPTY),r.showNotification());}
        @Override public ItemStack assemble(CraftingContainer grid,RegistryAccess access){return preserve(grid,super.assemble(grid,access));}
        @Override public RecipeSerializer<?> getSerializer(){return Armory.RESTYLE_SHAPED.get();}
    }
    public static final class Shapeless extends ShapelessRecipe {
        public Shapeless(ShapelessRecipe r){super(r.getId(),r.getGroup(),r.category(),r.getResultItem(RegistryAccess.EMPTY),r.getIngredients());}
        @Override public ItemStack assemble(CraftingContainer grid,RegistryAccess access){return preserve(grid,super.assemble(grid,access));}
        @Override public RecipeSerializer<?> getSerializer(){return Armory.RESTYLE_SHAPELESS.get();}
    }
    public static final class ShapedSerializer implements RecipeSerializer<Shaped>{
        private final ShapedRecipe.Serializer base=new ShapedRecipe.Serializer();
        public Shaped fromJson(ResourceLocation id,JsonObject json){return new Shaped(base.fromJson(id,json));}
        public Shaped fromNetwork(ResourceLocation id,FriendlyByteBuf b){return new Shaped(base.fromNetwork(id,b));}
        public void toNetwork(FriendlyByteBuf b,Shaped r){base.toNetwork(b,r);}
    }
    public static final class ShapelessSerializer implements RecipeSerializer<Shapeless>{
        private final ShapelessRecipe.Serializer base=new ShapelessRecipe.Serializer();
        public Shapeless fromJson(ResourceLocation id,JsonObject json){return new Shapeless(base.fromJson(id,json));}
        public Shapeless fromNetwork(ResourceLocation id,FriendlyByteBuf b){return new Shapeless(base.fromNetwork(id,b));}
        public void toNetwork(FriendlyByteBuf b,Shapeless r){base.toNetwork(b,r);}
    }
}
