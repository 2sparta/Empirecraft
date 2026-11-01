package ua.empirecraft.leaves.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.leaves.SeasonalVegetation;
@Mixin(value=SaplingBlock.class,remap=false)
public abstract class NaturalSaplingMixin {
    @Inject(method="m_222000_",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private void slowNaturalGrowth(ServerLevel l,BlockPos pos,BlockState state,RandomSource random,CallbackInfo ci){
        if(SeasonalVegetation.delayTree(l,pos))ci.cancel();
    }
}
