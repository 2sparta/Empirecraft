package org.bukkit.plugin;
public final class PluginManager {
    public Plugin plugin=new FakePlugin();
    public Plugin getPlugin(String name){return name.equals("Towny")?plugin:null;}
    public static final class FakePlugin implements Plugin {
        public boolean enabled=true;
        public boolean isEnabled(){return enabled;}
    }
}
