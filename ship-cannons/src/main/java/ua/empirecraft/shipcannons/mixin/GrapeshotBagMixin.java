package ua.empirecraft.shipcannons.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.munitions.big_cannon.grapeshot.GrapeshotBagProjectile;
import ua.empirecraft.shipcannons.CannonRecoil;

@Pseudo
@Mixin(targets="rbasamoyai.createbigcannons.munitions.big_cannon.DisintegratingBigCannonProjectile",remap=false)
public abstract class GrapeshotBagMixin {
    @Shadow protected abstract void disintegrate();
    @Inject(method="m_8119_()V",at=@At("HEAD"),cancellable=true)
    private void empirecraft$openAtMuzzle(CallbackInfo ci) {
        Entity self=(Entity)(Object)this;
        if((Object)this instanceof GrapeshotBagProjectile && !self.level().isClientSide && CannonRecoil.grapeshotEnabled()) {
            disintegrate();self.discard();ci.cancel();
        }
    }
}
