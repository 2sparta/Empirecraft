package ua.empirecraft.feudal;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachment;
import com.palmergames.bukkit.towny.event.SpawnEvent;
import java.util.*;

/** Towny's cancellable spawn event covers command aliases, outposts, menus and native API callers. */
public final class TownTravelGuard implements Listener {
    public static final String MESSAGE="Телепортація до міста вимкнена";
    final FeudalPlugin plugin;final Map<UUID,PermissionAttachment> attachments=new HashMap<>();
    TownTravelGuard(FeudalPlugin plugin){this.plugin=plugin;}
    void install(Player p){
        if(attachments.containsKey(p.getUniqueId()))return;
        var attachment=p.addAttachment(plugin);for(String permission:List.of("towny.town.spawn.*","towny.town.spawn.town","towny.town.spawn.nation","towny.town.spawn.ally","towny.town.spawn.public","towny.town.spawn.outpost","towny.nation.spawn.*","towny.nation.spawn.nation","towny.nation.spawn.ally","towny.nation.spawn.public"))attachment.setPermission(permission,false);
        attachments.put(p.getUniqueId(),attachment);
    }
    void register(){Bukkit.getPluginManager().registerEvents(this,plugin);for(Player p:Bukkit.getOnlinePlayers())install(p);}
    @EventHandler public void join(PlayerJoinEvent event){install(event.getPlayer());}
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent event){var entry=attachments.remove(event.getPlayer().getUniqueId());if(entry!=null)event.getPlayer().removeAttachment(entry);}
    public static boolean route(String root,String[] args){
        root=root.toLowerCase(Locale.ROOT);int namespace=root.indexOf(':');if(namespace>=0)root=root.substring(namespace+1);
        if(root.startsWith("/"))root=root.substring(1);
        if(!Set.of("t","town","n","nation","місто","город","нація","нация").contains(root))return false;
        if(args.length==0)return false;String action=args[0].toLowerCase(Locale.ROOT);if(Set.of("spawn","спавн").contains(action))return true;
        if(!Set.of("outpost","outposts","форпост","форпости").contains(action))return false;
        return args.length<2||!Set.of("list","add","delete","remove","rename","set","список").contains(args[1].toLowerCase(Locale.ROOT));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void command(PlayerCommandPreprocessEvent event){String[] parts=event.getMessage().trim().split("\\s+");if(route(parts[0],Arrays.copyOfRange(parts,1,parts.length))){event.setCancelled(true);event.getPlayer().sendMessage(MESSAGE);}}
    @EventHandler(priority=EventPriority.HIGHEST) public void nation(com.palmergames.bukkit.towny.event.NationSpawnEvent event){spawn(event);}
    @EventHandler(priority=EventPriority.HIGHEST) public void spawn(SpawnEvent event){event.setCancelled(true);event.setCancelMessage(MESSAGE);event.getPlayer().sendMessage(MESSAGE);}
}
