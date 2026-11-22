package ua.empirecraft.armory.mixin;

import com.otectus.immersivesmithing.recipe.SmithingData;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.armory.AssemblyCatalog;

@Mixin(value = SmithingData.class, remap = false)
public abstract class SmithingCatalogMixin {
    @Inject(method = "rebuild", at = @At("HEAD"))
    private static void empirecraft$installStagedRecipes(MinecraftServer server, CallbackInfo info) {
        AssemblyCatalog.install(server);
    }
}
