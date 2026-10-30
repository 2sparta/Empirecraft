package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.compat.CopycatMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Same collision behavior as hidden native cloth; material lookup never invokes shape code. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CopycatSailCollisionMixin {
    @Inject(method="m_60812_",at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$furledClothWithoutContext(BlockGetter world,BlockPos pos,CallbackInfoReturnable<VoxelShape> result) {
        BlockState state=(BlockState)(Object)this;
        if(state.hasBlockEntity() && CopycatMaterials.isCopycat(state) && CopycatMaterials.allSailsFurled(world,pos,state))result.setReturnValue(Shapes.empty());
    }
    @Inject(method="m_60742_",at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$furledCloth(BlockGetter world,BlockPos pos,CollisionContext context,CallbackInfoReturnable<VoxelShape> result) {
        BlockState state=(BlockState)(Object)this;
        if(state.hasBlockEntity() && CopycatMaterials.isCopycat(state) && CopycatMaterials.allSailsFurled(world,pos,state))result.setReturnValue(Shapes.empty());
    }
}
