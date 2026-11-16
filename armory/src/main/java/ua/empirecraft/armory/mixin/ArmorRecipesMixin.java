package ua.empirecraft.armory.mixin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import com.google.gson.JsonObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.armory.AssemblyRecipe;
@Mixin(value=RecipeManager.class,remap=false)
public abstract class ArmorRecipesMixin {
    @Inject(method={"m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V","m_44024_"},at=@At("TAIL"),require=2)
    private void armory$invalidate(CallbackInfo ci){AssemblyRecipe.invalidate((RecipeManager)(Object)this);}
}
