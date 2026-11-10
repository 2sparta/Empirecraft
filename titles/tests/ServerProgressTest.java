package ua.empirecraft.titles;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarFile;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

/** Exercise real Bukkit enums, YAML parsing and overloaded statistics through interface proxies. */
public final class ServerProgressTest {
    private static int checks, reads;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Adapter check " + checks); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    public static void main(String[] args) throws Exception {
        var warnings = new ArrayList<String>();
        PluginManager plugins = proxy(PluginManager.class, (p, m, a) -> m.getName().equals("isPluginEnabled") ? false : null);
        Server server = proxy(Server.class, (p, m, a) -> m.getName().equals("getPluginManager") ? plugins : null);
        Map<Object, Integer> counts = new HashMap<>();
        counts.put(Material.DIAMOND_ORE, 20); counts.put(Material.DEEPSLATE_DIAMOND_ORE, 44);
        counts.put(EntityType.SKELETON, 100); counts.put(Statistic.PLAY_ONE_MINUTE, 72000);
        Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getServer" -> server;
            case "getUniqueId" -> new UUID(0, 1);
            case "getStatistic" -> { reads++; yield counts.getOrDefault(a[a.length - 1], 0); }
            default -> null;
        });
        var progress = new ServerProgress(player, warnings::add);
        var ore = new TitleRules.Requirement("statistic", "MINE_BLOCK", List.of("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"), 64, "Ore");
        check(progress.read(ore) == 64); check(reads == 2);
        counts.put(Material.DIAMOND_ORE, 999);
        check(progress.read(ore) == 64 && reads == 2); // One check uses one coherent cached snapshot.
        check(new ServerProgress(player, warnings::add).read(ore) == 1043); // Next check reads new state.
        counts.put(Material.DIAMOND_ORE, Integer.MAX_VALUE); counts.put(Material.DEEPSLATE_DIAMOND_ORE, Integer.MAX_VALUE);
        check(new ServerProgress(player, warnings::add).read(ore) == (long)Integer.MAX_VALUE * 2);
        var kills = new TitleRules.Requirement("statistic", "KILL_ENTITY", List.of("SKELETON"), 100, "Skeletons");
        check(progress.read(kills) == 100);
        var hours = new TitleRules.Requirement("statistic", "PLAY_ONE_MINUTE", List.of(), 72000, "Hour");
        check(progress.read(hours) == 72000);
        var missing = new TitleRules.Requirement("advancement", "missing:example", List.of(), 1, "Missing");
        check(progress.read(missing) == -1);
        var town = new TitleRules.Requirement("towny", "member", List.of(), 1, "Town");
        check(progress.read(town) == -1); // An absent plugin cannot award membership.
        check(warnings.size() == 2);
        boolean invalid = false;
        try { ServerProgress.validate(new TitleRules.Requirement("statistic", "MINE_BLOCK", List.of(), 1, "Bad")); }
        catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid);
        // Parse every production requirement and validate against the actual 1.20.1 API.
        try (var jar = new JarFile(args[0]); var stream = jar.getInputStream(jar.getJarEntry("catalog.yml"))) {
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            int titles = 0;
            for (String id : yaml.getKeys(false)) {
                var c = Objects.requireNonNull(yaml.getConfigurationSection(id));
                for (Map<?, ?> r : c.getMapList("requirements")) {
                    var targets = new ArrayList<String>();
                    for (Object target : (List<?>)r.get("targets")) targets.add(target.toString());
                    ServerProgress.validate(new TitleRules.Requirement(r.get("type").toString(), r.get("key").toString(),
                        targets, ((Number)r.get("amount")).longValue(), r.get("description").toString()));
                }
                titles++;
            }
            check(titles == 137);
        }
        System.out.println("Server progress adapter checks passed: " + checks);
    }
}
