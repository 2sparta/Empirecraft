package ua.empirecraft.armory;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
public final class WorkshopBlock extends BaseEntityBlock {
    public WorkshopBlock(Properties properties){super(properties);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new WorkshopEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,Armory.ENTITY.get(),WorkshopEntity::tick);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof WorkshopEntity table)NetworkHooks.openScreen((ServerPlayer)player,table,pos);return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){if(state.getBlock()!=next.getBlock()&&!level.isClientSide&&level.getBlockEntity(pos) instanceof WorkshopEntity table)table.dropContents();super.onRemove(state,level,pos,next,moving);}
}
