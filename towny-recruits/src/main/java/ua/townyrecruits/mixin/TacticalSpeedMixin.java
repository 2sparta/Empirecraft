package ua.townyrecruits.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import ua.townyrecruits.tactics.*;

@Mixin(value = PathNavigation.class, remap = false)
public abstract class TacticalSpeedMixin {
    @Shadow @Final protected Mob f_26494_;
    @ModifyVariable(method = {"m_26536_(Lnet/minecraft/world/level/pathfinder/Path;D)Z", "m_26517_(D)V"},
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double selectedSpeed(double nativeSpeed) {
        var r = TacticalActor.rider(f_26494_);
        return r != null && r.getIsOwned() && !TacticalMovement.moving(r) && !TacticalCombat.controlsMovement(r)
            ? nativeSpeed * TacticalCombat.speed(r).multiplier / TacticalOrder.Speed.NORMAL.multiplier : nativeSpeed;
    }
}
