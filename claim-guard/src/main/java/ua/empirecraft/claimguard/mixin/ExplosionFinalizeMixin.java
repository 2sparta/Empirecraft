package ua.empirecraft.claimguard.mixin;

import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.claimguard.WeaponGuard;

@Mixin(targets = {
    "net.minecraft.world.level.Explosion",
    "net.mehvahdjukaar.supplementaries.common.misc.explosion.BombExplosion",
    "net.mehvahdjukaar.supplementaries.common.misc.explosion.CannonBallExplosion"
}, remap = false)
public abstract class ExplosionFinalizeMixin {
    @Inject(method = "m_46075_(Z)V", at = @At("HEAD"))
    private void empirecraft$protectBlocks(boolean particles, CallbackInfo ci) {
        Explosion explosion = (Explosion)(Object)this;
        if (WeaponGuard.weapon(explosion) != null) {
            WeaponGuard.filterBlocks(explosion, ((ExplosionAccess)explosion).empirecraft$level());
        }
    }
}
