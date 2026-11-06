package ua.empirecraft.engagement.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.*;
import ua.empirecraft.engagement.*;

@Mixin(value=Explosion.class,remap=false)
public abstract class ExplosionScopeMixin {
    @WrapMethod(method="m_46061_") private void calculate(Operation<Void> original){try(var ignored=ShotOrigins.enter(CombatEvents.explosionOrigin((Explosion)(Object)this))){original.call();}}
    @WrapMethod(method="m_46075_") private void finish(boolean particles,Operation<Void> original){try(var ignored=ShotOrigins.enter(CombatEvents.explosionOrigin((Explosion)(Object)this))){original.call(particles);}}
}
