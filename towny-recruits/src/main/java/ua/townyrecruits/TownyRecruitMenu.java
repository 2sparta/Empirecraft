package ua.townyrecruits;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.permissions.*;
import org.bukkit.plugin.Plugin;

/** Bukkit is linked only when the Forge server bridge starts; no second plugin is installed. */
final class TownyRecruitMenu implements Listener,AutoCloseable {
    final Recruitment recruitment;final Plugin towny;
    final NamespacedKey entry=new NamespacedKey("townyrecruits","recruitment");
    final class Menu implements InventoryHolder {
        final UUID owner;final Inventory inventory;
        Menu(Player player){owner=player.getUniqueId();inventory=Bukkit.createInventory(this,27,"Рекрути міста");}
        public Inventory getInventory(){return inventory;}
    }
    TownyRecruitMenu(Recruitment recruitment)throws Exception {
        this.recruitment=recruitment;towny=(Plugin)recruitment.bridge.towny.plugin;
        var manager=Bukkit.getPluginManager();
        for(String permission:List.of("trbridge.admin","townyrecruits.admin","tarmyadmin.admin"))if(manager.getPermission(permission)==null)manager.addPermission(new Permission(permission,PermissionDefault.OP));
        manager.registerEvents(this,towny);
        @SuppressWarnings("unchecked") Class<? extends Event> event=(Class<? extends Event>)Class.forName("com.palmergames.bukkit.towny.event.statusscreen.TownStatusScreenEvent",true,towny.getClass().getClassLoader());
        manager.registerEvent(event,this,EventPriority.MONITOR,(listener,e)->{
            try{
                var b=recruitment.bridge;b.refreshNow();Object town=e.getClass().getMethod("getTown").invoke(e);UUID city=b.towny.id(town);
                Object n=b.towny.call(town,"getNationOrNull");UUID nation=n==null?null:b.towny.id(n);
                e.getClass().getMethod("addLine",String.class,String.class).invoke(e,"townyrecruits-development","§6Розвиток: §f"+RecruitmentConfig.number(recruitment.state.balance(city)));
                e.getClass().getMethod("addLine",String.class,String.class).invoke(e,"townyrecruits-army","§6Війська нації: §f"+(nation==null?"0/0":b.count(nation)+"/"+b.limit(nation)));
            }catch(Exception ex){TownyRecruits.LOG.error("Town status extension failed",ex);}
        },towny,true);
    }
    boolean hirePermission(Player player){String key=recruitment.config.values.getProperty("hire.permission");return player.isPermissionSet(key)?player.hasPermission(key):Boolean.parseBoolean(recruitment.config.values.getProperty("hire.residents-by-default"));}
    void message(Player player,String key){player.sendMessage(recruitment.config.message(key));}
    ItemStack item(Material material,String name,String action,String...lore){
        ItemStack item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lore));if(action!=null)meta.getPersistentDataContainer().set(entry,PersistentDataType.STRING,action);item.setItemMeta(meta);return item;
    }
    void open(Player player){try{
        var b=recruitment.bridge;b.refreshNow();UUID town=b.towny.city(player.getUniqueId()),nation=b.snapshot.residents().get(player.getUniqueId());
        if(town==null||nation==null){player.sendMessage("Ваше місто має входити до нації Towny.");return;}
        var menu=new Menu(player);var limits=recruitment.state.nations.get(nation);
        menu.inventory.setItem(4,item(Material.PAPER,"Розвиток: "+RecruitmentConfig.number(recruitment.state.balance(town)),null,"Війська нації: "+b.count(nation)+"/"+b.limit(nation),"Ваші військові: "+recruitment.ownerCount(nation,player.getUniqueId())+"/"+recruitment.perPlayer(nation,player.getUniqueId())));
        for(String type:List.of("recruit","shieldman")){
            boolean shield=type.equals("shieldman"),available=!shield||limits!=null&&limits.shieldman;
            menu.inventory.setItem(shield?15:11,item(available?(shield?Material.SHIELD:Material.IRON_SWORD):Material.BARRIER,shield?"Щитоносець":"Звичайний рекрут",type,
                "Золото: "+recruitment.config.gold(type)+" злитків","Розвиток: "+RecruitmentConfig.number(recruitment.config.cost(type)),available?"Доступний — натисніть для найму":"Цей тип військ ще не відкритий. Побудуйте необхідну військову споруду для отримання доступу"));
        }
        menu.inventory.setItem(22,item(Material.ARROW,"Повернутися до міста","back"));player.openInventory(menu.inventory);
    }catch(Exception ex){TownyRecruits.LOG.error("Cannot open recruitment menu",ex);message(player,"failure");}}
    @EventHandler(priority=EventPriority.HIGHEST) public void townMenu(InventoryOpenEvent event){
        var inventory=event.getInventory();Object holder=inventory.getHolder();if(holder==null||!holder.getClass().getName().equals("ua.empirecraft.feudal.TownMenu$View"))return;
        for(int slot:new int[]{50,52,46,45,48,49})if(slot<inventory.getSize()&&inventory.getItem(slot)==null){inventory.setItem(slot,item(Material.IRON_SWORD,"Рекрути","entry","Наймання військ за золото та розвиток"));return;}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        var top=event.getView().getTopInventory();Object holder=top.getHolder();
        if(holder instanceof Menu menu){
            event.setCancelled(true);if(!menu.owner.equals(player.getUniqueId())||event.getRawSlot()<0||event.getRawSlot()>=top.getSize()||event.getClick()!=ClickType.LEFT)return;
            String action=action(event.getCurrentItem());if(action==null)return;
            if(action.equals("back")){player.closeInventory();try{Object feudal=recruitment.bridge.towny.feudal();if(feudal!=null)feudal.getClass().getMethod("townMenuOpen",Player.class).invoke(feudal,player);}catch(Exception ex){TownyRecruits.LOG.warn("Town menu unavailable",ex);}return;}
            // Close the stale view before executing. A repeated packet cannot buy a second unit.
            player.closeInventory();recruitment.purchase(player,action);return;
        }
        if(holder!=null&&holder.getClass().getName().equals("ua.empirecraft.feudal.TownMenu$View")&&event.getRawSlot()>=0&&event.getRawSlot()<top.getSize()&&"entry".equals(action(event.getCurrentItem()))){event.setCancelled(true);open(player);}
    }
    String action(ItemStack item){return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(entry,PersistentDataType.STRING);}
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    @EventHandler public void login(PlayerJoinEvent event){Bukkit.getScheduler().runTask(towny,()->{var nativePlayer=recruitment.bridge.server.m_6846_().m_11259_(event.getPlayer().getUniqueId());if(nativePlayer!=null)recruitment.recover(nativePlayer);});}
    public void close(){HandlerList.unregisterAll(this);for(Player player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu)player.closeInventory();}
}
