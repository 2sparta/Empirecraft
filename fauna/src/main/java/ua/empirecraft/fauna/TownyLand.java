package ua.empirecraft.fauna;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Towny's current plot location and effective level; no animal ownership or farm registration. */
public final class TownyLand {
  private Object plugin, api;
  private Class<?> bukkit, settings;
  private boolean dirty = true;
  private Population.Land snapshot = Population.Land.unavailable();

  public void invalidate() {
    dirty = true;
  }

  public Population.Land read(MinecraftServer server, boolean force)
      throws ReflectiveOperationException {
    if (!server.isSameThread())
      throw new IllegalStateException("Towny is confined to the server thread");
    if (!dirty && !force) return snapshot;
    bukkit = Class.forName("org.bukkit.Bukkit");
    Object manager = bukkit.getMethod("getPluginManager").invoke(null);
    Object current =
        Class.forName("org.bukkit.plugin.PluginManager")
            .getMethod("getPlugin", String.class)
            .invoke(manager, "Towny");
    if (current == null
        || !Boolean.TRUE.equals(
            Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(current)))
      throw new IllegalStateException("Towny is not ready");
    if (plugin != current) {
      plugin = current;
      ClassLoader loader = current.getClass().getClassLoader();
      api =
          Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, loader)
              .getMethod("getInstance")
              .invoke(null);
      settings = Class.forName("com.palmergames.bukkit.towny.TownySettings", true, loader);
      listen(manager, loader);
    }
    int size = ((Number) settings.getMethod("getTownBlockSize").invoke(null)).intValue();
    if (size < 1 || size > 1024) throw new IllegalStateException("Invalid Towny plot size");
    var claims = new HashMap<String, String>();
    var cities = new LinkedHashMap<String, Population.City>();
    var dimensions = new HashMap<String, String>();
    for (Object town : (Collection<?>) call(api, "getTowns")) {
      String id = call(town, "getUUID").toString(),
          name = call(town, "getName").toString().replace('_', ' ');
      int level = ((Number) call(town, "getLevelNumber")).intValue();
      cities.put(id, new Population.City(id, name, level, (boolean) call(town, "isCapital")));
      for (Object block : (Collection<?>) call(town, "getTownBlocks")) {
        String worldName = call(call(block, "getWorld"), "getName").toString(),
            dimension = dimensions.get(worldName);
        if (dimension == null) {
          Object world = bukkit.getMethod("getWorld", String.class).invoke(null, worldName);
          if (world == null) continue;
          Object handle = call(world, "getHandle");
          if (!(handle instanceof ServerLevel levelHandle)) continue;
          dimension = levelHandle.dimension().location().toString();
          dimensions.put(worldName, dimension);
        }
        claims.put(dimension + "/" + call(block, "getX") + "/" + call(block, "getZ"), id);
      }
    }
    var next = new Population.Land(true, size, Map.copyOf(claims), Map.copyOf(cities));
    if (!next.equals(snapshot)) snapshot = next;
    dirty = false;
    return snapshot;
  }

  private void listen(Object manager, ClassLoader loader) throws ReflectiveOperationException {
    Class<?> listener = Class.forName("org.bukkit.event.Listener"),
        executor = Class.forName("org.bukkit.plugin.EventExecutor"),
        priority = Class.forName("org.bukkit.event.EventPriority"),
        pluginClass = Class.forName("org.bukkit.plugin.Plugin");
    Object marker =
        Proxy.newProxyInstance(loader, new Class[] {listener}, (p, m, a) -> objectMethod(p, m, a));
    Object callback =
        Proxy.newProxyInstance(
            loader,
            new Class[] {executor},
            (p, m, a) -> {
              if (m.getName().equals("execute")) {
                dirty = true;
                return null;
              }
              return objectMethod(p, m, a);
            });
    Object monitor = Enum.valueOf((Class) priority, "MONITOR");
    Method register =
        Class.forName("org.bukkit.plugin.PluginManager")
            .getMethod(
                "registerEvent",
                Class.class,
                listener,
                priority,
                executor,
                pluginClass,
                boolean.class);
    for (String name :
        List.of(
            "TownClaimEvent",
            "town.TownUnclaimEvent",
            "NewTownEvent",
            "DeleteTownEvent",
            "RenameTownEvent",
            "TownAddResidentEvent",
            "TownRemoveResidentEvent",
            "town.TownLevelIncreaseEvent",
            "town.TownLevelDecreaseEvent",
            "NationAddTownEvent",
            "NationRemoveTownEvent")) {
      Class<?> event = Class.forName("com.palmergames.bukkit.towny.event." + name, true, loader);
      register.invoke(manager, event, marker, monitor, callback, plugin, true);
    }
  }

  static Object objectMethod(Object proxy, Method method, Object[] args) {
    return switch (method.getName()) {
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == args[0];
      case "toString" -> "Empirecraft fauna claim listener";
      default -> null;
    };
  }

  public static Object call(Object target, String method) throws ReflectiveOperationException {
    return target.getClass().getMethod(method).invoke(target);
  }
}
