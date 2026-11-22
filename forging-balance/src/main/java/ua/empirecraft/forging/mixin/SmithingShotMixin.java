package ua.empirecraft.forging.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.forging.RangedQuality;

@Mixin(targets = "com.otectus.immersivesmithing.quality.ProjectileEfficacy", remap = false)
public abstract class SmithingShotMixin {
    @Inject(method = "snapshot", at = @At("TAIL"))
    private static void empirecraft$legacyQuality(Entity projectile, ItemStack weapon, CallbackInfo info) {
        if (!projectile.m_9236_().m_5776_()) RangedQuality.mark(projectile, weapon);
    }
}
