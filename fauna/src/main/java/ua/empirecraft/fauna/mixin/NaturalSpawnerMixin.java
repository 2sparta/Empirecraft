package ua.empirecraft.fauna.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ua.empirecraft.fauna.FaunaMod;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
  @ModifyVariable(method = "m_186524_", at = @At("HEAD"), argsOnly = true, remap = false)
  private static Iterable<Entity> fauna$separateCaps(Iterable<Entity> source) {
    return FaunaMod.wildMobCapEntities(source);
  }
}
