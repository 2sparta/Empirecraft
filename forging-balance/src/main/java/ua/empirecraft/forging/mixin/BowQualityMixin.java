package ua.empirecraft.forging.mixin;

import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ua.empirecraft.forging.RangedQuality;

@Mixin(value = BowItem.class, remap = false)
public abstract class BowQualityMixin {
    @Redirect(method = "m_5551_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean empirecraft$bowQuality(Level level, Entity projectile, ItemStack weapon, Level originalLevel, LivingEntity shooter, int remaining) {
        RangedQuality.mark(projectile, weapon);
        return level.m_7967_(projectile);
    }
}
