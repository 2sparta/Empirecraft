package ua.empirecraft.chat;

import com.palmergames.bukkit.towny.TownyAPI;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.SuffixNode;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import ua.empirecraft.feudal.FeudalPlugin;
import ua.empirecraft.feudal.LocalText;

public final class ChatPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Channel> selected = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> cosmetics = new HashMap<>();
    private FeudalPlugin feudal;
    private LuckPerms permissions;

    @Override public void onEnable() {
        feudal = (FeudalPlugin) Bukkit.getPluginManager().getPlugin("EmpirecraftFeudal");
        permissions = Bukkit.getServicesManager().load(LuckPerms.class);
        if (permissions == null) throw new IllegalStateException("LuckPerms API is unavailable");
        JavaPlugin titles = (JavaPlugin) Bukkit.getPluginManager().getPlugin("EmpirecraftTitles");
        try (var stream = Objects.requireNonNull(titles.getResource("catalog.yml"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var catalog = YamlConfiguration.loadConfiguration(reader);
            var icons = YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(getResource("cosmetics.yml")), StandardCharsets.UTF_8));
            int fallback=0;
            for (String key : catalog.getKeys(false)) {
                String glyph = icons.getString(key + ".glyph");
                if (glyph == null || glyph.isBlank()) fallback++;
                String suffix=catalog.getString(key + ".suffix", "");
                if(!suffix.isBlank())cosmetics.put(suffix,CosmeticDisplay.text(glyph,catalog.getString(key+".label"),key));
            }
            if(fallback>0)getLogger().warning("Bitmap artwork is unavailable for "+fallback+" cosmetic titles; using their text labels.");
        } catch (Exception e) { throw new IllegalStateException("Cannot load the installed cosmetic title catalog", e); }
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("ecchat").setExecutor(this);
        getCommand("ecchat").setTabCompleter(this);
        getLogger().info("Five chat channels enabled; local radius 50 blocks, Towny membership checked for every message.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void chat(AsyncPlayerChatEvent event) {
        // Capture the original recipients so mute/ignore/moderation plugins can narrow delivery.
        UUID sender = event.getPlayer().getUniqueId();
        Channel channel = selected.getOrDefault(sender, Channel.GLOBAL);
        String text = event.getMessage();
        Set<UUID> allowed = new HashSet<>();
        for (Player p : event.getRecipients()) allowed.add(p.getUniqueId());
        event.setCancelled(true);
        // Never access Towny, the database, locations or LuckPerms contexts on the async chat thread.
        Runnable deliver = () -> {
            Player player = Bukkit.getPlayer(sender);
            if (player != null && player.isOnline()) deliver(player, channel, text, allowed);
        };
        if (event.isAsynchronous()) Bukkit.getScheduler().runTask(this, deliver); else deliver.run();
    }

    @EventHandler public void quit(PlayerQuitEvent event) { selected.remove(event.getPlayer().getUniqueId()); }

    @Override public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(LocalText.tr("Канали чату доступні гравцям.")); return true; }
        try {
            if (args.length == 0) { system(player, tr(player,"Канали: /ecchat global|local|town|kingdom [повідомлення]. Локальний радіус: 50 блоків.")); return true; }
            Channel channel = Channel.fromId(args[0]);
            validate(channel, member(player));
            if (args.length == 1) {
                selected.put(player.getUniqueId(), channel);
                system(player, tr(player,"Вибрано канал: ") + channel.id + ".");
                return true;
            }
            String text = ChatRules.body(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            Set<Player> recipients = new HashSet<>(Bukkit.getOnlinePlayers());
            var event = new AsyncPlayerChatEvent(false, player, text, recipients);
            // Use the same Bukkit moderation event as ordinary chat. The handler below gets the explicit channel.
            Channel previous = selected.put(player.getUniqueId(), channel);
            try { Bukkit.getPluginManager().callEvent(event); }
            finally { if (previous == null) selected.remove(player.getUniqueId()); else selected.put(player.getUniqueId(), previous); }
        } catch (IllegalArgumentException e) { system(player, e.getMessage()); }
        return true;
    }

    private void validate(Channel channel, ChatRules.Member member) {
        if (channel == Channel.SYSTEM) throw new IllegalArgumentException("Системні повідомлення доступні лише для читання. Обери іншу вкладку.");
        if (channel == Channel.TOWN && member.town() == null) throw new IllegalArgumentException("Ти не є членом міста.");
        if (channel == Channel.KINGDOM && member.kingdom() == null) throw new IllegalArgumentException("Ти не є членом королівства.");
    }

    private ChatRules.Member member(Player player) {
        var resident = TownyAPI.getInstance().getResident(player.getUniqueId());
        var town = resident == null ? null : resident.getTownOrNull();
        var kingdom = resident == null ? null : resident.getNationOrNull();
        Location l = player.getLocation();
        return new ChatRules.Member(player.getUniqueId(), town == null ? null : town.getUUID(),
            kingdom == null ? null : kingdom.getUUID(), player.getWorld().getUID(), l.getX(), l.getY(), l.getZ());
    }

    private void deliver(Player sender, Channel channel, String raw, Set<UUID> allowed) {
        try {
            String body = ChatRules.body(raw);
            var from = member(sender);
            validate(channel, from);
            TextComponent message = formatted(sender, channel, body);
            for (Player receiver : Bukkit.getOnlinePlayers()) {
                if (allowed.contains(receiver.getUniqueId()) && ChatRules.receives(channel, from, member(receiver))) receiver.spigot().sendMessage(message);
            }
            getLogger().info("[" + channel.id + "] " + sender.getName() + ": " + body);
        } catch (IllegalArgumentException e) { system(sender, e.getMessage()); }
        catch (Exception e) { getLogger().log(Level.SEVERE, "Chat delivery failed", e); system(sender, "Не вдалося надіслати повідомлення. Спробуй ще раз."); }
    }

    TextComponent formatted(Player player, Channel channel, String body) throws Exception {
        var resident = TownyAPI.getInstance().getResident(player.getUniqueId());
        var town = resident == null ? null : resident.getTownOrNull();
        var kingdom = resident == null ? null : resident.getNationOrNull();
        String city = town == null ? "—" : town.getName();
        String nation = kingdom == null ? "—" : kingdom.getName();
        String title = feudal.chatTitle(player.getUniqueId());
        String cosmetic = cosmetic(player);
        return ChatFormat.message(channel, city, nation, player.getName(), title, cosmetic, body);
    }

    private String cosmetic(Player player) {
        var user = permissions.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        var options = permissions.getContextManager().getQueryOptions(player);
        return user.resolveInheritedNodes(options).stream()
            .filter(n -> n instanceof SuffixNode s && s.getValue() && s.getPriority() == 100 && cosmetics.containsKey(s.getMetaValue()))
            .map(n -> ((SuffixNode)n).getMetaValue()).sorted().map(cosmetics::get).findFirst().orElse("");
    }
    private static TextComponent part(String text, ChatColor color) {
        return ChatFormat.part(text, color);
    }
    private static String tr(Player player,String source){try(var languageScope=LocalText.use(player)){return LocalText.tr(source);}}
    private static void system(Player player, String text) {
        text=tr(player,text);
        TextComponent message = part(text, ChatColor.YELLOW); message.setInsertion(Channel.SYSTEM.marker()); player.spigot().sendMessage(message);
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("global", "local", "town", "kingdom").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}
