package ua.empirecraft.feudal;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import net.md_5.bungee.api.chat.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** The menu displays copies; originals remain in storage until both players confirm. */
public final class TradeController implements CommandExecutor,TabCompleter,Listener {
    private final FeudalPlugin plugin;private final FeudalService service;private final TradeLedger ledger;
    private final Map<UUID,TradeSession> sessions=new HashMap<>();private final Map<UUID,Request> requests=new HashMap<>();
    private final Map<UUID,Long> cooldown=new HashMap<>();private final Map<UUID,UUID> chatEdits=new ConcurrentHashMap<>();
    private final double distance;private final long requestMillis,idleMillis,confirmMillis;
    private static final int[] OWN={0,1,2,3,9,10,11,12,18,19,20,21,27,28,29,30},OTHER={5,6,7,8,14,15,16,17,23,24,25,26,32,33,34,35};
    private record Request(UUID from,UUID to,long expires) {}
    private static final class View implements InventoryHolder {final UUID session,owner;final List<UUID> choices;Inventory inventory;View(UUID session,UUID owner,List<UUID> choices){this.session=session;this.owner=owner;this.choices=choices;}public Inventory getInventory(){return inventory;}}
    public TradeController(FeudalPlugin plugin,FeudalService service) throws Exception {
        this.plugin=plugin;this.service=service;ledger=new TradeLedger(service);distance=Math.max(1,Math.min(16,plugin.getConfig().getDouble("trade.distance-blocks",5)));
        requestMillis=Math.max(10,plugin.getConfig().getLong("trade.request-seconds",30))*1000;idleMillis=Math.max(30,plugin.getConfig().getLong("trade.idle-seconds",120))*1000;confirmMillis=Math.max(3,plugin.getConfig().getLong("trade.confirm-seconds",3))*1000;
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,5,5);for(var op:ledger.pending())plugin.getLogger().warning("Незавершений обмін "+string(op,"id")+"; /trade admin pending");
    }
    private long now(){return service.now();}private Player player(UUID id){return Bukkit.getPlayer(id);}
    private void msg(CommandSender p,String text){p.sendMessage("§6[Обмін] §f"+text);}
    private Actor actor(CommandSender p){return new Actor(p instanceof Player x?x.getUniqueId().toString():"консоль",p.hasPermission("empirecraft.feudal.admin"));}
    static boolean near(Location a,Location b,double d){return a!=null&&b!=null&&a.getWorld()!=null&&b.getWorld()!=null&&TradeSession.inRange(a.getWorld().getUID(),a.getX(),a.getY(),a.getZ(),b.getWorld().getUID(),b.getX(),b.getY(),b.getZ(),d);}
    private boolean eligible(Player p){return p!=null&&p.isOnline()&&!p.isDead()&&p.hasPermission("empirecraft.trade.use")&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
    private boolean near(Player a,Player b){return eligible(a)&&eligible(b)&&a.canSee(b)&&b.canSee(a)&&near(a.getLocation(),b.getLocation(),distance);}
    private boolean requested(UUID id){return requests.values().stream().anyMatch(r->r.from.equals(id)||r.to.equals(id));}
    private TradeSession session(Player p){var s=sessions.get(p.getUniqueId());require(s!=null,"Спочатку почніть обмін: /trade Ім’яГравця.");return s;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){try{
        if(args.length>0&&args[0].equalsIgnoreCase("admin")){admin(sender,args);return true;}
        require(sender instanceof Player,"Команда доступна у грі.");Player p=(Player)sender;UUID id=p.getUniqueId();require(p.hasPermission("empirecraft.trade.use"),"Немає права на обмін.");
        if(args.length==0){if(sessions.containsKey(id)){var s=sessions.get(id);chatEdits.remove(id);s.editing.remove(id);open(p,s);render(s);}else if(requests.containsKey(id))requestMenu(p,requests.get(id));else choose(p);return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "accept","прийняти" -> {require(args.length<=2,"/trade accept [гравець]");var r=requests.get(id);require(r!=null&&(args.length==1||player(r.from)!=null&&player(r.from).getName().equalsIgnoreCase(args[1])),"Такого запиту немає.");accept(p,r);}
            case "deny","відхилити","cancel","скасувати" -> {var s=sessions.get(id);if(s!=null)cancel(s,"Обмін скасовано учасником.");else clearRequests(id,"Запит на обмін скасовано.");}
            case "coins","монети" -> {require(args.length==3,"/trade coins <КОД> <сума>");var s=session(p);setCoins(p,s,args[1].toUpperCase(Locale.ROOT),money(args[2]));chatEdits.remove(id);s.editing.remove(id);open(p,s);render(s);}
            default -> {require(args.length==1,"/trade [гравець|accept|cancel|coins]");var target=Bukkit.getPlayerExact(args[0]);require(target!=null,"Гравець має бути в мережі.");request(p,target);}
        }
    }catch(IllegalArgumentException ex){msg(sender,"§c"+ex.getMessage());}catch(Exception ex){plugin.failure("Обмін",ex);msg(sender,"§cНе вдалося виконати обмін. Передачу не повторено; повідомте адміністратора.");}return true;}
    private void choose(Player p) throws Exception {
        require(eligible(p),"Обмін доступний живим гравцям у виживанні або пригоді.");require(!ledger.pending(p.getUniqueId().toString()),"Попередній обмін потребує перевірки адміністратора.");
        var list=Bukkit.getOnlinePlayers().stream().filter(x->!x.equals(p)&&near(p,x)&&!sessions.containsKey(x.getUniqueId())&&!requested(x.getUniqueId())).sorted(Comparator.comparing(Player::getName)).limit(45).toList();
        var v=new View(null,p.getUniqueId(),list.stream().map(Player::getUniqueId).toList());v.inventory=Bukkit.createInventory(v,54,"Обмін: гравці поруч");
        for(int i=0;i<list.size();i++)v.inventory.setItem(i,icon(Material.PLAYER_HEAD,"§e"+list.get(i).getName(),"§7Надіслати запит; до "+distance+" блоків"));v.inventory.setItem(49,icon(Material.BOOK,"§eОберіть гравця поруч",list.isEmpty()?"§7Поруч немає доступних гравців.":"§7Обидва учасники мають погодитися."));p.openInventory(v.inventory);
    }
    private void request(Player a,Player b) throws Exception {
        UUID x=a.getUniqueId(),y=b.getUniqueId();require(!x.equals(y),"Оберіть іншого гравця.");require(near(a,b),"Учасники мають бути в одному світі, не далі "+distance+" блоків.");var reverse=requests.get(x);if(reverse!=null&&reverse.from.equals(y)){accept(a,reverse);return;}
        require(!sessions.containsKey(x)&&!sessions.containsKey(y)&&!requested(x)&&!requested(y),"Учасник уже має обмін або запит.");require(!ledger.pending(x.toString())&&!ledger.pending(y.toString()),"Попередній обмін учасника потребує перевірки.");require(now()-cooldown.getOrDefault(x,0L)>=5000,"Зачекайте 5 секунд перед наступним запитом.");
        cooldown.put(x,now());requests.put(y,new Request(x,y,now()+requestMillis));if(a.getOpenInventory().getTopInventory().getHolder() instanceof View)a.closeInventory();msg(a,"Запит надіслано "+b.getName()+" на "+requestMillis/1000+" с.");msg(b,a.getName()+" пропонує обмін. /trade відкриє запит.");
        var yes=new TextComponent("§a[Прийняти] ");yes.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/trade accept "+a.getName()));var no=new TextComponent("§c[Відхилити]");no.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/trade deny"));b.spigot().sendMessage(yes,no);
    }
    private void requestMenu(Player p,Request r){require(now()<r.expires,"Строк запиту минув.");var from=player(r.from);require(from!=null,"Гравець вийшов.");var v=new View(null,p.getUniqueId(),List.of(r.from));v.inventory=Bukkit.createInventory(v,27,"Запит: "+from.getName());v.inventory.setItem(11,icon(Material.LIME_CONCRETE,"§aПрийняти",from.getName()+" хоче торгувати."));v.inventory.setItem(15,icon(Material.RED_CONCRETE,"§cВідхилити"));p.openInventory(v.inventory);}
    private void accept(Player b,Request r) throws Exception {
        var a=player(r.from);require(requests.get(r.to)==r&&now()<r.expires,"Запит уже не діє.");require(b.getUniqueId().equals(r.to)&&near(a,b),"Учасники мають залишатися поруч.");require(!sessions.containsKey(r.from)&&!sessions.containsKey(r.to)&&!ledger.pending(r.from.toString())&&!ledger.pending(r.to.toString()),"Учасник уже зайнятий.");require(TradeInventory.empty(a.getItemOnCursor())&&TradeInventory.empty(b.getItemOnCursor()),"Приберіть предмет із курсора.");
        requests.remove(r.to);var s=new TradeSession(r.from,r.to,now());sessions.put(s.a,s);sessions.put(s.b,s);open(a,s);open(b,s);render(s);
    }
    private void open(Player p,TradeSession s){if(p.getOpenInventory().getTopInventory().getHolder() instanceof View v&&s.id.equals(v.session))return;var v=new View(s.id,p.getUniqueId(),List.of());v.inventory=Bukkit.createInventory(v,54,"Обмін з "+Objects.requireNonNull(player(s.other(p.getUniqueId()))).getName());p.openInventory(v.inventory);}
    private static ItemStack icon(Material m,String name,String... lore){var item=new ItemStack(m);var meta=item.getItemMeta();meta.setDisplayName(name);meta.setLore(List.of(lore));item.setItemMeta(meta);return item;}
    private static String summary(Map<String,Long> coins){return coins.isEmpty()?"немає":String.join(", ",coins.entrySet().stream().map(e->format(e.getValue())+" "+e.getKey()).toList());}
    private ItemStack display(ItemStack source,boolean own){var item=source.clone();var meta=item.getItemMeta();var lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<String>();lore.add(own?"§eНатисніть, щоб прибрати з пропозиції.":"§7Предмет іншого учасника.");meta.setLore(lore);item.setItemMeta(meta);return item;}
    private void render(TradeSession s){for(UUID id:List.of(s.a,s.b)){
        var p=player(id);if(p==null||!(p.getOpenInventory().getTopInventory().getHolder() instanceof View v)||!s.id.equals(v.session))continue;var inv=v.inventory;var own=s.offer(id);var other=s.offer(s.other(id));inv.clear();for(int slot:new int[]{4,13,22,31,40})inv.setItem(slot,icon(Material.GRAY_STAINED_GLASS_PANE," "));
        int i=0;for(var item:own.items.values())inv.setItem(OWN[i++],display(item,true));i=0;for(var item:other.items.values())inv.setItem(OTHER[i++],display(item,false));String balance;try{balance=format(service.balance("p:"+id,own.currency));}catch(Exception ex){balance="?";}
        inv.setItem(36,icon(Material.GOLD_NUGGET,"§eМонети: "+own.currency,"§fПропозиція: "+summary(own.coins),"§7Баланс "+own.currency+": "+balance,"§eЛКМ: ввести суму; ПКМ: прибрати валюту"));inv.setItem(37,icon(Material.ARROW,"§eПопередня валюта"));inv.setItem(38,icon(Material.ARROW,"§eНаступна валюта"));inv.setItem(39,icon(Material.BARRIER,"§cПрибрати всі монети"));inv.setItem(41,icon(Material.GOLD_INGOT,"§eМонети іншого учасника","§f"+summary(other.coins)));
        inv.setItem(45,icon(own.ready==s.revision?Material.LIME_CONCRETE:Material.YELLOW_CONCRETE,own.ready==s.revision?"§aВи підтвердили":"§eПідтвердити пропозицію","§7Зміна предметів або монет скидає обидві згоди.","§7Повторне натискання відкликає згоду."));inv.setItem(46,icon(Material.GOLD_NUGGET,"§e+1 "+own.currency,"§7ПКМ: −1"));inv.setItem(47,icon(Material.GOLD_NUGGET,"§e+10 "+own.currency,"§7ПКМ: −10"));inv.setItem(48,icon(Material.GOLD_NUGGET,"§e+100 "+own.currency,"§7ПКМ: −100"));
        long n=s.settleAt==0?0:Math.max(0,(s.settleAt-now()+999)/1000);inv.setItem(49,icon(Material.CLOCK,!s.editing.isEmpty()?"§eУчасник вводить суму":s.settleAt==0?"§eОчікування підтверджень":"§aОбмін через "+n+" с"));inv.setItem(50,icon(other.ready==s.revision?Material.LIME_DYE:Material.GRAY_DYE,other.ready==s.revision?"§aІнший учасник підтвердив":"§7Інший учасник ще не підтвердив"));
        inv.setItem(51,icon(Material.BOOK,"§eЯк торгувати","§7Натисніть стос у нижньому інвентарі.","§7Ліворуч — ваше; праворуч — іншого гравця.","§7До завершення предмети залишаються у власників.","§7Закриття вікна скасовує обмін."));inv.setItem(52,icon(Material.GOLD_BLOCK,"§eУвесь баланс "+own.currency));inv.setItem(53,icon(Material.RED_CONCRETE,"§cСкасувати обмін"));
    }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false) public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(v.owner))return;if(e.getClick()!=ClickType.LEFT&&e.getClick()!=ClickType.RIGHT&&e.getClick()!=ClickType.SHIFT_LEFT)return;
        int raw=e.getRawSlot(),slot=e.getSlot();boolean bottom=e.getClickedInventory()==p.getInventory(),right=e.isRightClick();
        Bukkit.getScheduler().runTask(plugin,()->{if(p.getOpenInventory().getTopInventory()!=v.inventory)return;try{
            if(v.session==null){if(v.inventory.getSize()==27){if(raw==11){var r=requests.get(p.getUniqueId());require(r!=null,"Запит уже скасовано.");accept(p,r);}else if(raw==15){clearRequests(p.getUniqueId(),"Запит відхилено.");p.closeInventory();}}else if(raw>=0&&raw<v.choices.size()){var t=player(v.choices.get(raw));require(t!=null,"Гравець вийшов.");request(p,t);}return;}
            var s=sessions.get(p.getUniqueId());if(s==null||!s.id.equals(v.session))return;UUID id=p.getUniqueId();var o=s.offer(id);
            if(bottom&&slot>=0&&slot<36)s.toggle(id,slot,p.getInventory().getItem(slot),now());else if(raw>=0&&raw<36){for(int i=0;i<OWN.length;i++)if(raw==OWN[i]&&i<o.items.size()){s.toggle(id,new ArrayList<>(o.items.keySet()).get(i),null,now());break;}}
            else switch(raw){
                case 36 -> {if(right)s.coins(id,o.currency,0,now());else{s.editing.add(id);chatEdits.put(id,s.id);s.changed(now());p.closeInventory();msg(p,"Введіть суму "+o.currency+" у чаті. «назад» — повернутися; /trade cancel — скасувати.");}}
                case 37,38 -> {var codes=service.db.query("SELECT code FROM currencies ORDER BY code").stream().map(x->string(x,"code")).toList();o.currency=codes.get(Math.floorMod(codes.indexOf(o.currency)+(raw==37?-1:1),codes.size()));}
                case 39 -> {if(!o.coins.isEmpty()){o.coins.clear();s.changed(now());}}
                case 45 -> {require(!o.items.isEmpty()||!o.coins.isEmpty()||!s.offer(s.other(id)).items.isEmpty()||!s.offer(s.other(id)).coins.isEmpty(),"Обидві пропозиції порожні.");s.confirm(id,now(),confirmMillis);}
                case 46,47,48 -> {long n=raw==46?100:raw==47?1000:10000;setCoins(p,s,o.currency,Math.max(0,Math.addExact(o.coins.getOrDefault(o.currency,0L),right?-n:n)));}
                case 52 -> setCoins(p,s,o.currency,service.balance("p:"+id,o.currency));case 53 -> {cancel(s,"Обмін скасовано учасником.");return;}default -> {}
            }render(s);
        }catch(IllegalArgumentException ex){msg(p,"§c"+ex.getMessage());}catch(Exception ex){plugin.failure("Вікно обміну",ex);var s=sessions.get(p.getUniqueId());if(s!=null)cancel(s,"Помилка обміну. Предмети залишилися у власників.");}});
    }
    private void setCoins(Player p,TradeSession s,String code,long n) throws Exception {service.currency(code);require(n<=service.balance("p:"+p.getUniqueId(),code),"Недостатньо монет "+code+" у гаманці.");s.coins(p.getUniqueId(),code,n,now());s.offer(p.getUniqueId()).currency=code;}
    @EventHandler(priority=EventPriority.LOWEST) public void chat(AsyncPlayerChatEvent e){UUID id=e.getPlayer().getUniqueId(),expected=chatEdits.get(id);if(expected==null)return;e.setCancelled(true);String text=e.getMessage();Bukkit.getScheduler().runTask(plugin,()->{var s=sessions.get(id);if(s==null||!s.id.equals(expected)||!expected.equals(chatEdits.get(id)))return;try{require(text.length()<=32,"Надто довга сума.");if(!text.equalsIgnoreCase("назад"))setCoins(e.getPlayer(),s,s.offer(id).currency,money(text.trim()));chatEdits.remove(id);s.editing.remove(id);open(e.getPlayer(),s);render(s);}catch(IllegalArgumentException ex){msg(e.getPlayer(),"§c"+ex.getMessage());}catch(Exception ex){plugin.failure("Монети обміну",ex);cancel(s,"Не вдалося змінити суму.");}});}
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent e){if(e.getInventory().getHolder() instanceof View v&&v.session!=null){var s=sessions.get(e.getPlayer().getUniqueId());if(s!=null&&s.id.equals(v.session)&&!s.editing.contains(e.getPlayer().getUniqueId()))cancel(s,"Вікно закрито — обмін скасовано.");}}
    @EventHandler public void quit(PlayerQuitEvent e){stop(e.getPlayer(),"Учасник вийшов із гри.");cooldown.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){stop(e.getPlayer(),"Учасник телепортувався.");}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageEvent e){if(e.getFinalDamage()>0&&e.getEntity() instanceof Player p)stop(p,"Учасник отримав пошкодження.");}
    @EventHandler public void death(PlayerDeathEvent e){stop(e.getEntity(),"Учасник загинув.");}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e){var s=sessions.get(e.getPlayer().getUniqueId());if(s!=null){var other=player(s.other(e.getPlayer().getUniqueId()));if(other==null||!near(e.getTo(),other.getLocation(),distance))cancel(s,"Учасники відійшли надто далеко.");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drop(PlayerDropItemEvent e){if(sessions.containsKey(e.getPlayer().getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e){if(sessions.containsKey(e.getPlayer().getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(sessions.containsKey(e.getEntity().getUniqueId()))e.setCancelled(true);}
    private void stop(Player p,String reason){var s=sessions.get(p.getUniqueId());if(s!=null)cancel(s,reason);clearRequests(p.getUniqueId(),reason);}
    private void clearRequests(UUID id,String reason){for(var r:new ArrayList<>(requests.values()))if(r.from.equals(id)||r.to.equals(id)){requests.remove(r.to);for(UUID x:List.of(r.from,r.to)){var p=player(x);if(p!=null)msg(p,reason);}}}
    private void remove(TradeSession s){sessions.remove(s.a,s);sessions.remove(s.b,s);chatEdits.remove(s.a,s.id);chatEdits.remove(s.b,s.id);s.editing.clear();}
    private void closeViews(TradeSession s,String reason){for(UUID id:List.of(s.a,s.b)){var p=player(id);if(p==null)continue;if(p.getOpenInventory().getTopInventory().getHolder() instanceof View v&&s.id.equals(v.session))p.closeInventory();msg(p,reason);}}
    private void cancel(TradeSession s,String reason){remove(s);closeViews(s,reason);}
    private void tick(){long time=now();for(var r:new ArrayList<>(requests.values()))if(time>=r.expires||!near(player(r.from),player(r.to)))clearRequests(r.from,"Запит завершився або гравці відійшли.");for(var s:new HashSet<>(sessions.values())){
        var a=player(s.a);var b=player(s.b);if(!near(a,b)){cancel(s,"Учасники мають залишатися поруч.");continue;}if(time-s.touched>=idleMillis){cancel(s,"Час очікування обміну минув.");continue;}
        if(!TradeInventory.matches(a.getInventory().getStorageContents(),s.first.items)||!TradeInventory.matches(b.getInventory().getStorageContents(),s.second.items)){cancel(s,"Запропонований предмет змінився.");continue;}if(s.due(time)){complete(s);continue;}if(s.settleAt>0){int n=(int)((s.settleAt-time+999)/1000);if(n!=s.renderedCountdown){s.renderedCountdown=n;render(s);}}
    }}
    private void complete(TradeSession s){String op=null;var a=player(s.a);var b=player(s.b);try{
        require(near(a,b)&&s.due(now()),"Умови обміну більше не виконуються.");require(TradeInventory.empty(a.getItemOnCursor())&&TradeInventory.empty(b.getItemOnCursor()),"Приберіть предмет із курсора.");var beforeA=TradeInventory.copy(a.getInventory().getStorageContents());var beforeB=TradeInventory.copy(b.getInventory().getStorageContents());
        var afterA=TradeInventory.plan(beforeA,s.first.items,s.second.items.values(),a.getInventory().getMaxStackSize());var afterB=TradeInventory.plan(beforeB,s.second.items,s.first.items.values(),b.getInventory().getMaxStackSize());
        op=ledger.prepare(s.a.toString(),s.b.toString(),s.first.coins,s.second.coins,TradeInventory.encode(beforeA),TradeInventory.encode(afterA),TradeInventory.encode(beforeB),TradeInventory.encode(afterB));remove(s);a.getInventory().setStorageContents(afterA);b.getInventory().setStorageContents(afterB);require(TradeInventory.same(a.getInventory().getStorageContents(),afterA)&&TradeInventory.same(b.getInventory().getStorageContents(),afterB),"Сторонній плагін змінив інвентар.");a.saveData();b.saveData();ledger.finish(op,true,new Actor(s.a.toString(),false));closeViews(s,"§aОбмін завершено. № "+op);
    }catch(Exception ex){remove(s);closeViews(s,op==null?"§cОбмін скасовано: "+ex.getMessage():"§cОбмін № "+op+" потребує перевірки адміністратора. Не повторюйте видачу вручну.");if(op!=null||!(ex instanceof IllegalArgumentException))plugin.failure("Завершення обміну "+op,ex);}}
    private void admin(CommandSender sender,String[] args) throws Exception {
        require(sender.hasPermission("empirecraft.feudal.admin"),"Потрібне право адміністратора.");if(args.length==2&&args[1].equals("pending")){for(var r:ledger.pending())msg(sender,string(r,"id")+" · "+string(r,"a")+" ↔ "+string(r,"b"));return;}
        require(args.length==4&&args[1].equals("recover")&&Set.of("complete","rollback").contains(args[3]),"/trade admin pending | /trade admin recover <ID> <complete|rollback>");var op=ledger.operation(args[2]);require("PREPARED".equals(string(op,"status")),"Обмін уже завершено.");var a=player(UUID.fromString(string(op,"a")));var b=player(UUID.fromString(string(op,"b")));require(a!=null&&b!=null&&!a.isDead()&&!b.isDead(),"Обидва гравці мають бути в мережі й живими.");require(!sessions.containsKey(a.getUniqueId())&&!sessions.containsKey(b.getUniqueId()),"Спочатку закрийте активні обміни.");a.closeInventory();b.closeInventory();
        var ba=TradeInventory.decode(string(op,"before_a"));var bb=TradeInventory.decode(string(op,"before_b"));var aa=TradeInventory.decode(string(op,"after_a"));var ab=TradeInventory.decode(string(op,"after_b"));require(TradeInventory.empty(a.getItemOnCursor())&&TradeInventory.empty(b.getItemOnCursor()),"Приберіть предмети з курсорів.");
        require((TradeInventory.same(a.getInventory().getStorageContents(),ba)||TradeInventory.same(a.getInventory().getStorageContents(),aa))&&(TradeInventory.same(b.getInventory().getStorageContents(),bb)||TradeInventory.same(b.getInventory().getStorageContents(),ab)),"Інвентарі вже змінилися. Перевірте журнал і резервну копію вручну.");boolean done=args[3].equals("complete");a.getInventory().setStorageContents(done?aa:ba);b.getInventory().setStorageContents(done?ab:bb);a.saveData();b.saveData();ledger.finish(args[2],done,actor(sender));msg(sender,"Результат обміну збережено.");
    }
    public void shutdown(){for(var s:new HashSet<>(sessions.values()))cancel(s,"Сервер зупиняється — обмін скасовано.");requests.clear();chatEdits.clear();}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] a){if(!(sender instanceof Player p))return List.of();var values=new ArrayList<String>();if(a.length==1){values.addAll(List.of("accept","deny","cancel","coins"));for(var x:Bukkit.getOnlinePlayers())if(!x.equals(p)&&near(p,x))values.add(x.getName());}if(a.length==2&&a[0].equals("coins"))try{for(var row:service.db.query("SELECT code FROM currencies ORDER BY code"))values.add(string(row,"code"));}catch(Exception ignored){}String prefix=a[a.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(x->x.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();}
}
