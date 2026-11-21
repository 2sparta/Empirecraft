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
        final UUID owner;final Inventory inventory;final String kind;final int page;final UUID target;boolean used;
        Menu(Player player){this(player,"hire",0,null,"Рекрути міста");}
        Menu(Player player,String kind,int page,UUID target,String title){owner=player.getUniqueId();this.kind=kind;this.page=page;this.target=target;inventory=Bukkit.createInventory(this,27,title);}
        public Inventory getInventory(){return inventory;}
    }
    TownyRecruitMenu(Recruitment recruitment)throws Exception {
        this.recruitment=recruitment;towny=(Plugin)recruitment.bridge.towny.plugin;
        var manager=Bukkit.getPluginManager();
        for(String permission:List.of("trbridge.admin","townyrecruits.admin","tarmyadmin.admin"))if(manager.getPermission(permission)==null)manager.addPermission(new Permission(permission,PermissionDefault.OP));
        manager.registerEvents(this,towny);
        TownyCommandCompat.register(this,towny);
        @SuppressWarnings("unchecked") Class<? extends Event> event=(Class<? extends Event>)Class.forName("com.palmergames.bukkit.towny.event.statusscreen.TownStatusScreenEvent",true,towny.getClass().getClassLoader());
        manager.registerEvent(event,this,EventPriority.MONITOR,(listener,e)->{
            try{
                var b=recruitment.bridge;b.refreshNow();Object town=e.getClass().getMethod("getTown").invoke(e);UUID city=b.towny.id(town);
                Object n=b.towny.call(town,"getNationOrNull");UUID nation=n==null?null:b.towny.id(n);
                e.getClass().getMethod("addLine",String.class,String.class).invoke(e,"townyrecruits-development","§6Людський ресурс: §f"+recruitment.state.people(city));
                e.getClass().getMethod("addLine",String.class,String.class).invoke(e,"townyrecruits-army","§6Війська нації: §f"+(nation==null?"0/0":b.count(nation)+"/"+b.limit(nation)));
            }catch(Exception ex){TownyRecruits.LOG.error("Town status extension failed",ex);}
        },towny,true);
    }
    boolean hirePermission(Player player){String key=recruitment.config.values.getProperty("hire.permission");return player.isPermissionSet(key)?player.hasPermission(key):Boolean.parseBoolean(recruitment.config.values.getProperty("hire.residents-by-default"));}
    void message(Player player,String key){player.sendMessage(recruitment.config.message(key));}
    ItemStack item(Material material,String name,String action,String...lore){
        ItemStack item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lore));if(action!=null)meta.getPersistentDataContainer().set(entry,PersistentDataType.STRING,action);item.setItemMeta(meta);return item;
    }
    static String cap(int value){return value==Integer.MAX_VALUE?"∞":Integer.toString(value);}
    void render(Player player,Menu menu)throws Exception {
        var b=recruitment.bridge;b.refreshNow();UUID town=b.towny.city(player.getUniqueId()),nation=b.snapshot.residents().get(player.getUniqueId());
        if(town==null||nation==null)throw new IllegalArgumentException("Ваше місто має входити до нації Towny.");
        menu.inventory.clear();
        menu.inventory.setItem(4,item(Material.PAPER,"Людський ресурс: "+recruitment.state.people(town),null,
            "Війська нації: "+b.count(nation)+"/"+cap(b.limit(nation)),
            "Ваші військові: "+recruitment.ownerCount(nation,player.getUniqueId())+"/"+cap(recruitment.perPlayer(nation,player.getUniqueId()))));
        for(String type:List.of("recruit","shieldman","militia")){
            boolean shield=type.equals("shieldman");String reason=recruitment.refusal(player,type);
            menu.inventory.setItem(type.equals("militia")?13:(shield?15:11),item(reason==null?(shield?Material.SHIELD:Material.IRON_SWORD):Material.BARRIER,
                (reason==null?"§a":"§c")+(type.equals("militia")?"Ополчення · 120 хв":(shield?"Щитоносець":"Звичайний рекрут")),reason==null?type:null,
                "Золото: "+recruitment.config.gold(type)+" злитків",
                "Людський ресурс: 1",
                reason==null?"Доступний — натисніть для найму":"Недоступний: "+reason,
                "Оплата одноразова. Після покупки буде показано списання."));
        }
        menu.inventory.setItem(18,item(Material.BOOK,"Мої рекрути → Розпустити","troops:0",
            "Список завантажених власних військових.",
            "Для розпуску потрібне окреме підтвердження."));
        menu.inventory.setItem(22,item(Material.ARROW,"Повернутися до міста","back"));
    }
    void open(Player player){try{
        Menu menu=new Menu(player);render(player,menu);player.openInventory(menu.inventory);
    }catch(Exception ex){player.sendMessage(Bridge.errorText(ex));}}
    void troops(Player player,int requestedPage)throws Exception {
        var b=recruitment.bridge;b.refreshNow();
        UUID nation=b.snapshot.residents().get(player.getUniqueId());
        var units=b.loaded.stream().filter(r->r.getIsOwned()&&r.m_6084_()&&player.getUniqueId().equals(r.getOwnerUUID()))
            .filter(r->{var u=b.data.units.get(r.m_20148_());return u!=null&&!u.retired()&&player.getUniqueId().equals(u.owner())&&Objects.equals(nation,u.nation());})
            .sorted(Comparator.comparing(r->r.m_20148_().toString())).toList();
        int last=Math.max(0,(units.size()-1)/9),page=Math.min(Math.max(0,requestedPage),last);
        Menu menu=new Menu(player,"troops",page,null,"Мої рекрути — "+(page+1)+"/"+(last+1));
        menu.inventory.setItem(4,item(Material.PAPER,"Завантажених: "+units.size(),null,
            "Вивантажені живі рекрути теж займають ліміт.",
            "Щоб розпустити такого рекрута, підійдіть до нього."));
        for(int i=page*9;i<Math.min(units.size(),page*9+9);i++){
            var r=units.get(i);String label=r.m_6095_()==com.talhanation.recruits.init.ModEntityTypes.RECRUIT_SHIELDMAN.get()?"Щитоносець":"Рекрут";
            // Entity display names are informational; the action stores the canonical UUID separately.
            menu.inventory.setItem(9+i-page*9,item(Material.PAPER,label+" — "+r.m_20148_().toString().substring(0,8),"dismiss:"+r.m_20148_(),
                "Координати: "+(int)Math.floor(r.m_20185_())+", "+(int)Math.floor(r.m_20186_())+", "+(int)Math.floor(r.m_20189_()),
                "Натисніть, щоб переглянути підтвердження розпуску."));
        }
        if(page>0)menu.inventory.setItem(18,item(Material.ARROW,"Попередня сторінка","troops:"+(page-1)));
        if(page<last)menu.inventory.setItem(26,item(Material.ARROW,"Наступна сторінка","troops:"+(page+1)));
        menu.inventory.setItem(22,item(Material.ARROW,"До найму","hire"));player.openInventory(menu.inventory);
    }
    void confirm(Player player,UUID unit){
        Menu menu=new Menu(player,"confirm",0,unit,"Розпустити рекрута?");
        menu.inventory.setItem(4,item(Material.PAPER,"Рекрут "+unit.toString().substring(0,8),null,
            "Він стане нейтральним і звільнить місце в ліміті.",
            "Золото та людський ресурс за найм не повертаються.",
            "Перед розпуском заберіть потрібне спорядження."));
        menu.inventory.setItem(11,item(Material.LIME_WOOL,"Підтвердити розпуск","confirm-dismiss"));
        menu.inventory.setItem(15,item(Material.RED_WOOL,"Скасувати","troops:0"));player.openInventory(menu.inventory);
    }
    void tick(){
        for(Player player:Bukkit.getOnlinePlayers())try{
            var top=player.getOpenInventory().getTopInventory();
            if(top.getHolder() instanceof Menu menu&&menu.kind.equals("hire")&&!menu.used)render(player,menu);
            else if(RecruitProvisionView.isProvision(top))RecruitProvisionView.refresh(recruitment,top);
        }catch(Exception ex){if(recruitment.bridge.ticks%1200==0)TownyRecruits.LOG.warn("Recruit menu refresh unavailable: {}",Bridge.errorText(ex));}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void townMenu(InventoryOpenEvent event){
        var inventory=event.getInventory();Object holder=inventory.getHolder();if(holder==null||!holder.getClass().getName().equals("ua.empirecraft.feudal.TownMenu$View"))return;
        for(int slot:new int[]{50,52,46,45,48,49})if(slot<inventory.getSize()&&inventory.getItem(slot)==null){inventory.setItem(slot,item(Material.IRON_SWORD,"Рекрути","entry","Наймання військ за золото та людський ресурс"));return;}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player player))return;
        var top=event.getView().getTopInventory();Object holder=top.getHolder();
        if(holder instanceof Menu menu){
            event.setCancelled(true);
            if(menu.used||!menu.owner.equals(player.getUniqueId())||event.getRawSlot()<0||event.getRawSlot()>=top.getSize()||event.getClick()!=ClickType.LEFT)return;
            String action=action(event.getCurrentItem());if(action==null)return;
            menu.used=true;
            Bukkit.getScheduler().runTask(towny,()->{
                if(!player.isOnline())return;
                // A queued action never acts on a different screen the player has opened.
                if(player.getOpenInventory().getTopInventory().getHolder()!=menu)return;
                player.closeInventory();
                try{
                    if(action.equals("back")){Object feudal=recruitment.bridge.towny.feudal();if(feudal!=null)feudal.getClass().getMethod("townMenuOpen",Player.class).invoke(feudal,player);}
                    else if(action.equals("hire"))open(player);
                    else if(action.startsWith("troops:"))troops(player,Integer.parseInt(action.substring(7)));
                    else if(action.startsWith("dismiss:")&&menu.kind.equals("troops"))confirm(player,UUID.fromString(action.substring(8)));
                    else if(action.equals("confirm-dismiss")&&menu.kind.equals("confirm")&&menu.target!=null){recruitment.dismiss(player,menu.target);troops(player,0);}
                    else if(menu.kind.equals("hire")&&Set.of("recruit","shieldman","militia").contains(action))recruitment.purchase(player,action);
                }catch(Exception ex){player.sendMessage(Bridge.errorText(ex));}
            });
            return;
        }
        if(holder!=null&&holder.getClass().getName().equals("ua.empirecraft.feudal.TownMenu$View")&&event.getRawSlot()>=0&&event.getRawSlot()<top.getSize()&&"entry".equals(action(event.getCurrentItem()))){event.setCancelled(true);Bukkit.getScheduler().runTask(towny,()->{if(player.isOnline())open(player);});}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void provisionOpen(InventoryOpenEvent event){
        if(RecruitProvisionView.isProvision(event.getInventory()))try{RecruitProvisionView.decorate(recruitment,event.getInventory());}catch(Exception ex){TownyRecruits.LOG.debug("Provision display pending",ex);}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void provisionClick(InventoryClickEvent event){
        var top=event.getView().getTopInventory();
        if(!RecruitProvisionView.isProvision(top)||!(event.getWhoClicked() instanceof Player player))return;
        Bukkit.getScheduler().runTask(towny,()->{
            if(player.isOnline()&&player.getOpenInventory().getTopInventory()==top)try{RecruitProvisionView.refresh(recruitment,top);}catch(Exception ex){TownyRecruits.LOG.debug("Provision refresh pending",ex);}
        });
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void command(org.bukkit.event.player.PlayerCommandPreprocessEvent event){TownyCommandCompat.route(event);}
    String action(ItemStack item){return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(entry,PersistentDataType.STRING);}
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    @EventHandler public void login(PlayerJoinEvent event){Bukkit.getScheduler().runTask(towny,()->{var nativePlayer=recruitment.bridge.server.m_6846_().m_11259_(event.getPlayer().getUniqueId());if(nativePlayer!=null)recruitment.recover(nativePlayer);});}
    public void close(){HandlerList.unregisterAll(this);for(Player player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu)player.closeInventory();}
}
