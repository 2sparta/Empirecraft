package ua.empirecraft.shipcannons.mixin;
import net.mehvahdjukaar.moonlight.api.entity.ImprovedProjectileEntity;
import net.mehvahdjukaar.supplementaries.common.entities.CannonBallEntity;
import net.mehvahdjukaar.supplementaries.common.entities.SlingshotProjectileEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.WorldEntityHandler;

@Mixin(value=ImprovedProjectileEntity.class,remap=false)
public abstract class CannonballTickMixin {
    /** Moonlight replaces Projectile.tick, so VS's usual shipyard-exit hook never runs. */
    @Inject(method="m_8119_()V",at=@At("HEAD"),require=1)
    private void empirecraft$leaveShipyard(CallbackInfo ci) {
        if(!((Object)this instanceof CannonBallEntity) && !((Object)this instanceof SlingshotProjectileEntity))return;
        Projectile ball=(Projectile)(Object)this;
        var ship=VSGameUtilsKt.getShipManaging(ball);
        if(ship!=null && !ball.level().getBlockCollisions(ball,ball.getBoundingBox().inflate(.1)).iterator().hasNext())
            WorldEntityHandler.INSTANCE.moveEntityFromShipyardToWorld(ball,ship);
    }
}
