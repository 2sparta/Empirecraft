package ua.empirecraft.calendar.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "sereneseasons.season.SeasonalCropGrowthHandler", remap = false)
public abstract class FertilityDecayMixin {
    @Inject(method = "onCropGrowth", at = @At("HEAD"), cancellable = true, require = 1)
    private static void empirecraft$allowLeafDecay(Level level, BlockPos pos, BlockState state, CallbackInfo growth, CallbackInfo hook) {
        // A fruit-bearing canopy is seasonal; decay after chopping its trunk is not growth.
        if (state.m_60734_() instanceof LeavesBlock && state.m_61138_(LeavesBlock.f_54418_)
            && state.m_61138_(LeavesBlock.f_54419_) && state.m_61143_(LeavesBlock.f_54418_) == 7
            && !state.m_61143_(LeavesBlock.f_54419_)) hook.cancel();
    }
}
