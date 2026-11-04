package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.ship.SealedCabins;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = 900)
public abstract class SealedCabinEntityMixin {
    @Inject(method = "m_6075_", at = @At("HEAD"), remap = false)
    private void empirecraft$refreshCabin(CallbackInfo callback) {
        SealedCabins.refresh((Entity)(Object)this);
    }
}
