package ua.empirecraft.claimguard.mixin;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Explosion.class, remap = false)
public interface ExplosionAccess {
    @Accessor("f_46012_") Level empirecraft$level();
}
