package ua.empirecraft.shipcannons.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.munitions.big_cannon.grapeshot.GrapeshotBurst;
import rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst;
import ua.empirecraft.shipcannons.*;

@Pseudo
@Mixin(targets="rbasamoyai.createbigcannons.munitions.big_cannon.shrapnel.ShrapnelBurst",remap=false)
public abstract class GrapeshotLightHitMixin {
    @Inject(method="onSubProjectileHitBlock",at=@At("HEAD"),cancellable=true)
    private void empirecraft$noSecondDamage(BlockHitResult hit,ProjectileBurst.SubProjectile pellet,CallbackInfo ci) {
        Entity self=(Entity)(Object)this;
        if((Object)this instanceof GrapeshotBurst && ((GrapeshotState)this).empirecraft$consumeStop(hit.getBlockPos())) {ci.cancel();return;}
        // A light block returned by our ray was a denial or an exhausted budget. Native damage
        // must not turn that stop into a second, permission-free attempt.
        if((Object)this instanceof GrapeshotBurst && CannonRecoil.grapeshotEnabled() && GrapeshotDamage.light(self.level().getBlockState(hit.getBlockPos())))ci.cancel();
    }
}
