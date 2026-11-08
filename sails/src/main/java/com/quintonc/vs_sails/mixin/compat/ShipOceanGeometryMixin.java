package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.client.OceanWaterClip;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Client hull edits must rebuild the intersecting world-water mesh too. */
@Mixin(Level.class)
public abstract class ShipOceanGeometryMixin {
    @Inject(method="m_6933_",at=@At("RETURN"),remap=false)
    private void empirecraft$oceanAfterEdit(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> result){
        if(!result.getReturnValue())return;
        Level level=(Level)(Object)this;if(!level.isClientSide)return;
        var ship=VSGameUtilsKt.getShipManagingPos(level,pos);
        if(ship!=null)OceanWaterClip.invalidate(ship.getId());
    }
}
