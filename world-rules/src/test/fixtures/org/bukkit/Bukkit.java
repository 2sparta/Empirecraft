package org.bukkit;
public final class Bukkit {
    public static final org.bukkit.plugin.PluginManager MANAGER=new org.bukkit.plugin.PluginManager();
    public static boolean primary=true;
    public static boolean isPrimaryThread(){return primary;}
    public static org.bukkit.plugin.PluginManager getPluginManager(){return MANAGER;}
}
