package ua.empirecraft.worldrules.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=BrewingStandBlockEntity.class,remap=false)
public abstract class BrewingMixin {
    @Inject(method="m_155285_",at=@At("HEAD"),cancellable=true)
    private static void empirecraft$noBrewing(Level level,BlockPos pos,BlockState state,BrewingStandBlockEntity stand,CallbackInfo ci) { ci.cancel(); }
}
