package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.client.JourneyMapHeading;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {"journeymap.client.ui.minimap.MiniMap", "journeymap.client.ui.fullscreen.Fullscreen"}, remap = false)
public abstract class JourneyMapCameraYawMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;m_90590_()F"), remap = false, require = 1)
    private float empirecraft$cameraHeading(Camera camera) { return JourneyMapHeading.cameraYaw(camera); }
}
