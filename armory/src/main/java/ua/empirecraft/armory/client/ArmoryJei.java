package ua.empirecraft.armory.client;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ua.empirecraft.armory.*;
@JeiPlugin
public final class ArmoryJei implements IModPlugin {
    public static final mezz.jei.api.recipe.RecipeType<AssemblyRecipe> TYPE=mezz.jei.api.recipe.RecipeType.create(Armory.ID,"assembly",AssemblyRecipe.class);
    @Override public ResourceLocation getPluginUid(){return new ResourceLocation(Armory.ID,"workshop");}
    @Override public void registerCategories(IRecipeCategoryRegistration r){r.addRecipeCategories(new Category(r.getJeiHelpers().getGuiHelper()));}
    @Override public void registerRecipes(IRecipeRegistration r){var level=Minecraft.getInstance().level;if(level!=null)r.addRecipes(TYPE,AssemblyRecipe.all(level));r.addIngredientInfo(new ItemStack(Armory.TABLE_ITEM.get()),VanillaTypes.ITEM_STACK,Component.translatable("armory.jei"));}
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration r){r.addRecipeCatalyst(new ItemStack(Armory.TABLE_ITEM.get()),TYPE);}
    private static final class Category implements IRecipeCategory<AssemblyRecipe> {
        private final IDrawable background,icon;
        Category(IGuiHelper h){background=h.createBlankDrawable(232,128);icon=h.createDrawableIngredient(VanillaTypes.ITEM_STACK,new ItemStack(Armory.TABLE_ITEM.get()));}
        @Override public mezz.jei.api.recipe.RecipeType<AssemblyRecipe> getRecipeType(){return TYPE;}
        @Override public Component getTitle(){return Component.translatable("block.empirecraft_armory.armorer_table");}
        @Override public IDrawable getBackground(){return background;}
        @Override public IDrawable getIcon(){return icon;}
        @Override public void setRecipe(IRecipeLayoutBuilder b,AssemblyRecipe r,IFocusGroup focuses){
            for(int step=0;step<4;step++){var needs=r.stage(step);for(int i=0;i<needs.size();i++){var ingredient=needs.get(i);List<ItemStack> stacks=Arrays.stream(ingredient.getItems()).map(s->s.getItem() instanceof PartItem?Parts.sample(s.getItem()):s.copy()).toList();b.addSlot(RecipeIngredientRole.INPUT,26+i*18,step*27+10).addItemStacks(stacks);}}
            b.addSlot(RecipeIngredientRole.OUTPUT,210,110).addItemStack(r.result);
        }
        @Override public void draw(AssemblyRecipe r,mezz.jei.api.gui.ingredient.IRecipeSlotsView slots,GuiGraphics g,double x,double y){var font=Minecraft.getInstance().font;for(int i=0;i<4;i++)g.drawString(font,(i+1)+".",3,i*27+14,0xff534838,false);g.drawString(font,Component.translatable("armory.jei_cost",r.profile.metalCost()),2,113,0xff534838,false);}
    }
}
