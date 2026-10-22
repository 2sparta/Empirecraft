package ua.empirecraft.townymap;

import net.minecraft.server.level.ServerLevel;
import java.lang.reflect.Method;
import java.util.*;

/** Uses Towny's own plugin classloader on Mohist, without bundling Bukkit or Towny classes. */
final class TownyAccess {
    private final Class<?> bukkit;
    private final Object plugin;
    private final Object api;
    private final Class<?> settings;
    private final Map<Class<?>, Map<String, Method>> methods = new HashMap<>();

    TownyAccess() throws ReflectiveOperationException {
        bukkit = Class.forName("org.bukkit.Bukkit");
        Object manager = bukkit.getMethod("getPluginManager").invoke(null);
        plugin = Class.forName("org.bukkit.plugin.PluginManager")
                .getMethod("getPlugin", String.class).invoke(manager, "Towny");
        if (plugin == null || !enabled()) throw new IllegalStateException("Towny is not enabled");
        ClassLoader loader = plugin.getClass().getClassLoader();
        api = Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, loader)
                .getMethod("getInstance").invoke(null);
        settings = Class.forName("com.palmergames.bukkit.towny.TownySettings", true, loader);
    }

    boolean enabled() throws ReflectiveOperationException {
        return (boolean) Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(plugin);
    }

    Map<String, Claims> snapshot(boolean independentTowns) throws ReflectiveOperationException {
        if (!enabled()) throw new IllegalStateException("Towny is not enabled");
        int size = ((Number) settings.getMethod("getTownBlockSize").invoke(null)).intValue();
        if (size < 1 || size > 1024) throw new IllegalStateException("Invalid Towny town block size: " + size);
        Map<String, MutableClaims> groups = new TreeMap<>();
        Map<String, String> dimensions = new HashMap<>();
        for (Object town : (Collection<?>) call(api, "getTowns")) {
            Object nation = call(town, "getNationOrNull");
            if (nation == null && !independentTowns) continue;
            Object owner = nation == null ? town : nation;
            String ownerId = (nation == null ? "town/" : "nation/") + call(owner, "getUUID");
            String name = call(owner, "getName").toString().replace('_', ' ');
            int color = mapColor(call(owner, "getMapColorHexCode"), ownerId);
            for (Object block : (Collection<?>) call(town, "getTownBlocks")) {
                String worldName = call(call(block, "getWorld"), "getName").toString();
                String dimension = dimensions.get(worldName);
                if (dimension == null) {
                    Object world = bukkit.getMethod("getWorld", String.class).invoke(null, worldName);
                    if (world == null) continue;
                    Object handle = call(world, "getHandle");
                    if (!(handle instanceof ServerLevel level))
                        throw new IllegalStateException("Bukkit world does not expose a Mohist ServerLevel: " + worldName);
                    dimension = level.dimension().location().toString();
                    dimensions.put(worldName, dimension);
                }
                String id = ownerId + "/" + dimension;
                MutableClaims group = groups.get(id);
                if (group == null) {
                    group = new MutableClaims(id, name, dimension, color, size);
                    groups.put(id, group);
                }
                group.cells.add(new ClaimGeometry.Cell(((Number) call(block, "getX")).intValue(),
                        ((Number) call(block, "getZ")).intValue()));
            }
        }
        Map<String, Claims> snapshot = new TreeMap<>();
        groups.forEach((id, g) -> snapshot.put(id, new Claims(g.id, g.name, g.dimension, g.color, g.size, g.cells)));
        return Map.copyOf(snapshot);
    }

    private Object call(Object target, String name) throws ReflectiveOperationException {
        Map<String, Method> cached = methods.computeIfAbsent(target.getClass(), c -> new HashMap<>());
        Method method = cached.get(name);
        if (method == null) {
            method = target.getClass().getMethod(name);
            cached.put(name, method);
        }
        return method.invoke(target);
    }

    static int mapColor(Object raw, String id) {
        if (raw != null) {
            String value = raw.toString().trim().replaceFirst("^(#|0[xX])", "");
            if (value.matches("[0-9a-fA-F]{6}")) return Integer.parseInt(value, 16);
        }
        return java.awt.Color.HSBtoRGB((id.hashCode() & 0x7fffffff) / (float) Integer.MAX_VALUE, 0.65f, 0.9f) & 0xffffff;
    }

    private static final class MutableClaims {
        final String id, name, dimension;
        final int color, size;
        final Set<ClaimGeometry.Cell> cells = new HashSet<>();
        MutableClaims(String id, String name, String dimension, int color, int size) {
            this.id = id; this.name = name; this.dimension = dimension; this.color = color; this.size = size;
        }
    }
}
