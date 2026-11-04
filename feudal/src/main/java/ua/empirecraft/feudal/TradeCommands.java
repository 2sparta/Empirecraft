package ua.empirecraft.feudal;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Offers are ghost copies until both sides lock and confirm the exact same revision. */
public final class TradeCommands implements CommandExecutor,TabCompleter,Listener {
    private final FeudalPlugin plugin;
    private final CommerceService commerce;
    private final ItemTransactions items;
    private final double radius;
    private final TradeCurrencies currencies;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Request> requests=new HashMap<>();
    private final Map<UUID,Long> sent=new HashMap<>();
    private record Request(UUID from,long expires) {}
    private static final class Offer {final Map<Integer,ItemStack> slots=new TreeMap<>();String code="",quoteCode="";long coins;boolean ready,confirmed;}
    private static final class Session {
        final Player a,b;final Offer left=new Offer(),right=new Offer();View av,bv;long lockedAt,lastAction=System.currentTimeMillis();boolean finishing;
        Session(Player a,Player b){this.a=a;this.b=b;}
        Offer offer(Player p){return p.getUniqueId().equals(a.getUniqueId())?left:right;}
        Offer other(Player p){return p.getUniqueId().equals(a.getUniqueId())?right:left;}
        Player partner(Player p){return p.getUniqueId().equals(a.getUniqueId())?b:a;}
    }
    private static final class View implements InventoryHolder {
        final Session session;final Player owner;final Inventory inventory;
        View(Session session,Player owner){this.session=session;this.owner=owner;inventory=Bukkit.createInventory(this,54,LocalText.tr("Обмін з ")+session.partner(owner).getName());}
        public Inventory getInventory(){return inventory;}
    }
    public TradeCommands(FeudalPlugin plugin,CommerceService commerce,ItemTransactions items){
        this.plugin=plugin;this.commerce=commerce;this.items=items;this.currencies=new TradeCurrencies(commerce.s);radius=Math.max(1,Math.min(16,plugin.getConfig().getDouble("trade.radius",6)));
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,10,10);
    }
    boolean active(Player p){return sessions.containsKey(p.getUniqueId());}
    private boolean near(Player a,Player b){return a.isOnline() && b.isOnline() && !a.isDead() && !b.isDead() && a.getWorld().equals(b.getWorld()) && a.getLocation().distanceSquared(b.getLocation())<=radius*radius;}
    private void available(Player p) throws Exception {
 try(var languageScope=LocalText.use(p)){
require(!active(p),LocalText.tr("Гравець уже торгує."));CommerceService.inventoryAvailable(commerce.db,p.getUniqueId().toString());require(ItemTransactions.empty(p.getItemOnCursor()),LocalText.tr("Спершу звільніть предмет на курсорі."));
 }
}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
 try(var languageScope=LocalText.use(sender)){

        try {
            args=CommandLanguage.normalize("trade",args);require(sender instanceof Player,LocalText.tr("Команда доступна гравцю."));Player p=(Player)sender;
            if(args.length>0 && args[0].equalsIgnoreCase("cancel")){Session s=sessions.get(p.getUniqueId());if(s!=null)cancel(s,LocalText.tr("Обмін скасовано."));requests.entrySet().removeIf(e->e.getKey().equals(p.getUniqueId()) || e.getValue().from.equals(p.getUniqueId()));return true;}
            require(commerce.s.cityMarkets==null,LocalText.tr("Торгівля доступна через фізичний Товарний відділ: /market menu. Прямі перекази: /cu pay."));
            if(args.length>0 && args[0].equalsIgnoreCase("coins")) {
                require(args.length==3,LocalText.tr("/trade coins КОД сума"));Session s=sessions.get(p.getUniqueId());require(s!=null && !s.finishing,LocalText.tr("Спершу відкрийте обмін."));
                String code=args[1].toUpperCase(Locale.ROOT);commerce.s.currency(code);long n=money(args[2]);require(commerce.s.balance("p:"+p.getUniqueId(),code)>=n,LocalText.tr("Недостатньо коштів у внутрішньому гаманці."));
                require(n==0 || currencies.owned(p.getUniqueId()).stream().anyMatch(c->c.code().equals(code)),LocalText.tr("Оберіть валюту зі свого гаманця."));Offer o=s.offer(p);o.code=code;o.coins=n;changed(s);return true;
            }
            if(args.length>0 && args[0].equalsIgnoreCase("accept")) {
                Request r=requests.get(p.getUniqueId());require(r!=null && r.expires>System.currentTimeMillis(),LocalText.tr("Немає чинного запиту."));Player other=Bukkit.getPlayer(r.from);require(other!=null && near(p,other),LocalText.tr("Підійдіть до учасника на ")+radius+LocalText.tr(" блоків."));
                if(args.length>1)require(other.getName().equalsIgnoreCase(args[1]),LocalText.tr("Запит надійшов від іншого гравця: ")+other.getName());
                available(p);available(other);requests.remove(p.getUniqueId());open(other,p);return true;
            }
            if(args.length>0 && args[0].equalsIgnoreCase("help")){plugin.message(p,LocalText.tr("/trade [гравець] — запит; /trade accept [гравець]; /trade coins КОД сума; /trade cancel. Радіус: ")+radius+LocalText.tr(" блоків."));return true;}
            available(p);Player target;
            if(args.length==0)target=Bukkit.getOnlinePlayers().stream().filter(x->!x.equals(p) && p.canSee(x) && near(p,x)).min(Comparator.comparingDouble(x->p.getLocation().distanceSquared(x.getLocation()))).orElse(null);
            else target=Bukkit.getPlayerExact(args[0]);
            require(target!=null && !target.equals(p) && p.canSee(target) && near(p,target),LocalText.tr("Гравець має бути поруч (до ")+radius+LocalText.tr(" блоків) у тому самому світі."));available(target);
            long now=System.currentTimeMillis();require(now-sent.getOrDefault(p.getUniqueId(),0L)>=10_000,LocalText.tr("Між запитами має минути 10 секунд."));
            Request previous=requests.get(target.getUniqueId());require(previous==null || previous.expires<=now,LocalText.tr("Гравець уже має вхідний запит."));
            requests.put(target.getUniqueId(),new Request(p.getUniqueId(),now+30_000));sent.put(p.getUniqueId(),now);
            plugin.message(p,LocalText.tr("Запит надіслано ")+target.getName()+LocalText.tr(" на 30 секунд."));plugin.message(target,p.getName()+LocalText.tr(" пропонує обмін. Прийняти: /trade accept ")+p.getName());
        }catch(IllegalArgumentException ex){plugin.message(sender,"§c"+ex.getMessage());}catch(Exception ex){plugin.failure(LocalText.tr("Обмін"),ex);plugin.message(sender,LocalText.tr("§cНе вдалося завершити обмін. Якщо передачу підготовлено, адміністратор побачить її у /commerce pending."));}return true;

 }
}
    private void open(Player a,Player b)throws Exception {
 try(var languageScope=LocalText.use(a)){
Session s=new Session(a,b);s.left.code=currencies.initial(a.getUniqueId());s.right.code=currencies.initial(b.getUniqueId());s.av=new View(s,a);s.bv=new View(s,b);sessions.put(a.getUniqueId(),s);sessions.put(b.getUniqueId(),s);try{render(s);a.openInventory(s.av.inventory);b.openInventory(s.bv.inventory);}catch(Exception ex){cancel(s,LocalText.tr("Не вдалося відкрити обмін."));throw ex;}
 }
}
    private static ItemStack icon(Material material,String title,String... lore){ItemStack i=new ItemStack(material);var m=i.getItemMeta();m.setDisplayName(title);m.setLore(List.of(lore));i.setItemMeta(m);return i;}
    private void render(Session s)throws Exception {render(s.av);render(s.bv);}
    private void render(View v)throws Exception {
        Inventory inv=v.inventory;inv.clear();Offer own=v.session.offer(v.owner),other=v.session.other(v.owner);int n=0;
        for(ItemStack item:own.slots.values())inv.setItem(n++,item.clone());n=27;for(ItemStack item:other.slots.values())inv.setItem(n++,item.clone());
        for(int slot=18;slot<27;slot++)inv.setItem(slot,icon(Material.GRAY_STAINED_GLASS_PANE," "));
        for(int slot=45;slot<54;slot++)inv.setItem(slot,icon(Material.GRAY_STAINED_GLASS_PANE," "));
        inv.setItem(18,icon(Material.GOLD_NUGGET,LocalText.tr("§eВи: ")+format(own.coins)+" "+own.code,LocalText.tr("ЛКМ +1; ПКМ −1; Shift — крок 10"),LocalText.tr("Точна сума: /trade coins КОД сума"),LocalText.tr("Це монети внутрішнього гаманця")));
        var wallet=currencies.personal(v.owner.getUniqueId(),own.code);
        inv.setItem(19,icon(Material.SUNFLOWER,LocalText.tr("§eВалюта 1: ")+wallet.name()+" ("+own.code+")",LocalText.tr("Особистий баланс: ")+format(wallet.balance())+" "+own.code,LocalText.tr("Натисніть для наступної власної валюти")));
        var targets=currencies.targets(own.code);if(!targets.contains(own.quoteCode))own.quoteCode=TradeCurrencies.next(targets,"");
        inv.setItem(20,icon(Material.LAPIS_LAZULI,own.quoteCode.isEmpty()?LocalText.tr("§bВалюта 2: немає іншої валюти"):LocalText.tr("§bВалюта 2: ")+currencies.name(own.quoteCode)+" ("+own.quoteCode+")",currencies.quote(own.code,own.quoteCode),LocalText.tr("Натисніть для наступної валюти"),LocalText.tr("Перегляд курсу без переказу коштів")));
        inv.setItem(21,icon(own.ready?Material.LIME_DYE:Material.YELLOW_DYE,own.ready?LocalText.tr("§aВаша пропозиція зафіксована"):LocalText.tr("§e1. Зафіксувати пропозицію"),LocalText.tr("Кожна зміна скидає обидва підтвердження")));
        long wait=Math.max(0,3-(System.currentTimeMillis()-v.session.lockedAt)/1000);
        inv.setItem(23,icon(own.confirmed?Material.EMERALD:Material.CLOCK,own.confirmed?LocalText.tr("§aПідтверджено"):LocalText.tr("§e2. Підтвердити обмін"),own.ready && other.ready?LocalText.tr("Перевірте обидві пропозиції; очікування: ")+wait+LocalText.tr(" с"):LocalText.tr("Спершу обидва фіксують пропозиції")));
        inv.setItem(24,icon(Material.BARRIER,LocalText.tr("§cСкасувати обмін")));
        inv.setItem(26,icon(Material.BOOK,LocalText.tr("Ваша пропозиція — зверху"),LocalText.tr("Натисніть стек у своєму інвентарі, щоб додати"),LocalText.tr("Повторне натискання прибирає його"),LocalText.tr("Предмети залишаються у вас до завершення")));
        inv.setItem(45,icon(Material.GOLD_NUGGET,LocalText.tr("§eПартнер: ")+format(other.coins)+" "+other.code));
        inv.setItem(49,icon(other.confirmed?Material.EMERALD:other.ready?Material.LIME_DYE:Material.YELLOW_DYE,other.confirmed?LocalText.tr("Партнер підтвердив"):other.ready?LocalText.tr("Партнер зафіксував пропозицію"):LocalText.tr("Партнер обирає предмети")));
    }
    private void changed(Session s)throws Exception {s.left.ready=s.right.ready=s.left.confirmed=s.right.confirmed=false;s.lockedAt=0;s.lastAction=System.currentTimeMillis();render(s);}
    @EventHandler(priority=EventPriority.HIGH) public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !v.owner.equals(p) || sessions.get(p.getUniqueId())!=v.session)return;Session s=v.session;if(s.finishing)return;
        try {
            require(near(s.a,s.b),LocalText.tr("Учасники мають стояти поруч."));Offer o=s.offer(p);
            int raw=e.getRawSlot();
            if(e.getClickedInventory()==p.getInventory()){
                require(e.getClick()==ClickType.LEFT || e.getClick()==ClickType.RIGHT,LocalText.tr("Використовуйте звичайне натискання без гарячих клавіш."));int slot=e.getSlot();require(slot>=0 && slot<36,LocalText.tr("Оберіть предмет із головного інвентарю."));
                if(o.slots.remove(slot)==null){ItemStack item=p.getInventory().getItem(slot);require(!ItemTransactions.empty(item),LocalText.tr("Клітинка порожня."));require(o.slots.size()<18,LocalText.tr("Максимум 18 стеків за обмін."));o.slots.put(slot,item.clone());}changed(s);return;
            }
            if(raw>=0 && raw<18){var keys=new ArrayList<>(o.slots.keySet());if(raw<keys.size()){o.slots.remove(keys.get(raw));changed(s);}return;}
            if(raw==24){cancel(s,LocalText.tr("Обмін скасовано."));return;}
            if(raw==18){require(!o.code.isEmpty(),LocalText.tr("У гаманці немає монет міста."));require(e.getClick()==ClickType.LEFT || e.getClick()==ClickType.RIGHT || e.getClick()==ClickType.SHIFT_LEFT || e.getClick()==ClickType.SHIFT_RIGHT,LocalText.tr("ЛКМ або ПКМ."));long delta=e.isShiftClick()?1000:100;long next=e.isRightClick()?Math.max(0,o.coins-delta):Math.addExact(o.coins,delta);require(next<=CurrencyService.MAX_MONEY && commerce.s.balance("p:"+p.getUniqueId(),o.code)>=next,LocalText.tr("Недостатньо монет."));o.coins=next;changed(s);}
            if(raw==19){var codes=currencies.owned(p.getUniqueId()).stream().map(TradeCurrencies.Currency::code).toList();require(!codes.isEmpty(),LocalText.tr("У вашому особистому гаманці немає монет."));String next=TradeCurrencies.next(codes,o.code);if(!next.equals(o.code)){o.code=next;o.coins=0;changed(s);}}
            if(raw==20){o.quoteCode=TradeCurrencies.next(currencies.targets(o.code),o.quoteCode);s.lastAction=System.currentTimeMillis();render(s);}
            if(raw==21){o.ready=!o.ready;s.left.confirmed=s.right.confirmed=false;s.lockedAt=s.left.ready && s.right.ready?System.currentTimeMillis():0;s.lastAction=System.currentTimeMillis();render(s);}
            if(raw==23){require(s.left.ready && s.right.ready && System.currentTimeMillis()-s.lockedAt>=3000,LocalText.tr("Обидва мають зафіксувати пропозиції та зачекати 3 секунди."));o.confirmed=true;s.lastAction=System.currentTimeMillis();render(s);if(s.left.confirmed && s.right.confirmed)settle(s);}
        }catch(IllegalArgumentException ex){plugin.message(p,"§c"+ex.getMessage());}catch(Exception ex){plugin.failure(LocalText.tr("Передача обміну"),ex);cancel(s,LocalText.tr("§cПередача потребує перевірки: /commerce pending."));}
    }
    private void settle(Session s) throws Exception {
        require(near(s.a,s.b),LocalText.tr("Ви відійшли від партнера."));require(!s.left.slots.isEmpty() || !s.right.slots.isEmpty() || s.left.coins>0 || s.right.coins>0,LocalText.tr("Порожній обмін."));
        var a=s.a.getInventory().getStorageContents();var b=s.b.getInventory().getStorageContents();
        var afterA=ItemTransactions.removeSlots(a,s.left.slots);var afterB=ItemTransactions.removeSlots(b,s.right.slots);
        for(ItemStack i:s.right.slots.values())afterA=ItemTransactions.add(afterA,i,i.getAmount());
        for(ItemStack i:s.left.slots.values())afterB=ItemTransactions.add(afterB,i,i.getAmount());
        var payments=new ArrayList<CommerceService.Payment>();
        if(s.left.coins>0)payments.add(new CommerceService.Payment("p:"+s.a.getUniqueId(),"p:"+s.b.getUniqueId(),s.left.code,s.left.coins));
        if(s.right.coins>0)payments.add(new CommerceService.Payment("p:"+s.b.getUniqueId(),"p:"+s.a.getUniqueId(),s.right.code,s.right.coins));
        String id=commerce.trade(new Actor(s.a.getUniqueId().toString(),false),s.b.getUniqueId().toString(),List.of(items.change(s.a,a,afterA),items.change(s.b,b,afterB)),payments);
        s.finishing=true;items.apply(id);cancel(s,LocalText.tr("§aОбмін завершено. № ")+id);
    }
    private void cancel(Session s,String message){sessions.remove(s.a.getUniqueId());sessions.remove(s.b.getUniqueId());for(Player p:List.of(s.a,s.b)){if(p.isOnline()){if(p.getOpenInventory().getTopInventory().getHolder() instanceof View v && v.session==s)p.closeInventory();plugin.message(p,message);}}}
    private void tick(){long now=System.currentTimeMillis();requests.entrySet().removeIf(e->e.getValue().expires<=now);sent.entrySet().removeIf(e->now-e.getValue()>60_000);
        for(Session s:new HashSet<>(sessions.values())){if(!near(s.a,s.b) || now-s.lastAction>180_000)cancel(s,LocalText.tr("Обмін скасовано: відстань або 3 хвилини без дій."));else try{render(s);}catch(Exception ex){plugin.failure(LocalText.tr("Оновлення валют обміну"),ex);cancel(s,LocalText.tr("Не вдалося оновити дані валют. Обмін скасовано."));}}}
    public void shutdown(){for(Session s:new HashSet<>(sessions.values()))cancel(s,LocalText.tr("Сервер завершує роботу. Обмін скасовано."));requests.clear();}
    @EventHandler public void close(InventoryCloseEvent e){if(e.getInventory().getHolder() instanceof View v && sessions.containsKey(e.getPlayer().getUniqueId()) && !v.session.finishing)cancel(v.session,LocalText.tr("Вікно закрито. Обмін скасовано."));}
    @EventHandler public void quit(PlayerQuitEvent e){Session s=sessions.get(e.getPlayer().getUniqueId());if(s!=null)cancel(s,LocalText.tr("Учасник вийшов. Обмін скасовано."));requests.entrySet().removeIf(x->x.getKey().equals(e.getPlayer().getUniqueId()) || x.getValue().from.equals(e.getPlayer().getUniqueId()));}
    @EventHandler(ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){Session s=sessions.get(e.getPlayer().getUniqueId());if(s!=null)cancel(s,LocalText.tr("Телепортація. Обмін скасовано."));}
    @EventHandler public void death(PlayerDeathEvent e){Session s=sessions.get(e.getEntity().getUniqueId());if(s!=null)cancel(s,LocalText.tr("Учасник загинув. Обмін скасовано."));}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
 try(var languageScope=LocalText.use(sender)){
return CommandHints.complete(commerce.s,sender,"trade",args);
 }
}
}
