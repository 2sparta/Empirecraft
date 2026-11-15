package ua.empirecraft.casting.mixin;
import java.util.*;import java.util.function.Function;import com.google.gson.JsonElement;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.tfc.RecipePolicy;import ua.empirecraft.casting.Metallurgy;
@Mixin(value=RecipePolicy.class,remap=false)
public abstract class PolicyMixin {
 @Inject(method="rewrite",at=@At("TAIL"))private static <K> void recipes(Map<K,JsonElement> recipes,Function<String,K> key,CallbackInfo ci){Metallurgy.rewrite(recipes,key);}
 @Inject(method="forbidden",at=@At("HEAD"),cancellable=true)private static void retired(String id,CallbackInfoReturnable<Boolean> cir){if(Metallurgy.retired(id))cir.setReturnValue(true);}
}
