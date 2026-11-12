package ua.empirecraft.tfc;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** A shaft enters from below; the native TFC quern rests directly above. */
public final class QuernDriveBlock extends KineticBlock implements IBE<QuernDriveBlockEntity> {
    public QuernDriveBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction face) { return face==Direction.DOWN; }
    @Override public Class<QuernDriveBlockEntity> getBlockEntityClass() { return QuernDriveBlockEntity.class; }
    @Override public BlockEntityType<? extends QuernDriveBlockEntity> getBlockEntityType() { return Integration.DRIVE_ENTITY.get(); }
}
