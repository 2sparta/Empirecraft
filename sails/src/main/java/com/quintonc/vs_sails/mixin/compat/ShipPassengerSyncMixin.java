package com.quintonc.vs_sails.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;

/** Remote players do not run local deck collision, so authority must renew their lease. */
@Mixin(value=EntityDraggingInformation.class,remap=false)
public abstract class ShipPassengerSyncMixin {
    @Shadow private Long lastShipStoodOn;
    @Shadow private Long lastShipStoodOnServerWriteOnly;
    @Shadow private int ticksSinceStoodOnShip;

    @Inject(method="setTicksSinceLastServerPacket(I)V",at=@At("HEAD"))
    private void empirecraft$renewPassenger(int ticks,CallbackInfo ci){
        // Only the authoritative client-bound motion handler resets this timer.
        // Null/different IDs still detach normally; local collision is unchanged.
        if(ticks==0 && lastShipStoodOn!=null && lastShipStoodOn.equals(lastShipStoodOnServerWriteOnly))
            ticksSinceStoodOnShip=0;
    }

    @Inject(method="setServerRelativePlayerPosition(Lorg/joml/Vector3dc;)V",at=@At("HEAD"))
    private void empirecraft$renewServerPassenger(org.joml.Vector3dc position,CallbackInfo ci){
        // The server also receives repeated positions without changing the ship ID.
        if(position!=null && position.isFinite() && lastShipStoodOn!=null)ticksSinceStoodOnShip=0;
    }
}
