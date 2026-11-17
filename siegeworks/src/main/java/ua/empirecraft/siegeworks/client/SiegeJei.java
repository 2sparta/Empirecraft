package ua.empirecraft.siegeworks.client;
import java.util.*;
import mezz.jei.api.*;import mezz.jei.api.constants.VanillaTypes;import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;import mezz.jei.api.gui.drawable.IDrawable;import mezz.jei.api.gui.ingredient.IRecipeSlotsView;import mezz.jei.api.helpers.IGuiHelper;import mezz.jei.api.recipe.*;import mezz.jei.api.recipe.category.IRecipeCategory;import mezz.jei.api.registration.*;import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.network.chat.Component;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.item.ItemStack;import net.minecraftforge.registries.ForgeRegistries;import net.minecraftforge.api.distmarker.Dist;import net.minecraftforge.fml.common.Mod;import net.minecraftforge.event.TickEvent;import net.minecraftforge.eventbus.api.SubscribeEvent;
import ua.empirecraft.siegeworks.Plan;
@JeiPlugin
@Mod.EventBusSubscriber(modid="siegeworks",value=Dist.CLIENT)
public final class SiegeJei implements IModPlugin {
 public static final RecipeType<Plan> TYPE=RecipeType.create("empirecraft_siegeworks","construction",Plan.class);
 static IJeiRuntime runtime;static List<Plan> shown=List.of();static int epoch,ticks;
 public static int epoch(){int hash=0;for(var e:Plan.definitions().entrySet())hash+=e.getKey().hashCode()^System.identityHashCode(e.getValue());return hash;}
 @Override public ResourceLocation getPluginUid(){return new ResourceLocation("empirecraft_siegeworks","blueprints");}
 @Override public void registerCategories(IRecipeCategoryRegistration r){r.addRecipeCategories(new Category(r.getJeiHelpers().getGuiHelper()));}
 @Override public void registerRecipes(IRecipeRegistration r){shown=Plan.all();r.addRecipes(TYPE,shown);epoch=epoch();}
 @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration r){r.addRecipeCatalyst(item("axiomata:drawing_table"),TYPE);r.addRecipeCatalyst(item("axiomata:construction_hammer"),TYPE);}
 @Override public void onRuntimeAvailable(IJeiRuntime value){runtime=value;}
 @Override public void onRuntimeUnavailable(){runtime=null;shown=List.of();epoch=0;}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||runtime==null||Minecraft.getInstance().level==null||++ticks%20!=0)return;int current=epoch();if(current==epoch)return;runtime.getRecipeManager().hideRecipes(TYPE,shown);shown=Plan.all();runtime.getRecipeManager().addRecipes(TYPE,shown);epoch=current;}
 public static boolean show(ItemStack stack){if(runtime==null)return false;runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,stack));return true;}
 static ItemStack item(String id){return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));}
 static Component text(String key,Object...args){return Component.translatable("empirecraft_siegeworks."+key,args);}
 static final class Category implements IRecipeCategory<Plan> {
  final IDrawable background,icon;
  Category(IGuiHelper gui){background=gui.createBlankDrawable(228,144);icon=gui.createDrawableIngredient(VanillaTypes.ITEM_STACK,item("axiomata:drawing_table"));}
  @Override public RecipeType<Plan> getRecipeType(){return TYPE;}@Override public Component getTitle(){return text("title");}@Override public IDrawable getBackground(){return background;}@Override public IDrawable getIcon(){return icon;}
  @Override public void setRecipe(IRecipeLayoutBuilder b,Plan p,IFocusGroup focus){for(int i=0;i<p.materials().size();i++){var in=p.materials().get(i);var stacks=Arrays.stream(in.ingredient().getItems()).map(s->s.copyWithCount(in.count())).toList();b.addSlot(RecipeIngredientRole.INPUT,4+i%6*19,22+i/6*19).addItemStacks(stacks);}
   b.addSlot(RecipeIngredientRole.OUTPUT,201,27).addItemStack(p.result());b.addSlot(RecipeIngredientRole.CATALYST,156,80).addItemStack(p.blueprint()).addTooltipCallback((slot,tip)->tip.add(text("quality")));b.addSlot(RecipeIngredientRole.CATALYST,201,80).addItemStack(item("axiomata:construction_hammer")).addTooltipCallback((slot,tip)->tip.add(text("hammers")));}
  @Override public void draw(Plan p,IRecipeSlotsView slots,GuiGraphics g,double x,double y){var font=Minecraft.getInstance().font;g.drawString(font,text("stages",p.stages()),3,4,0xff534838,false);g.drawString(font,"→",174,32,0xff534838,false);g.drawString(font,text("blueprint"),143,68,0xff534838,false);g.drawString(font,text("hammer"),194,68,0xff534838,false);g.drawWordWrap(font,text("instructions"),3,105,222,0xff534838);}
 }
}
