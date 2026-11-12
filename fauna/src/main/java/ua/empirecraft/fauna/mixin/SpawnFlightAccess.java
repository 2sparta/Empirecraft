package ua.empirecraft.fauna.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets="com.ninni.spawn.server.entity.base.FlyingWalkingAnimal",remap=false)
public interface SpawnFlightAccess {
  @Invoker("canFly") boolean empirecraft$canFly();
  @Invoker("setFlying") void empirecraft$takeOff(boolean flying);
}
