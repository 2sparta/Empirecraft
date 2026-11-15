package ua.empirecraft.launcherclient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public final class WeatherEffects {
    @Inject(method="m_109703_", at=@At("HEAD"), cancellable=true, remap=false)
    private void empirecraft$lightWeather(LightTexture light, float partial, double x, double y, double z, CallbackInfo ci) {
        if (LauncherClient.reducedEffects()) ci.cancel();
    }
}
