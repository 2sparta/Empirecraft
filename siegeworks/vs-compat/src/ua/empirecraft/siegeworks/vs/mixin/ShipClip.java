package ua.empirecraft.siegeworks.vs.mixin;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.siegeworks.vs.*;
@Mixin(targets="me.mss1r.axiomata.ballistics.BlockClip",remap=false)public abstract class ShipClip {
 @Inject(method="colliders",at=@At("HEAD"),cancellable=true)private static void clip(BlockGetter getter,Vec3 from,Vec3 to,CallbackInfoReturnable<BlockHitResult> cir){
  if(!(getter instanceof Level level))return;
  var hit=ShipRaycast.first(level,null,from,to,true,s->false);if(hit!=null)cir.setReturnValue(hit.result());
 }
}
