package ua.empirecraft.kitchenclienttest.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** TEST ONLY: fail instead of silently letting a mesh worker enter LSO's live registry. */
@Mixin(targets="sfiomn.legendarysurvivaloverhaul.util.internal.TemperatureUtilInternal",remap=false)
public class ThreadedTemperatureGuard {
 @Inject(method="getWorldTemperature",at=@At("HEAD"),require=1)
 private void verify(Level level,BlockPos pos,CallbackInfoReturnable<Float> result){
  if(level.isClientSide&&!Minecraft.getInstance().isSameThread())throw new AssertionError("Worker entered unsafe LSO temperature registry");
 }
}
