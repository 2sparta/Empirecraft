package ua.empirecraft.tfc.mixin;
import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.tfc.RecipePolicy;

@Mixin(value=RecipeManager.class,remap=false,priority=500)
public abstract class RecipeMixin {
    @Inject(method="m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",at=@At("HEAD"))
    private void empirecraft$tfcRecipes(Map<ResourceLocation,JsonElement> recipes,ResourceManager manager,ProfilerFiller profiler,CallbackInfo ci) {
        RecipePolicy.rewrite(recipes,ResourceLocation::new);
    }
}
