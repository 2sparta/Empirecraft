package ua.empirecraft.titles;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.SuffixNode;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class TitlesPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final int PRIORITY = 100;
    private final Map<String, TitleRules.Title> titles = new LinkedHashMap<>();
    private final Set<String> suffixes = new HashSet<>();
    private LuckPerms permissions;

    @Override public void onEnable() {
        saveDefaultConfig();
        permissions = getServer().getServicesManager().load(LuckPerms.class);
        if (permissions == null) {
            getLogger().severe("LuckPerms API unavailable; title transactions disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try (var stream = Objects.requireNonNull(getResource("catalog.yml"))) {
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String id : yaml.getKeys(false)) {
                ConfigurationSection c = Objects.requireNonNull(yaml.getConfigurationSection(id));
                var title = new TitleRules.Title(id, c.getString("permission"), c.getString("suffix"),
                    c.getString("label"), c.getString("kind"), c.getString("material", "AIR"), c.getInt("amount"));
                if (!Set.of("shop", "achievement", "season").contains(title.kind()) ||
                    title.permission() == null || title.suffix() == null || title.label() == null ||
                    title.kind().equals("achievement") && (Material.matchMaterial(title.material()) == null || title.amount() <= 0))
                    throw new IllegalArgumentException("Invalid title: " + id);
                titles.put(id, title);
                suffixes.add(title.suffix());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load title catalog", e);
        }
        Objects.requireNonNull(getCommand("etitle")).setExecutor(this);
        Objects.requireNonNull(getCommand("etitle")).setTabCompleter(this);
        getLogger().info("Loaded " + titles.size() + " cosmetic titles. Menu: /titles");
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
        if (args.length != 2 || !Set.of("choose", "unlock").contains(args[0]) || !titles.containsKey(args[1])) {
            message(player, "&eВідкрийте меню командою /titles."); return true;
        }
        TitleRules.Title title = titles.get(args[1]);
        TitleRules.Result result = args[0].equals("choose") ? TitleRules.choose(port, title) :
            TitleRules.unlock(port, title, getConfig().getDouble("shop-price", 30.0));
        switch (result) {
            case SELECTED, UNLOCKED -> {
                port.save();
                message(player, (result == TitleRules.Result.UNLOCKED ? "&aОтримано титул: " : "&aОбрано титул: ") + title.label());
            }
            case LOCKED -> message(player, "&cЦей титул вам ще недоступний.");
            case NO_MONEY -> message(player, "&cНедостатньо коштів або економіка зараз недоступна.");
            case NO_ITEMS -> message(player, "&cНедостатньо звичайних предметів. Іменні та змінені предмети не приймаються.");
            default -> message(player, "&cОперація недоступна. Зверніться до адміністрації.");
        }
        return true;
    }

    private static void message(Player player, String text) {
        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6Титули &8» " + text));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("choose", "unlock", "clear").stream().filter(s -> s.startsWith(args[0])).toList();
        if (args.length == 2 && !args[0].equals("clear")) return titles.keySet().stream().filter(s -> s.startsWith(args[1])).toList();
        return List.of();
    }

    private final class Port implements TitleRules.PlayerPort {
        private final Player player;
        private final User user;
        private Economy chargedEconomy;
        private final Map<Integer, ItemStack> consumed = new LinkedHashMap<>();
        private Port(Player player, User user) { this.player = player; this.user = user; }
        @Override public boolean owns(String permission) {
            return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        }
        @Override public boolean withdraw(double price) {
            if (!getServer().getPluginManager().isPluginEnabled("Vault")) return false;
            Economy economy = getServer().getServicesManager().load(Economy.class);
            if (economy == null) return false;
            EconomyResponse response = economy.withdrawPlayer(player, price);
            if (response == null || !response.transactionSuccess()) return false;
            chargedEconomy = economy;
            return true;
        }
        @Override public boolean consume(String name, int amount) {
            Material material = Material.matchMaterial(name);
            PlayerInventory inventory = player.getInventory();
            // Bukkit player slots 0..35 and offhand 40. Armor is excluded.
            int[] slots = new int[37];
            for (int i = 0; i < 36; i++) slots[i] = i;
            slots[36] = 40;
            int[] stacks = new int[slots.length];
            for (int i = 0; i < slots.length; i++) {
                ItemStack item = inventory.getItem(slots[i]);
                if (item != null && item.getType() == material && !item.hasItemMeta()) stacks[i] = item.getAmount();
            }
            int[] plan = TitleRules.plan(stacks, amount);
            if (plan == null) return false;
            // No command dispatch, scheduling, or inventory events between check and removal.
            for (int i = 0; i < plan.length; i++) if (plan[i] > 0) {
                ItemStack item = Objects.requireNonNull(inventory.getItem(slots[i])).clone();
                consumed.put(slots[i], item.clone());
                item.setAmount(item.getAmount() - plan[i]);
                inventory.setItem(slots[i], item.getAmount() == 0 ? null : item);
            }
            return true;
        }
        @Override public void refundMoney(double price) {
            if (chargedEconomy == null || !chargedEconomy.depositPlayer(player, price).transactionSuccess())
                getLogger().severe("Title refund failed for UUID " + player.getUniqueId() + ": " + price);
        }
        @Override public void refundItems(String material, int amount) {
            consumed.forEach((slot, original) -> player.getInventory().setItem(slot, original));
        }
        @Override public boolean grant(String permission) {
            return user.data().add(PermissionNode.builder(permission).value(true).build()).wasSuccessful();
        }
        private void clear() {
            // Remove only this plugin's known title suffixes. Other priorities and all ranks survive.
            user.data().clear(node -> node instanceof SuffixNode suffix && suffix.getPriority() == PRIORITY && suffixes.contains(suffix.getMetaValue()));
        }
        @Override public void select(String suffix) {
            clear();
            user.data().add(SuffixNode.builder(suffix, PRIORITY).build());
        }
        private void save() {
            permissions.getUserManager().saveUser(user).exceptionally(error -> {
                getLogger().severe("Could not persist title for UUID " + player.getUniqueId() + ": " + error.getMessage());
                return null;
            });
        }
    }
}
