package ua.empirecraft.tfc.mixin;
import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=FarmlandBlock.class,remap=false)
public abstract class FarmlandMixin {
    public boolean canSustainPlant(BlockState state,BlockGetter world,BlockPos pos,Direction face,IPlantable plant){
        var type=plant.getPlantType(world,pos.relative(face));
        return face==Direction.UP&&(type==PlantType.CROP||type==PlantType.PLAINS);
    }
    public boolean isFertile(BlockState state,BlockGetter world,BlockPos pos){
        return world instanceof LevelAccessor level&&FarmlandBlock.getHydration(level,pos)>20;
    }
}
