package ua.empirecraft.interactions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import ua.empirecraft.claimguard.WeaponGuard;

public final class ThrownImpacts {
    private static final boolean MOHIST = bukkit();
    private ThrownImpacts() {}

    private static boolean bukkit() {
        try { Class.forName("org.bukkit.Bukkit", false, ThrownImpacts.class.getClassLoader()); return true; }
        catch (ClassNotFoundException missing) { return false; }
    }

    public static boolean tracked(Entity entity) {
        return entity instanceof AbstractArrow && switch (entity.getClass().getName()) {
            case "com.TBK.medieval_boomsticks.server.entity.ThrowableAxe",
                 "com.TBK.medieval_boomsticks.server.entity.ThrowableKnife",
                 "com.TBK.medieval_boomsticks.server.entity.ThrowableSmallRock",
                 "com.TBK.medieval_boomsticks.server.entity.ThrowableLargeRock",
                 "com.TBK.medieval_boomsticks.server.entity.ThrowableWardart",
                 "com.TBK.medieval_boomsticks.server.entity.ThrownJavelin" -> true;
            default -> false;
        };
    }

    public static boolean allows(AbstractArrow arrow, Entity target) {
        Entity owner = arrow.getOwner();
        if (target instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (owner instanceof Player thrower && player != thrower && !thrower.canHarmPlayer(player)) return false;
            if (player instanceof ServerPlayer serverPlayer && !serverPlayer.getServer().isPvpAllowed()) return false;
        }
        if (target.isInvulnerableTo(arrow.damageSources().trident(arrow, owner == null ? arrow : owner))) return false;
        return target.level().isClientSide || !MOHIST || WeaponGuard.damage(arrow, target);
    }

    public static boolean selfImpact(Entity projectile, Player defender, Player attacker) {
        return defender == attacker && tracked(projectile) && allows((AbstractArrow) projectile, defender);
    }
}
