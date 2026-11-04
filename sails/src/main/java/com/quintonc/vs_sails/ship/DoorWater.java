package com.quintonc.vs_sails.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Door geometry stays vanilla; contained ship water is explicitly non-source. */
public final class DoorWater {
    public static final IntegerProperty FINITE=IntegerProperty.create("empirecraft_water_units",0,8);
    private DoorWater(){}
    public static boolean canContain(BlockState state){
        return state.isAir() || state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER)
                || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.hasProperty(BlockStateProperties.WATERLOGGED);
    }
    public static FluidState fluid(BlockState state){
        if(!state.getValue(BlockStateProperties.WATERLOGGED))return Fluids.EMPTY.defaultFluidState();
        int units=state.hasProperty(FINITE)?state.getValue(FINITE):0;
        return units>0?Fluids.FLOWING_WATER.getFlowing(units,units==8):Fluids.WATER.defaultFluidState();
    }
    public static BlockState placement(BlockPlaceContext context,BlockState state){
        if(state==null)return null;
        var water=context.getLevel().getFluidState(context.getClickedPos());
        boolean ship=VSGameUtilsKt.getShipManagingPos(context.getLevel(),context.getClickedPos())!=null;
        return state.setValue(BlockStateProperties.WATERLOGGED,water.is(FluidTags.WATER))
                .setValue(FINITE,ship&&water.is(FluidTags.WATER)?Math.min(8,water.getAmount()):0);
    }
    public static boolean write(ServerLevel level,BlockPos pos,int volume){
        BlockState state=level.getBlockState(pos),replacement;
        if(state.hasProperty(BlockStateProperties.WATERLOGGED)){
            replacement=state.setValue(BlockStateProperties.WATERLOGGED,volume>0);
            if(state.hasProperty(FINITE))replacement=replacement.setValue(FINITE,volume);
        }else if(state.isAir() || state.getBlock() instanceof LiquidBlock && state.getFluidState().is(FluidTags.WATER)){
            replacement=volume==0?Blocks.AIR.defaultBlockState():Blocks.WATER.defaultBlockState()
                    .setValue(LiquidBlock.LEVEL,volume==8?8:8-volume);
        }else return false;
        if(state!=replacement)level.setBlock(pos,replacement,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        return true;
    }
}
