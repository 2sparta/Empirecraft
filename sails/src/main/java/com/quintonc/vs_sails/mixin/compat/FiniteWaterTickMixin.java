package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.ship.ShipFlooding;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class FiniteWaterTickMixin {
    @Inject(method="m_6292_",at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$noRegeneratingWater(Level level,BlockPos pos,FluidState state,CallbackInfo ci){
        if((Object)this instanceof WaterFluid && ShipFlooding.shipPosition(level,pos))ci.cancel();
    }
}
