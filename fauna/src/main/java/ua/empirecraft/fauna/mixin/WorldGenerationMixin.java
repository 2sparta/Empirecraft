package ua.empirecraft.fauna.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.fauna.*;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenerationMixin {
  @Inject(method = "m_7967_", at = @At("HEAD"), cancellable = true, remap = false)
  private void fauna$check(Entity entity, CallbackInfoReturnable<Boolean> callback) {
    var s = FaunaMod.session;
    if (s != null
        && entity instanceof Mob mob
        && FaunaTypes.managed(mob.getType())
        && !s.beforeGeneration(mob, ((WorldGenRegion) (Object) this).getLevel()))
      callback.setReturnValue(false);
  }

  @Inject(method = "m_7967_", at = @At("RETURN"), remap = false)
  private void fauna$stored(Entity entity, CallbackInfoReturnable<Boolean> callback) {
    var s = FaunaMod.session;
    if (s != null && entity instanceof Mob mob && FaunaTypes.managed(mob.getType()))
      s.afterGeneration(mob, callback.getReturnValue());
  }
}
