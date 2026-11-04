package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.ship.DoorWater;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(TrapDoorBlock.class)
public abstract class FiniteTrapdoorMixin {
    @Inject(method="m_7926_",at=@At("TAIL"),remap=false)
    private void empirecraft$finiteProperty(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci){builder.add(DoorWater.FINITE);}
    @Inject(method="m_5573_",at=@At("RETURN"),cancellable=true,remap=false)
    private void empirecraft$placedInWater(BlockPlaceContext context,CallbackInfoReturnable<BlockState> result){
        result.setReturnValue(DoorWater.placement(context,result.getReturnValue()));
    }
    @Inject(method="m_5888_",at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$finiteFluid(BlockState state,CallbackInfoReturnable<FluidState> result){
        if(state.getValue(DoorWater.FINITE)>0)result.setReturnValue(DoorWater.fluid(state));
    }
}
