package ua.empirecraft.leaves.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.leaves.LeafSupportGuard;

@Mixin(value=LeavesBlock.class,remap=false)
public abstract class LeafDecayMixin {
    @Inject(method="m_213898_",at=@At("HEAD"),cancellable=true)
    private void empirecraft$keepSupport(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        if(LeafSupportGuard.protectedAt(level,pos))ci.cancel();
    }
}
