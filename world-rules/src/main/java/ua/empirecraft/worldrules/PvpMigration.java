package ua.empirecraft.worldrules;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One-time repair of the pack's conflicting wilderness PvP defaults. */
public final class PvpMigration extends SavedData {
    private final Set<String> migrated = new HashSet<>();
    private final Set<String> checked = new HashSet<>();
    private long nextWarning;

    public static PvpMigration load(CompoundTag tag) {
        var data = new PvpMigration();
        for (String key : tag.m_128431_()) if (tag.m_128471_(key)) data.migrated.add(key);
        return data;
    }
    @Override public CompoundTag m_7176_(CompoundTag tag) {
        migrated.stream().sorted().forEach(key -> tag.m_128379_(key,true));
        return tag;
    }
    public static void tick(MinecraftServer server) {
        var data = server.m_129783_().m_8895_().m_164861_(PvpMigration::load,PvpMigration::new,"empirecraft_wilderness_pvp_v1");
        data.apply(server);
    }
    private void apply(MinecraftServer server) {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            Object towny = Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"Towny");
            if (towny == null || !Boolean.TRUE.equals(Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(towny))) return;
            Class<?> apiType = Class.forName("com.palmergames.bukkit.towny.TownyAPI",true,towny.getClass().getClassLoader());
            Object api = apiType.getMethod("getInstance").invoke(null);
            for (var level : server.m_129785_()) {
                Object world = level.getClass().getMethod("getWorld").invoke(level);
                Class<?> worldType = Class.forName("org.bukkit.World");
                String name = worldType.getMethod("getName").invoke(world).toString();
                if (!RulesConfig.WORLDS.get().contains(name)) continue;
                String key = worldType.getMethod("getUID").invoke(world).toString();
                if (checked.contains(key)) continue;
                if (!migrated.contains(key)) {
                    Object rules = apiType.getMethod("getTownyWorld",String.class).invoke(api,name);
                    if (rules == null) continue;
                    worldType.getMethod("setPVP",boolean.class).invoke(world,true);
                    rules.getClass().getMethod("setPVP",boolean.class).invoke(rules,true);
                    rules.getClass().getMethod("save").invoke(rules);
                    migrated.add(key);m_77762_();
                    WorldRules.LOG.info("Enabled wilderness PvP in " + name + "; town/plot protection and friendly fire remain authoritative");
                }
                checked.add(key);
            }
        } catch (ClassNotFoundException standalone) {
            // Ordinary Forge/integrated servers do not have Bukkit or Towny.
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            long now = System.currentTimeMillis();
            if (now >= nextWarning) {
                nextWarning = now + 30000;
                WorldRules.LOG.warning("Wilderness PvP migration waits for Towny: " + unavailable);
            }
        }
    }
}
