package ua.empirecraft.interactions;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BoilGlassWaterRecipe extends SmeltingRecipe {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, "empirecraft_interactions");
    private static final RegistryObject<RecipeSerializer<?>> SERIALIZER = SERIALIZERS.register("boil_glass_water", Serializer::new);

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    private BoilGlassWaterRecipe(ResourceLocation id, String group, CookingBookCategory category,
                                Ingredient ingredient, ItemStack result, float experience, int time) {
        super(id, group, category, ingredient, result, experience, time);
    }

    @Override
    public boolean matches(Container input, Level level) {
        var water = Glassware.contents(input.getItem(0));
        return Glassware.isFreshWater(water) && !Glassware.isBoiled(water) && super.matches(input, level);
    }

    @Override
    public ItemStack assemble(Container input, RegistryAccess registries) {
        var water = Glassware.contents(input.getItem(0));
        return Glassware.isFreshWater(water) && !Glassware.isBoiled(water)
            ? Glassware.boiledWater(input.getItem(0)) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return Glassware.boiledWater(Glassware.waterDisplay(super.getResultItem(registries)));
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY,
            Ingredient.of(Glassware.waterDisplay(super.getResultItem(RegistryAccess.EMPTY))));
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    private static final class Serializer implements RecipeSerializer<BoilGlassWaterRecipe> {
        @Override
        public BoilGlassWaterRecipe fromJson(ResourceLocation id, JsonObject json) {
            return wrap(RecipeSerializer.SMELTING_RECIPE.fromJson(id, json));
        }

        @Override
        public BoilGlassWaterRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return wrap(RecipeSerializer.SMELTING_RECIPE.fromNetwork(id, buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, BoilGlassWaterRecipe recipe) {
            RecipeSerializer.SMELTING_RECIPE.toNetwork(buffer, recipe);
        }

        private static BoilGlassWaterRecipe wrap(SmeltingRecipe recipe) {
            return new BoilGlassWaterRecipe(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getIngredients().get(0),
                recipe.getResultItem(RegistryAccess.EMPTY), recipe.getExperience(), recipe.getCookingTime());
        }
    }
}
