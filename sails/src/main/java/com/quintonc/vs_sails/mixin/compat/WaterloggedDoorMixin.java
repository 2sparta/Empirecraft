package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.ship.DoorWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(DoorBlock.class)
public abstract class WaterloggedDoorMixin extends Block implements SimpleWaterloggedBlock {
    protected WaterloggedDoorMixin(Properties properties){super(properties);}
    @Inject(method="<init>",at=@At("TAIL"))
    private void empirecraft$dryDefault(Properties properties,net.minecraft.world.level.block.state.properties.BlockSetType type,CallbackInfo ci){
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,false).setValue(DoorWater.FINITE,0));
    }
    @Inject(method="m_7926_",at=@At("TAIL"),remap=false)
    private void empirecraft$waterProperties(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci){
        builder.add(BlockStateProperties.WATERLOGGED,DoorWater.FINITE);
    }
    @Inject(method="m_5573_",at=@At("RETURN"),cancellable=true,remap=false)
    private void empirecraft$placedInWater(BlockPlaceContext context,CallbackInfoReturnable<BlockState> result){
        result.setReturnValue(DoorWater.placement(context,result.getReturnValue()));
    }
    @WrapMethod(method="m_6402_",remap=false)
    private void empirecraft$upperWater(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack,Operation<Void> original){
        var upperFluid=level.getFluidState(pos.above());boolean upperWet=upperFluid.is(FluidTags.WATER);
        boolean ship=com.quintonc.vs_sails.ship.ShipFlooding.shipPosition(level,pos);
        original.call(level,pos,state,placer,stack);
        BlockState upper=level.getBlockState(pos.above());
        if(upper.getBlock()==(Object)this && upper.hasProperty(BlockStateProperties.WATERLOGGED))
            level.setBlock(pos.above(),upper.setValue(BlockStateProperties.WATERLOGGED,upperWet).setValue(DoorWater.FINITE,upperWet&&ship?Math.min(8,upperFluid.getAmount()):0),Block.UPDATE_ALL);
    }
    @Override public FluidState getFluidState(BlockState state){return DoorWater.fluid(state);}
}
