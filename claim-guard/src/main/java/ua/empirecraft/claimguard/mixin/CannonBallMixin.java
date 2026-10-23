package ua.empirecraft.claimguard.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.claimguard.WeaponGuard;

@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.entities.CannonBallEntity", remap = false)
public abstract class CannonBallMixin {
    @Inject(method = "m_5790_(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At("HEAD"), cancellable = true)
    private void empirecraft$protectImpact(EntityHitResult hit, CallbackInfo ci) {
        if (!WeaponGuard.damage((Entity)(Object)this, hit.m_82443_())) ci.cancel();
    }
}
