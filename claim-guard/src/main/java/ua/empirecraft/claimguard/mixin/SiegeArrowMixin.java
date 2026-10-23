package ua.empirecraft.claimguard.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.claimguard.WeaponGuard;

@Mixin(targets = "ru.magistu.siegemachines.entity.projectile.GiantArrow", remap = false)
public abstract class SiegeArrowMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lorg/joml/Vector3d;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void empirecraft$rememberGunner(EntityType<?> type, Level level, Vector3d position,
            LivingEntity shooter, Entity engine, ItemStack item, CallbackInfo ci) {
        WeaponGuard.rememberShooter((Entity)(Object)this, shooter);
    }

    @Inject(method = "m_5790_(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At("HEAD"), cancellable = true)
    private void empirecraft$protectImpact(EntityHitResult hit, CallbackInfo ci) {
        if (!WeaponGuard.damage((Entity)(Object)this, hit.m_82443_())) ci.cancel();
    }
}
