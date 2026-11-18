package ua.empirecraft.interactions.mixin;

import com.magistuarmory.block.PaviseBlock;
import com.magistuarmory.block.PaviseUpperCollisionBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.interactions.PaviseShapes;

@Mixin(value = {PaviseBlock.class, PaviseUpperCollisionBlock.class}, remap = false)
public abstract class PaviseShapeMixin {
    @Inject(method = "m_5940_", at = @At("HEAD"), cancellable = true)
    private void empirecraft$paviseShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                        CallbackInfoReturnable<VoxelShape> result) {
        result.setReturnValue(state.getBlock() instanceof PaviseBlock
            ? PaviseShapes.lower(state) : PaviseShapes.upper(level.getBlockState(pos.below())));
    }
}
