package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.ship.FiniteBuckets;
import com.quintonc.vs_sails.ship.ShipFlooding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(SimpleWaterloggedBlock.class)
public interface FiniteWaterloggedMixin {
    /**
     * @author Empirecraft
     * @reason Mixin 0.8.5 cannot inject into a default interface method. Preserve
     * finite ship quantities and retain normal exterior pickup behavior.
     */
    @Overwrite(remap=false)
    default ItemStack m_142598_(LevelAccessor accessor,BlockPos pos,BlockState state){
        if(accessor instanceof ServerLevel level && ShipFlooding.shipPosition(level,pos))return FiniteBuckets.pickupAutomated(level,pos);
        if(!state.getValue(BlockStateProperties.WATERLOGGED))return ItemStack.EMPTY;
        accessor.setBlock(pos,state.setValue(BlockStateProperties.WATERLOGGED,false),3);
        if(!state.canSurvive(accessor,pos))accessor.destroyBlock(pos,true);
        return new ItemStack(Items.WATER_BUCKET);
    }
}
