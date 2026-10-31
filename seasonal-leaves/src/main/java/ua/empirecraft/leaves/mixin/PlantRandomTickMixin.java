package ua.empirecraft.leaves.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.leaves.PlantGrowth;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PlantRandomTickMixin {
    @Inject(method="m_222972_",at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$growthSwitch(ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        if(PlantGrowth.blocked(level,pos,(BlockState)(Object)this))ci.cancel();
    }
}
