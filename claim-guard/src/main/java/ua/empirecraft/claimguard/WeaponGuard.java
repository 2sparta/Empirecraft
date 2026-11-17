package ua.empirecraft.claimguard;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

public final class WeaponGuard {
    public static final String SHOOTER = "EmpireCraftClaimGuardShooter";
    private static final TownyGate TOWNY = new TownyGate();
    private WeaponGuard() {}

    public static boolean isWeapon(Entity entity) {
        if (entity == null) return false;
        for (Class<?> c = entity.getClass(); c != null; c = c.getSuperclass()) {
            String n = c.getName();
            if (n.equals("net.mehvahdjukaar.supplementaries.common.entities.BombEntity")
                || n.equals("net.mehvahdjukaar.supplementaries.common.entities.CannonBallEntity")
                || n.equals("me.mss1r.siegeworks.entity.base.SiegeProjectile")
                || n.equals("me.mss1r.siegeworks.entity.base.AbstractSiegeEntity")) return true;
        }
        return false;
    }

    public static Entity weapon(Explosion explosion) {
        Entity direct = explosion.m_46077_().m_7640_();
        return isWeapon(direct) ? direct : isWeapon(explosion.m_253049_()) ? explosion.m_253049_() : null;
    }

    public static Entity weapon(DamageSource source) {
        return isWeapon(source.m_7640_()) ? source.m_7640_() : isWeapon(source.m_7639_()) ? source.m_7639_() : null;
    }

    public static UUID shooter(Entity weapon) {
        if (weapon == null) return null;
        if (weapon.getPersistentData().m_128403_(SHOOTER)) return weapon.getPersistentData().m_128342_(SHOOTER);
        Entity owner = weapon instanceof Projectile p ? p.m_19749_() : null;
        // Never infer the shooter from whoever happens to occupy a siege engine at impact.
        return owner instanceof Player ? owner.m_20148_() : null;
    }

    public static void rememberShooter(Entity weapon, Entity shooter) {
        if (!weapon.m_9236_().m_5776_() && shooter instanceof Player) {
            weapon.getPersistentData().m_128362_(SHOOTER, shooter.m_20148_());
        }
    }

    public static boolean block(Level level, BlockPos pos) {
        return level.m_5776_() || TOWNY.allows(level, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), TownyGate.Target.BLOCK, null);
    }

    public static boolean damage(Entity weapon, Entity victim) {
        Level level = victim.m_9236_();
        if (level.m_5776_()) return true;
        BlockPos pos = victim.m_20183_();
        TownyGate.Target kind = victim instanceof Player ? TownyGate.Target.PLAYER
            : victim instanceof Mob ? TownyGate.Target.MOB : TownyGate.Target.OBJECT;
        return TOWNY.allows(level, pos.m_123341_(), pos.m_123342_(), pos.m_123343_(), kind, shooter(weapon));
    }

    public static void filterBlocks(Explosion explosion, Level level) {
        if (!level.m_5776_()) explosion.m_46081_().removeIf(pos -> !block(level, pos));
    }

    public static List<Entity> filterEntities(List<Entity> entities, Entity weapon) {
        entities.removeIf(victim -> !damage(weapon, victim));
        return entities;
    }
}
