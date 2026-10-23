package ua.empirecraft.claimguard.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.claimguard.WeaponGuard;

@Mixin(value = Projectile.class, remap = false)
public abstract class ProjectileOwnerMixin {
    @Inject(method = "m_5602_(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void empirecraft$rememberOwner(Entity owner, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (WeaponGuard.isWeapon(self)) WeaponGuard.rememberShooter(self, owner);
    }
}
