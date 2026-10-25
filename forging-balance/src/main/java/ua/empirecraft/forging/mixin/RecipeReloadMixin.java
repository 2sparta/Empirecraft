package ua.empirecraft.forging.mixin;

import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.forging.ArmorGraph;

@Mixin(value = RecipeManager.class, remap = false)
public abstract class RecipeReloadMixin {
    @Inject(method = {"m_5787_(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", "m_44024_"}, at = @At("TAIL"), require = 2)
    private void empirecraft$refreshUpgradeGraph(CallbackInfo ci) {
        ArmorGraph.invalidate((RecipeManager)(Object)this);
    }
}
