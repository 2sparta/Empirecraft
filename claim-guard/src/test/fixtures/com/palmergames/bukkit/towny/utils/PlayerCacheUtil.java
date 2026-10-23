package com.palmergames.bukkit.towny.utils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.object.TownyPermission.ActionType;
public final class PlayerCacheUtil {
    public static int calls;
    public static boolean getCachePermission(Player player, Location loc, Material material, ActionType action) {
        calls++;
        if (action != ActionType.DESTROY || material != Material.DIAMOND_BLOCK) throw new AssertionError("Incorrect Towny permission API arguments");
        return loc.world.at(loc.x, loc.z).permitted.contains(player.id());
    }
}
