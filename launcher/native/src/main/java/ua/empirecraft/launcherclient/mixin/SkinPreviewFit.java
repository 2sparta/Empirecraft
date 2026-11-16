package ua.empirecraft.launcherclient.mixin;

import com.quickskin.mod.client.gui.screen.PlayerCapeMenuScreen;
import com.quickskin.mod.client.gui.screen.PlayerSkinMenuScreen;
import com.quickskin.mod.client.gui.widget.PlayerWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.launcherclient.HomeLayout;

/** Fit the existing editor's preview without overwriting saved Quick Skin preferences. */
@Mixin(value=PlayerWidget.class, remap=false)
public abstract class SkinPreviewFit {
    @Shadow private int cachedModelCenterX;
    @Shadow private int cachedModelCenterY;
    @Shadow private float cachedScale;
    @Shadow private float scale;

    @Inject(method="submitPreview", at=@At("HEAD"), remap=false)
    private void empirecraft$fitEditor(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        var screen = Minecraft.getInstance().screen;
        boolean cape = screen instanceof PlayerCapeMenuScreen;
        if (!(screen instanceof PlayerSkinMenuScreen) && !cape) return;
        var fit = HomeLayout.editorPreview(screen.width, screen.height, cape,
            cachedModelCenterX, cachedModelCenterY, cachedScale);
        cachedModelCenterX = fit.x();
        cachedModelCenterY = fit.feet();
        cachedScale = fit.scale();
        scale = cachedScale;
    }
}
