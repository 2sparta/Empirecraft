package org.bukkit;

import org.bukkit.plugin.PluginManager;

/** Minimal lifecycle fixture; test sources are never packaged in the mod. */
public final class Bukkit {
    public static Object server;
    public static PluginManager manager;
    public static Object getServer() { return server; }
    public static PluginManager getPluginManager() { return manager; }
}

