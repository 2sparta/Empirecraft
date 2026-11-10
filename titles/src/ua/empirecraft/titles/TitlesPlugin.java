package ua.empirecraft.titles;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class TitlesPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private static final int PRIORITY = 100;
    private final Map<String, TitleRules.Title> titles = new LinkedHashMap<>();
    private final Set<String> suffixes = new HashSet<>();
    private final Set<String> unavailableSources = new HashSet<>();
    private final Set<UUID> pending = new HashSet<>();
    private LuckPerms permissions;

    @Override public void onEnable() {
        saveDefaultConfig();
        permissions = getServer().getServicesManager().load(LuckPerms.class);
        if (permissions == null) {
            getLogger().severe("LuckPerms API unavailable; title awards disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try (var stream = Objects.requireNonNull(getResource("catalog.yml"))) {
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String id : yaml.getKeys(false)) {
                ConfigurationSection c = Objects.requireNonNull(yaml.getConfigurationSection(id));
                var requirements = new ArrayList<TitleRules.Requirement>();
                for (Map<?, ?> r : c.getMapList("requirements")) {
                    var targets = new ArrayList<String>();
                    if (r.get("targets") instanceof List<?> list) for (Object target : list) targets.add(target.toString());
                    var requirement = new TitleRules.Requirement(Objects.toString(r.get("type"), ""),
                        Objects.toString(r.get("key"), ""), targets, ((Number) r.get("amount")).longValue(),
                        Objects.toString(r.get("description"), ""));
                    ServerProgress.validate(requirement);
                    requirements.add(requirement);
                }
                var title = new TitleRules.Title(id, Objects.requireNonNull(c.getString("permission")),
                    Objects.requireNonNull(c.getString("suffix")), Objects.requireNonNull(c.getString("label")),
                    Objects.requireNonNull(c.getString("kind")), requirements);
                titles.put(id, title);
                suffixes.add(title.suffix());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load title catalog", e);
        }
        Objects.requireNonNull(getCommand("etitle")).setExecutor(this);
        Objects.requireNonNull(getCommand("etitle")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        long seconds = Math.max(10, Math.min(3600, getConfig().getLong("check-interval-seconds", 60)));
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) award(player);
        }, 100L, seconds * 20L);
        getLogger().info("Loaded " + titles.size() + " cosmetic titles. Menu: /titles");
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { queue(event.getPlayer()); }
    @EventHandler public void onAdvancement(PlayerAdvancementDoneEvent event) { queue(event.getPlayer()); }
    private void queue(Player player) {
        if (!pending.add(player.getUniqueId())) return;
        getServer().getScheduler().runTaskLater(this, () -> {
            pending.remove(player.getUniqueId());
            if (player.isOnline()) award(player);
        }, 20L);
    }

    private void award(Player player) {
        if (!player.hasPermission("empirecraft.titles.use")) return;
        User user = permissions.getUserManager().getUser(player.getUniqueId());
        if (user == null) return; // The periodic check retries after LuckPerms finishes loading.
        var port = new Port(player, user);
        int earned = 0;
        for (var title : titles.values()) {
            TitleRules.Result result = TitleRules.claim(port, title);
            if (result == TitleRules.Result.UNLOCKED) earned++;
        }
        if (earned > 0) {
            port.save();
            message(player, "&aОтримано нових титулів: " + earned + ". Оберіть свій у &e/titles&a.");
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Use the player menu /titles."); return true; }
        if (!player.hasPermission("empirecraft.titles.use")) return true;
        User user = permissions.getUserManager().getUser(player.getUniqueId());
        if (user == null) { message(player, "&cДані прав ще завантажуються. Спробуйте знову."); return true; }
        var port = new Port(player, user);
        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            port.clear(); port.save();
            message(player, "&aТитул знято.");
            return true;
        }
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (args.length != 2 || !Set.of("choose", "unlock", "progress").contains(action) || !titles.containsKey(args[1])) {
            message(player, "&eВідкрийте /titles або /etitle progress <ID титулу>."); return true;
        }
        TitleRules.Title title = titles.get(args[1]);
        if (action.equals("progress")) { progress(player, port, title); return true; }
        TitleRules.Result result = action.equals("choose") ? TitleRules.choose(port, title) : TitleRules.unlock(port, title);
        switch (result) {
            case SELECTED, UNLOCKED -> {
                port.save();
                message(player, (result == TitleRules.Result.UNLOCKED ? "&aОтримано титул: " : "&aОбрано титул: ") + title.label());
            }
            case LOCKED, INCOMPLETE, UNAVAILABLE -> progress(player, port, title);
            default -> message(player, "&cНе вдалося зберегти титул. Спробуйте знову пізніше.");
        }
        return true;
    }

    private void progress(Player player, Port port, TitleRules.Title title) {
        message(player, "&6" + title.label() + (port.owns(title.permission()) ? " &a— уже отримано." : " &7— умови отримання:"));
        if (title.kind().equals("season")) { message(player, "&7Пам’ятна нагорода адміністрації за серверну подію."); return; }
        for (var r : title.requirements()) {
            long value = port.progress(r);
            String state = value < 0 ? "&cджерело прогресу тимчасово недоступне" : value >= r.amount() ? "&aвиконано"
                : "&e" + (long) Math.floor(100.0 * value / r.amount()) + "%";
            message(player, "&7" + r.description() + " &8[" + state + "&8]");
        }
    }

    private static void message(Player player, String text) {
        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6Титули &8» " + text));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("choose", "unlock", "progress", "clear").stream().filter(s -> s.startsWith(args[0])).toList();
        if (args.length == 2 && !args[0].equals("clear")) return titles.keySet().stream().filter(s -> s.startsWith(args[1])).toList();
        return List.of();
    }

    private final class Port implements TitleRules.PlayerPort {
        private final User user;
        private final ServerProgress progress;
        private final Set<String> granted = new HashSet<>();
        private Port(Player player, User user) {
            this.user = user;
            progress = new ServerProgress(player, source -> {
                if (unavailableSources.add(source)) getLogger().warning("Title progress source unavailable: " + source);
            });
        }
        @Override public boolean owns(String permission) {
            return granted.contains(permission) || user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        }
        @Override public long progress(TitleRules.Requirement requirement) { return progress.read(requirement); }
        @Override public boolean grant(String permission) {
            boolean success = user.data().add(PermissionNode.builder(permission).value(true).build()).wasSuccessful();
            if (success) granted.add(permission);
            return success;
        }
        private void clear() {
            // Keep ranks, unrelated suffixes, and every other suffix priority.
            user.data().clear(node -> node instanceof SuffixNode suffix && suffix.getPriority() == PRIORITY && suffixes.contains(suffix.getMetaValue()));
        }
        @Override public void select(String suffix) {
            clear();
            user.data().add(SuffixNode.builder(suffix, PRIORITY).build());
        }
        private void save() {
            permissions.getUserManager().saveUser(user).exceptionally(error -> {
                getLogger().severe("Could not persist title for UUID " + user.getUniqueId() + ": " + error.getMessage());
                return null;
            });
        }
    }
}
