package ua.empirecraft.claimguard;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/** Bukkit and Towny must be resolved through their own loaders, after plugin enable. */
public final class TownyGate {
    public enum Target { BLOCK, PLAYER, MOB, OBJECT }
    private static final Logger LOG = Logger.getLogger("EmpireCraftClaimGuard");
    private static volatile long nextWarning;
    private Binding binding;

    public boolean allows(Object level, int x, int y, int z, Target target, UUID shooter) {
        try {
            if (NativeTowny.impact != null) {
                return Boolean.TRUE.equals(NativeTowny.impact.invoke(null, level, x, y, z, target.name(), shooter));
            }
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            Class<?> managerType = Class.forName("org.bukkit.plugin.PluginManager");
            Object plugin = managerType.getMethod("getPlugin", String.class).invoke(manager, "Towny");
            Class<?> pluginType = Class.forName("org.bukkit.plugin.Plugin");
            if (plugin == null || !Boolean.TRUE.equals(pluginType.getMethod("isEnabled").invoke(plugin))) {
                throw new IllegalStateException("Towny is not enabled");
            }
            if (!Boolean.TRUE.equals(bukkit.getMethod("isPrimaryThread").invoke(null))) {
                throw new IllegalStateException("Weapon operation outside the server thread");
            }
            if (binding == null || binding.plugin != plugin) binding = new Binding(plugin, bukkit);
            Object world = level.getClass().getMethod("getWorld").invoke(level);
            Object location = binding.location.newInstance(world, (double)x, (double)y, (double)z);
            Object api = binding.instance.invoke(null);
            if (api == null) throw new IllegalStateException("Towny API is not ready");
            if (!Boolean.TRUE.equals(binding.isTownyWorld.invoke(api, world))) return true;
            if (binding.getTownBlock.invoke(api, location) == null) return true;
            if (target == Target.PLAYER) return Boolean.TRUE.equals(binding.isPvp.invoke(api, location));
            if (target != Target.MOB || shooter == null) return false;
            Object player = binding.getPlayer.invoke(null, shooter);
            if (player == null) return false;
            Object block = binding.getBlock.invoke(location);
            Object material = binding.blockType.invoke(block);
            return Boolean.TRUE.equals(binding.permission.invoke(null, player, location, material, binding.destroy));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            binding = null;
            long now = System.currentTimeMillis();
            if (now >= nextWarning) {
                nextWarning = now + 30_000;
                LOG.warning("Claim lookup unavailable; mod weapon damage and block changes are denied until Towny recovers: " + failure);
            }
            return false;
        }
    }

    private static final class NativeTowny {
        static final Method impact = find();

        private static Method find() {
            try {
                return Class.forName("ua.empirecraft.townyforge.TownyForge", false, TownyGate.class.getClassLoader())
                        .getMethod("allowsImpact", Object.class, int.class, int.class, int.class, String.class, UUID.class);
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (NoSuchMethodException incompatible) {
                throw new IllegalStateException("The native Towny impact API is unavailable", incompatible);
            }
        }
    }

    private static final class Binding {
        final Object plugin, destroy;
        final Constructor<?> location;
        final Method instance, isTownyWorld, getTownBlock, isPvp, getPlayer, getBlock, blockType, permission;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Binding(Object plugin, Class<?> bukkit) throws ReflectiveOperationException {
            this.plugin = plugin;
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> api = Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, loader);
            Class<?> loc = Class.forName("org.bukkit.Location");
            Class<?> world = Class.forName("org.bukkit.World");
            Class<?> player = Class.forName("org.bukkit.entity.Player");
            Class<?> material = Class.forName("org.bukkit.Material");
            Class<?> action = Class.forName("com.palmergames.bukkit.towny.object.TownyPermission$ActionType", true, loader);
            Class<?> cache = Class.forName("com.palmergames.bukkit.towny.utils.PlayerCacheUtil", true, loader);
            location = loc.getConstructor(world, double.class, double.class, double.class);
            instance = api.getMethod("getInstance");
            isTownyWorld = api.getMethod("isTownyWorld", world);
            getTownBlock = api.getMethod("getTownBlock", loc);
            isPvp = api.getMethod("isPVP", loc);
            getPlayer = bukkit.getMethod("getPlayer", UUID.class);
            getBlock = loc.getMethod("getBlock");
            blockType = Class.forName("org.bukkit.block.Block").getMethod("getType");
            permission = cache.getMethod("getCachePermission", player, loc, material, action);
            destroy = Enum.valueOf((Class)action, "DESTROY");
        }
    }
}
