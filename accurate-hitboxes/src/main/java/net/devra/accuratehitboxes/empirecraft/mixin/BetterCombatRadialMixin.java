package net.devra.accuratehitboxes.empirecraft.mixin;

import net.devra.accuratehitboxes.empirecraft.BetterCombatGeometry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.bettercombat.client.collision.TargetFinder$RadialFilter",remap=false)
public abstract class BetterCombatRadialMixin {
    @Shadow @Final private Vec3 origin;
    @Shadow @Final private Vec3 orientation;
    @Shadow @Final private double attackRange;
    @Shadow @Final private double attackAngle;
    @Inject(method="lambda$filter$0",at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$modelRange(Entity entity,CallbackInfoReturnable<Boolean> ci){
        Boolean hit=BetterCombatGeometry.radial(entity,origin,orientation,attackRange,attackAngle);if(hit!=null)ci.setReturnValue(hit);
    }
}
