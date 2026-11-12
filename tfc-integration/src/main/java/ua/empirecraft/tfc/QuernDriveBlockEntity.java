package ua.empirecraft.tfc;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;

public final class QuernDriveBlockEntity extends KineticBlockEntity {
    public QuernDriveBlockEntity(BlockPos pos,BlockState state) { this(Integration.DRIVE_ENTITY.get(),pos,state); }
    public QuernDriveBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    @Override public float calculateStressApplied() { lastStressApplied=4f;return lastStressApplied; }
    /** Side funnels can extract the quern's underside output through the drive. */
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){
        if(cap==ForgeCapabilities.ITEM_HANDLER&&side!=Direction.UP&&getLevel()!=null&&getLevel().hasChunkAt(getBlockPos().above())){
            var quern=getLevel().getBlockEntity(getBlockPos().above());
            if(quern instanceof net.dries007.tfc.common.blockentities.QuernBlockEntity)return quern.getCapability(cap,Direction.DOWN);
        }
        return super.getCapability(cap,side);
    }
}
