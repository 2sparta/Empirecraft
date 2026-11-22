package ua.empirecraft.forging.mixin;

import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.forging.EquipmentRecipes;
import java.util.Map;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

@Mixin(value = RecipeManager.class, remap = false)
public abstract class RecipeReloadMixin {
    @Inject(method = "m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"))
    private void empirecraft$equipmentRecipes(Map<ResourceLocation, JsonElement> recipes, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        EquipmentRecipes.rewrite(recipes);
    }
}
