package ua.empirecraft.interactions.mixin;

import com.magistuarmory.block.PaviseBlock;
import com.magistuarmory.block.PaviseUpperCollisionBlock;
import me.mss1r.axiomata.ballistics.damage.StructuralDamageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StructuralDamageSystem.class, remap = false)
public abstract class PaviseDamageMixin {
    @ModifyVariable(method = "applyImpact", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static BlockPos empirecraft$damageAtBase(BlockPos pos, ServerLevel level) {
        if (level.getBlockState(pos).getBlock() instanceof PaviseUpperCollisionBlock
            && level.getBlockState(pos.below()).getBlock() instanceof PaviseBlock) {
            return pos.below();
        }
        return pos;
    }

    @Inject(method = "progress", at = @At("HEAD"), cancellable = true)
    private static void empirecraft$sharedDamage(ServerLevel level, BlockPos pos, BlockState state,
                                                  CallbackInfoReturnable<Float> result) {
        if (!(state.getBlock() instanceof PaviseUpperCollisionBlock)) return;
        var base = pos.below();
        var lower = level.getBlockState(base);
        if (lower.getBlock() instanceof PaviseBlock) result.setReturnValue(StructuralDamageSystem.progress(level, base, lower));
    }
}
