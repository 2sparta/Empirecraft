package ua.empirecraft.armory.client;

import mezz.jei.api.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.armory.*;

@JeiPlugin
public final class GunsmithJei implements IModPlugin {
    public static final RecipeType<GunsmithManual> TYPE=RecipeType.create(Armory.ID,"gunsmith_manual",GunsmithManual.class);
    @Override public ResourceLocation getPluginUid(){return new ResourceLocation(Armory.ID,"gunsmith_manual");}
    @Override public void registerCategories(IRecipeCategoryRegistration r){r.addRecipeCategories(new Category(r.getJeiHelpers().getGuiHelper()));}
    @Override public void registerRecipes(IRecipeRegistration r){r.addRecipes(TYPE,GunsmithManual.all());}
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration r){r.addRecipeCatalyst(item("gunsmith_table"),TYPE);}
    private static ItemStack item(String name){return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("empirecraft_forging",name)));}
    private static Component tr(String key,Object...args){return Component.translatable("armory.gunsmith."+key,args);}
    private static final class Category implements IRecipeCategory<GunsmithManual> {
        final IDrawable background,icon;
        Category(IGuiHelper gui){background=gui.createBlankDrawable(238,158);icon=gui.createDrawableIngredient(VanillaTypes.ITEM_STACK,item("gunsmith_table"));}
        @Override public RecipeType<GunsmithManual> getRecipeType(){return TYPE;}
        @Override public Component getTitle(){return Component.translatable("block.empirecraft_forging.gunsmith_table");}
        @Override public IDrawable getBackground(){return background;}
        @Override public IDrawable getIcon(){return icon;}
        @Override public void setRecipe(IRecipeLayoutBuilder b,GunsmithManual r,IFocusGroup focus){
            for(int i=0;i<r.materials().size();i++)b.addSlot(RecipeIngredientRole.INPUT,4+(i%6)*19,22+(i/6)*19).addItemStack(r.materials().get(i));
            b.addSlot(RecipeIngredientRole.OUTPUT,212,25).addItemStack(r.output());
            for(int i=0;i<r.tools().size();i++)b.addSlot(RecipeIngredientRole.CATALYST,4+i*20,77).addIngredients(r.tools().get(i)).addTooltipCallback((view,tip)->tip.add(tr("tool_hint")));
            if(r.researched())b.addSlot(RecipeIngredientRole.CATALYST,205,77).addItemStack(r.blueprint()).addTooltipCallback((view,tip)->tip.add(tr("blueprint_hint")));
        }
        @Override public void draw(GunsmithManual r,IRecipeSlotsView slots,GuiGraphics g,double x,double y){var font=Minecraft.getInstance().font;
            g.drawString(font,tr(r.researched()?"learned":"normal"),3,3,0xff534838,false);g.drawString(font,"→",166,29,0xff534838,false);g.drawString(font,tr("tools"),3,63,0xff534838,false);
            g.drawString(font,tr("steps",r.operations()),3,99,0xff534838,false);g.drawWordWrap(font,tr("instruction"),3,111,232,0xff534838);
        }
    }
}
