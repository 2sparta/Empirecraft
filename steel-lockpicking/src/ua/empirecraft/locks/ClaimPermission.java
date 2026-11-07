package ua.empirecraft.locks;

import java.lang.reflect.Method;
import java.util.UUID;
import melonslise.locks.common.util.Lockable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/** Optional Mohist/Towny bridge. No Bukkit classes are linked on a normal Forge client/server. */
public final class ClaimPermission {
    private ClaimPermission() {}
    public static boolean allowed(Player player, Lockable lockable) {
        try {
            Class<?> bukkit;
            try { bukkit = Class.forName("org.bukkit.Bukkit"); }
            catch (ClassNotFoundException absent) { return true; }
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            if (manager == null) return false;
            Class<?> pm = Class.forName("org.bukkit.plugin.PluginManager");
            Object towny = pm.getMethod("getPlugin", String.class).invoke(manager, "Towny");
            if (towny == null) return true;
            if (!(boolean)Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(towny)) return false;
            Object online = bukkit.getMethod("getPlayer", UUID.class).invoke(null, player.getUUID());
            if (online == null) return false;
            ClassLoader loader = towny.getClass().getClassLoader();
            Class<?> bp = Class.forName("org.bukkit.entity.Player");
            Class<?> worldType = Class.forName("org.bukkit.World");
            Object world = player.level().getClass().getMethod("getWorld").invoke(player.level());
            if (world == null || !world.equals(bp.getMethod("getWorld").invoke(online))) return false;
            Class<?> location = Class.forName("org.bukkit.Location"), material = Class.forName("org.bukkit.Material");
            Class<?> action = Class.forName("com.palmergames.bukkit.towny.object.TownyPermission$ActionType", true, loader);
            Object use = action.getField("SWITCH").get(null);
            Method permission = Class.forName("com.palmergames.bukkit.towny.utils.PlayerCacheUtil", true, loader)
                    .getMethod("getCachePermission", bp, location, material, action);
            for (BlockPos pos : lockable.bb.getContainedPos()) {
                Object block = worldType.getMethod("getBlockAt", int.class, int.class, int.class).invoke(world, pos.getX(), pos.getY(), pos.getZ());
                Object type = Class.forName("org.bukkit.block.Block").getMethod("getType").invoke(block);
                Object loc = location.getConstructor(worldType, double.class, double.class, double.class)
                        .newInstance(world, (double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
                if (!(boolean)permission.invoke(null, online, loc, type, use)) return false;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            // Towny installed but not ready / incompatible: do not silently bypass a claim.
            return false;
        }
    }
}
