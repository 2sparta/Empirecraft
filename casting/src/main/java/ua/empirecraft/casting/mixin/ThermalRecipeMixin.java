package ua.empirecraft.casting.mixin;
import com.google.gson.JsonObject;import net.minecraft.resources.ResourceLocation;
import net.stirdrem.overgeared.recipe.ForgingRecipe;import ua.empirecraft.casting.Thermal;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=ForgingRecipe.Serializer.class,remap=false)
public abstract class ThermalRecipeMixin {
 @Inject(method="fromJson",at=@At("HEAD"))
 private void temperatures(ResourceLocation id,JsonObject json,CallbackInfoReturnable<ForgingRecipe> cir){Thermal.forging(json);}
}
