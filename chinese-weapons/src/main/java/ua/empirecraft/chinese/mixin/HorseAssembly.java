package ua.empirecraft.chinese.mixin;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.armory.*;
import ua.empirecraft.chinese.ChineseRules;
@Mixin(value=AssemblyRecipe.class,remap=false)
public abstract class HorseAssembly {
 @Inject(method="convert",at=@At("HEAD"),cancellable=true,require=1)
 private static void chinese$horse(ResourceLocation id,Recipe<?> recipe,RegistryAccess access,CallbackInfoReturnable<Recipe<?>> cir){if(recipe==null||recipe instanceof AssemblyRecipe||recipe instanceof CustomRecipe||recipe instanceof SmithingTrimRecipe)return;var out=recipe.getResultItem(access);if(!(out.getItem() instanceof HorseArmorItem)||!ChineseRules.subject(out))return;
  var profile=new Profile("iron","lamellar",List.of("lamellar_panel","lamellar_panel","lamellar_panel","lamellar_panel","lamellar_panel","lamellar_panel","shoulders","waist"));cir.setReturnValue(new AssemblyRecipe(id,out,profile,Ingredient.EMPTY,List.of(Ingredient.of(net.minecraftforge.common.Tags.Items.LEATHER),Ingredient.of(net.minecraft.tags.ItemTags.WOOL),Ingredient.of(Items.STRING))));
 }
}
