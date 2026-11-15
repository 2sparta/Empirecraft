package ua.empirecraft.casting;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.network.NetworkHooks;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;

public final class CastingBlock extends BaseEntityBlock {
    /** Facing is the pouring channel, towards the crucible. */
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty MOLD=BooleanProperty.create("mold"),ACTIVE=BlockStateProperties.LIT;
    public CastingBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(MOLD,false).setValue(ACTIVE,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,MOLD,ACTIVE);}
    static Direction connection(LevelReader level,BlockPos pos,Direction current){
        if(level.hasChunkAt(pos.relative(current))&&level.getBlockEntity(pos.relative(current)) instanceof CrucibleBlockEntity)return current;
        for(var d:Direction.Plane.HORIZONTAL)if(level.hasChunkAt(pos.relative(d))&&level.getBlockEntity(pos.relative(d)) instanceof CrucibleBlockEntity)return d;
        return current;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,connection(context.getLevel(),context.getClickedPos(),context.getHorizontalDirection()));}
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){
        if(!level.isClientSide){var direction=connection(level,pos,state.getValue(FACING));if(direction!=state.getValue(FACING))level.setBlock(pos,state.setValue(FACING,direction),2);}
    }
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(0,0,0,16,13,16);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CastingBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,Casting.ENTITY.get(),CastingBlockEntity::serverTick);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof CastingBlockEntity table)NetworkHooks.openScreen((ServerPlayer)player,table,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(state.getBlock()!=next.getBlock()&&level.getBlockEntity(pos) instanceof CastingBlockEntity table&&!level.isClientSide)table.dropContents();
        super.onRemove(state,level,pos,next,moving);
    }
}
