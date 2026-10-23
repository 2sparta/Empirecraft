package ua.empirecraft.claimguard.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.empirecraft.claimguard.WeaponGuard;

@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.entities.BombEntity", remap = false)
public abstract class BombMixin {
    @Redirect(method = "m_5790_(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean empirecraft$protectImpact(Entity victim, DamageSource source, float damage) {
        return WeaponGuard.damage((Entity)(Object)this, victim) && victim.m_6469_(source, damage);
    }

    @Redirect(method = "m_5790_(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;m_142687_(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
    private void empirecraft$protectHitObject(Entity victim, Entity.RemovalReason reason) {
        if (WeaponGuard.damage((Entity)(Object)this, victim)) victim.m_142687_(reason);
    }
}
