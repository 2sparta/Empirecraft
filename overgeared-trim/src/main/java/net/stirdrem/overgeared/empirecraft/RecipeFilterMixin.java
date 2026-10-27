package net.stirdrem.overgeared.empirecraft;

import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=RecipeManager.class, remap=false)
public abstract class RecipeFilterMixin {
    @Inject(method="m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",at=@At("HEAD"))
    private void empirecraft$removeDeletedRecipes(Map<ResourceLocation,JsonElement> recipes,ResourceManager manager,ProfilerFiller profiler,CallbackInfo ci) {
        RecipePolicy.filter(recipes);
    }
}
