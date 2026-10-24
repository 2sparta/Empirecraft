package ua.empirecraft.worldrules;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import java.util.logging.Logger;
import net.minecraft.core.BlockPos;

/** Read Towny on the server thread through Towny's loader; never guess an unavailable claim. */
public final class TownyWilderness {
    private final Predicate<String> worlds;
    private Object plugin;
    private Class<?> bukkit, pluginType;
    private Method getInstance, isTownyWorld, getTownBlock;
    private Constructor<?> location;
    private long nextWarning;

    public TownyWilderness() { this(name -> RulesConfig.WORLDS.get().contains(name)); }
    public TownyWilderness(Predicate<String> worlds) { this.worlds=worlds; }
    public boolean allowed(Object level, BlockPos pos) {
        try {
            if (bukkit == null) { bukkit = Class.forName("org.bukkit.Bukkit"); pluginType = Class.forName("org.bukkit.plugin.Plugin"); }
            if (!Boolean.TRUE.equals(bukkit.getMethod("isPrimaryThread").invoke(null))) return false;
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            Object current = Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin", String.class).invoke(manager, "Towny");
            if (current == null || !Boolean.TRUE.equals(pluginType.getMethod("isEnabled").invoke(current))) throw new IllegalStateException("Towny is not enabled");
            if (current != plugin) bind(current);
            Object world = level.getClass().getMethod("getWorld").invoke(level);
            String name = (String)Class.forName("org.bukkit.World").getMethod("getName").invoke(world);
            if (!worlds.test(name)) return false;
            Object api = getInstance.invoke(null);
            if (api == null) throw new IllegalStateException("Towny API is not ready");
            if (!Boolean.TRUE.equals(isTownyWorld.invoke(api, world))) return false;
            Object at = location.newInstance(world, (double)pos.m_123341_(), (double)pos.m_123342_(), (double)pos.m_123343_());
            return getTownBlock.invoke(api, at) == null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            plugin = null;
            if (System.currentTimeMillis() >= nextWarning) {
                nextWarning = System.currentTimeMillis() + 30000;
                Logger.getLogger("EmpireCraftWorldRules").warning("Natural renewal paused: claim status is unavailable: " + e);
            }
            return false;
        }
    }

    private void bind(Object current) throws ReflectiveOperationException {
        Class<?> api = Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, current.getClass().getClassLoader());
        Class<?> world = Class.forName("org.bukkit.World"), loc = Class.forName("org.bukkit.Location");
        getInstance = api.getMethod("getInstance");
        isTownyWorld = api.getMethod("isTownyWorld", world);
        getTownBlock = api.getMethod("getTownBlock", loc);
        location = loc.getConstructor(world, double.class, double.class, double.class);
        plugin = current;
    }
}
