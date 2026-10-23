package ua.empirecraft.claimguard.mixin;

import com.google.common.collect.Multiset;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.claimguard.WeaponGuard;

@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.misc.explosion.CannonBallExplosion", remap = false)
public abstract class CannonExplosionMixin {
    @Inject(method = "destroyBlockNoEffects", at = @At("HEAD"), cancellable = true)
    private void empirecraft$protectRecursiveBreak(BlockPos pos, Level level, Entity source, int recursion,
            Multiset<SoundEvent> sounds, CallbackInfoReturnable<Boolean> cir) {
        if (!WeaponGuard.block(level, pos)) cir.setReturnValue(false);
    }
}
