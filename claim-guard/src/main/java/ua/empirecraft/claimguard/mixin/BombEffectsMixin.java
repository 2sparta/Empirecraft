package ua.empirecraft.claimguard.mixin;

import java.util.List;
import net.mehvahdjukaar.supplementaries.common.misc.explosion.BombExplosion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.empirecraft.claimguard.WeaponGuard;

/** Spiky poison has its own radius and an unattributed magic DamageSource. */
@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.entities.BombEntity$BombType", remap = false)
public abstract class BombEffectsMixin {
    @Redirect(method = "afterExploded", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;m_45933_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> empirecraft$protectSecondaryEffects(Level level, Entity excluded, AABB area,
            BombExplosion explosion, Level originalLevel) {
        return WeaponGuard.filterEntities(level.m_45933_(excluded, area), WeaponGuard.weapon(explosion));
    }
}
