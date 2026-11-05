package ua.empirecraft.fauna.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.fauna.FaunaMod;

@Mixin(NaturalSpawner.SpawnState.class)
public abstract class SpawnStateMixin {
  @Inject(method = "m_47131_", at = @At("HEAD"), cancellable = true, remap = false)
  private void fauna$independentCap(Mob mob, ChunkAccess chunk, CallbackInfo callback) {
    if (FaunaMod.captive(mob)) callback.cancel();
  }
}
