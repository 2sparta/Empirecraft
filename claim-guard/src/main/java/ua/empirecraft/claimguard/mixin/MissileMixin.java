package ua.empirecraft.claimguard.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.magistu.siegemachines.entity.projectile.MissileType;
import ua.empirecraft.claimguard.WeaponGuard;

@Mixin(targets = "ru.magistu.siegemachines.entity.projectile.Missile", remap = false)
public abstract class MissileMixin {
    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lorg/joml/Vector3d;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/Entity;Lru/magistu/siegemachines/entity/projectile/MissileType;)V", at = @At("RETURN"))
    private void empirecraft$rememberGunner(EntityType<?> type, Level level, Vector3d position,
            LivingEntity shooter, Entity engine, MissileType missile, CallbackInfo ci) {
        WeaponGuard.rememberShooter((Entity)(Object)this, shooter);
    }

    @Inject(method = "canHurt", at = @At("HEAD"), cancellable = true)
    private void empirecraft$protectImpact(Entity victim, CallbackInfoReturnable<Boolean> cir) {
        if (!WeaponGuard.damage((Entity)(Object)this, victim)) cir.setReturnValue(false);
    }

    @Inject(method = "shouldDamageEntity", at = @At("HEAD"), cancellable = true)
    private void empirecraft$protectBlast(Explosion explosion, Entity victim, CallbackInfoReturnable<Boolean> cir) {
        if (!WeaponGuard.damage((Entity)(Object)this, victim)) cir.setReturnValue(false);
    }

    @Inject(method = "shouldBlockDestroy", at = @At("HEAD"), cancellable = true)
    private void empirecraft$protectBlocks(Explosion explosion, BlockGetter reader, BlockPos pos,
            BlockState state, float power, CallbackInfoReturnable<Boolean> cir) {
        if (!WeaponGuard.block(((Entity)(Object)this).m_9236_(), pos)) cir.setReturnValue(false);
    }

    @Redirect(method = "crackBlocks", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;m_46597_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean empirecraft$protectCracking(Level level, BlockPos pos, BlockState state) {
        return WeaponGuard.block(level, pos) && level.m_46597_(pos, state);
    }
}
