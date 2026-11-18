package ua.empirecraft.interactions.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.interactions.ThrownImpacts;

@Mixin(value = AbstractArrow.class, remap = false)
public abstract class ThrownArrowMixin {
    @Inject(method = "m_5603_", at = @At("RETURN"), cancellable = true)
    private void empirecraft$filterTarget(Entity target, CallbackInfoReturnable<Boolean> result) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (result.getReturnValue() && ThrownImpacts.tracked(arrow) && !ThrownImpacts.allows(arrow, target)) {
            result.setReturnValue(false);
        }
    }

    @Redirect(method = "m_8119_", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;m_7099_(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean empirecraft$checkReturningThrow(Player attacker, Player target) {
        return ThrownImpacts.selfImpact((Entity) (Object) this, target, attacker) || attacker.canHarmPlayer(target);
    }
}
