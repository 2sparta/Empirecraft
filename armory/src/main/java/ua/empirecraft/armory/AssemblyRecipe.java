package ua.empirecraft.armory;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class AssemblyRecipe implements Recipe<SimpleContainer> {
    private static final Map<RecipeManager,List<AssemblyRecipe>> CATALOG=new WeakHashMap<>();
    public final ResourceLocation id;
    public final ItemStack result;
    public final Profile profile;
    public final Ingredient base;
    public final List<Ingredient> extras;
    public AssemblyRecipe(ResourceLocation id,ItemStack result,Profile profile,Ingredient base,List<Ingredient> extras){this.id=id;this.result=result.copyWithCount(1);this.profile=profile;this.base=base;this.extras=List.copyOf(extras);}
    private static boolean standardMaterial(ItemStack s){
        String path=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getPath();
        if(path.contains("netherite")||path.contains("diamond"))return false;
        if(s.getItem() instanceof ArmorItem)return false;
        return path.contains("ingot")||path.contains("nugget")||path.contains("plate")||path.contains("sheet")||path.contains("chainmail")||path.contains("lamellar")||path.endsWith("_ring")||path.equals("leather_strip")||path.equals("leather")||path.equals("string");
    }
    public static Recipe<?> convert(ResourceLocation id,Recipe<?> recipe,RegistryAccess registries){
        if(recipe==null)return null;
        if(recipe instanceof AssemblyRecipe)return recipe;
        if(RestyleRecipes.wrapped(recipe))return recipe;
        if(recipe instanceof SmithingTrimRecipe||recipe instanceof CustomRecipe)return recipe;
        ItemStack output=recipe.getResultItem(registries);
        if(!(output.getItem() instanceof ArmorItem armor))return recipe;
        String path=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(armor).getPath();
        if(path.endsWith("_decoration")||path.endsWith("_model")||armor.getDefense()==0&&armor.getToughness()==0&&output.getMaxDamage()==0)return recipe;
        List<ItemStack> sources=new ArrayList<>();boolean cosmetic=false,structural=false;
        if(recipe instanceof SmithingRecipe smithing)for(var item:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues())if(item instanceof ArmorItem a&&a.getEquipmentSlot()==armor.getEquipmentSlot()){
            var stack=new ItemStack(item);if(smithing.isBaseIngredient(stack)){sources.add(stack);if(a.getDefense()==armor.getDefense()&&a.getToughness()==armor.getToughness()&&a.getMaterial().getName().equals(armor.getMaterial().getName()))cosmetic=true;else structural=true;}
        }
        for(Ingredient in:recipe.getIngredients())for(ItemStack s:in.getItems())if(s.getItem() instanceof ArmorItem a){
            if(a.getEquipmentSlot()==armor.getEquipmentSlot())sources.add(s);
            if(a.getEquipmentSlot()==armor.getEquipmentSlot()&&a.getDefense()==armor.getDefense()&&a.getToughness()==armor.getToughness()&&(a.getMaterial().getName().equals(armor.getMaterial().getName())||RestyleRecipes.yelek(a)&&RestyleRecipes.yelek(armor)))cosmetic=true;else if(a.getEquipmentSlot()==armor.getEquipmentSlot())structural=true;
        }
        // Dye, trims and same-protection restyling keep their original NBT-preserving routes.
        if(cosmetic&&!structural)return RestyleRecipes.wrap(recipe);
        if(armor.getMaterial()==ArmorMaterials.TURTLE)return recipe;
        var base=sources.isEmpty()?Ingredient.EMPTY:Ingredient.of(sources.stream());List<Ingredient> extras=new ArrayList<>();
        for(var in:recipe.getIngredients()){
            var options=in.getItems();if(in.isEmpty()||options.length==0)continue;
            if(Arrays.stream(options).allMatch(s->base.test(s)||standardMaterial(s)))continue;
            extras.add(in);
        }
        if(recipe instanceof SmithingRecipe smithing){
            // Vanilla smithing exposes an empty ingredient list. Preserve templates and rare additions.
            List<ItemStack> templates=new ArrayList<>(),additions=new ArrayList<>();
            for(var item:net.minecraftforge.registries.ForgeRegistries.ITEMS.getValues()){var s=new ItemStack(item);if(smithing.isTemplateIngredient(s))templates.add(s);if(smithing.isAdditionIngredient(s)&&!standardMaterial(s))additions.add(s);}
            if(!templates.isEmpty())extras.add(Ingredient.of(templates.stream()));if(!additions.isEmpty())extras.add(Ingredient.of(additions.stream()));
        }
        if(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(armor).toString().equals(OttomanCombat.NS+"shishak_helmet")){extras.add(Ingredient.of(Items.GOLD_INGOT));extras.add(Ingredient.of(Items.GOLD_NUGGET));}
        return new AssemblyRecipe(id,output,Profile.of(output,recipe),base,extras);
    }
    public List<Ingredient> stage(int stage){
        List<Ingredient> needs=new ArrayList<>();
        if(stage==0){if(!base.isEmpty())needs.add(base);needs.addAll(extras);needs.add(Ingredient.of(Armory.PARTS.get("padded_lining").get()));}
        else if(stage==1){int end=(profile.forms().size()+1)/2;for(int i=0;i<end;i++)needs.add(Ingredient.of(profile.item(profile.forms().get(i))));}
        else if(stage==2){int start=(profile.forms().size()+1)/2;for(int i=start;i<profile.forms().size();i++)needs.add(Ingredient.of(profile.item(profile.forms().get(i))));}
        else if(stage==3){needs.add(Ingredient.of(Armory.PARTS.get("buckle").get()));if(!profile.construction().equals("padded"))for(int i=0;i<4;i++)needs.add(Ingredient.of(Armory.PARTS.get("rivets").get()));needs.add(Ingredient.of(net.minecraftforge.common.Tags.Items.LEATHER));}
        return needs;
    }
    @Override public boolean matches(SimpleContainer c,Level level){return false;}
    @Override public ItemStack assemble(SimpleContainer c,RegistryAccess access){return result.copy();}
    @Override public boolean canCraftInDimensions(int w,int h){return false;}
    @Override public boolean isSpecial(){return true;}
    @Override public ItemStack getResultItem(RegistryAccess access){return result.copy();}
    @Override public NonNullList<Ingredient> getIngredients(){NonNullList<Ingredient> values=NonNullList.create();for(int s=0;s<4;s++)values.addAll(stage(s));return values;}
    @Override public ResourceLocation getId(){return id;}
    @Override public RecipeSerializer<?> getSerializer(){return Armory.SERIALIZER.get();}
    @Override public RecipeType<?> getType(){return Armory.TYPE.get();}
    public static synchronized void invalidate(RecipeManager manager){CATALOG.remove(manager);}
    public static synchronized List<AssemblyRecipe> all(Level level){return CATALOG.computeIfAbsent(level.getRecipeManager(),m->m.getAllRecipesFor(Armory.TYPE.get()).stream().sorted(Comparator.comparing(r->r.id.toString())).toList());}
    public static final class Serializer implements RecipeSerializer<AssemblyRecipe>{
        @Override public AssemblyRecipe fromJson(ResourceLocation id,JsonObject json){throw new JsonParseException("Assembly recipes are derived from active armor recipes; use those recipes to customize them");}
        @Override public void toNetwork(FriendlyByteBuf b,AssemblyRecipe r){b.writeItem(r.result);b.writeUtf(r.profile.metal());b.writeUtf(r.profile.construction());b.writeVarInt(r.profile.forms().size());for(String f:r.profile.forms())b.writeUtf(f);r.base.toNetwork(b);b.writeVarInt(r.extras.size());for(var extra:r.extras)extra.toNetwork(b);}
        @Override public AssemblyRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf b){ItemStack s=b.readItem();String m=b.readUtf(32),c=b.readUtf(32);int n=b.readVarInt();if(n<1||n>32)throw new IllegalArgumentException("Invalid assembly part count");List<String> forms=new ArrayList<>();for(int i=0;i<n;i++){String f=b.readUtf(32);if(!Armory.FORMS.containsKey(f)&&!Armory.PARTS.containsKey(f))throw new IllegalArgumentException("Unknown part "+f);forms.add(f);}var base=Ingredient.fromNetwork(b);int count=b.readVarInt();if(count<0||count>64)throw new IllegalArgumentException("Invalid extra ingredient count");List<Ingredient> extras=new ArrayList<>();for(int i=0;i<count;i++)extras.add(Ingredient.fromNetwork(b));return new AssemblyRecipe(id,s,new Profile(m,c,List.copyOf(forms)),base,extras);}
    }
}
