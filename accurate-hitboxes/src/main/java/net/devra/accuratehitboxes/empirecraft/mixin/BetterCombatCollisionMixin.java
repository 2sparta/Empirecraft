package net.devra.accuratehitboxes.empirecraft.mixin;

import net.devra.accuratehitboxes.empirecraft.BetterCombatGeometry;
import net.bettercombat.client.collision.OrientedBoundingBox;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.bettercombat.client.collision.TargetFinder$CollisionFilter",remap=false)
public abstract class BetterCombatCollisionMixin {
    @Shadow private OrientedBoundingBox obb;
    @Inject(method="lambda$filter$0",at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$modelCollision(Entity entity,CallbackInfoReturnable<Boolean> ci){
        Boolean hit=BetterCombatGeometry.collides(obb,entity);if(hit!=null)ci.setReturnValue(hit);
    }
}
