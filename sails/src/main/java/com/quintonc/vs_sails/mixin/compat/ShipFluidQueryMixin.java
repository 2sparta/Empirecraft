package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.ship.FloodQueryScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.BlockState;
import com.quintonc.vs_sails.ship.ShipFlooding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class ShipFluidQueryMixin {
    @Inject(method="m_6933_",at=@At("RETURN"),remap=false)
    private void empirecraft$geometryChanged(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> result){
        if(result.getReturnValue())ShipFlooding.changed((Level)(Object)this,pos);
    }
    @Inject(method="m_6425_",at=@At("RETURN"),cancellable=true,remap=false)
    private void empirecraft$finiteInterior(BlockPos pos,CallbackInfoReturnable<FluidState> result){
        result.setReturnValue(FloodQueryScope.filter((Level)(Object)this,pos,result.getReturnValue()));
    }
}
