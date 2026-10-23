package org.bukkit;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
public final class Bukkit {
    public static final PluginManager MANAGER = new PluginManager();
    public static final Map<UUID, Player> ONLINE = new HashMap<>();
    public static boolean primary = true;
    public static PluginManager getPluginManager() { return MANAGER; }
    public static boolean isPrimaryThread() { return primary; }
    public static Player getPlayer(UUID id) { return ONLINE.get(id); }
}
