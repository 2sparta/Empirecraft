package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.ship.CabinAirSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Keep native entity, camera and fluid-particle queries on the same current hull topology. */
@Mixin(value = VSGameUtilsKt.class, remap = false)
public abstract class SealedCabinQueryMixin {
    @Inject(method = {"isPositionSealed", "isPositionMaybeSealed"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void empirecraft$confirmAirPocket(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        if (!ValkyrienSkies.isConnectivityEnabled(level.isClientSide)) return;
        Boolean sealed = CabinAirSpace.atStoragePosition(level, pos);
        if (sealed != null) result.setReturnValue(sealed);
    }
}
