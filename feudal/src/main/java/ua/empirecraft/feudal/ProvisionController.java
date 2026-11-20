package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.event.town.TownPreMergeEvent;
import com.palmergames.bukkit.towny.event.town.TownPreUnclaimEvent;
import com.palmergames.bukkit.towny.event.town.TownUnclaimEvent;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.potion.*;
import java.lang.reflect.*;
import java.math.*;
import java.nio.file.*;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

/** Native Towny permissions/events remain authoritative. No world blocks are removed by famine. */
public final class ProvisionController implements Listener,CommandExecutor {
    public boolean settleArmy(String townId){try{var town=com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(java.util.UUID.fromString(townId.substring(2)));return town!=null&&settle(town,clock(town));}catch(Exception ex){return false;}}

    private final FeudalPlugin plugin;private final ProvisionService service;private final ItemTransactions items;private final TownyBridge bridge;
    private final Map<UUID,Map<ProvisionClock.Plot,Long>> reservations=new HashMap<>();
    private String clockError="";private long nextConsequences;
    private static final class Chest implements InventoryHolder {
        final UUID town;final Map<Integer,Long> versions=new HashMap<>();Inventory inventory;
        Chest(UUID town){this.town=town;}public Inventory getInventory(){return inventory;}
    }
    public ProvisionController(FeudalPlugin plugin,ProvisionService service,ItemTransactions items,TownyBridge bridge){this.plugin=plugin;this.service=service;this.items=items;this.bridge=bridge;}
    private ProvisionClock clock(Town town){
        var home=town.getHomeBlockOrNull();
        return ProvisionClock.world(home==null?null:home.getWorldCoord().getWorldName(),service.s.now());
    }
    private static String id(Town town){return TownyBridge.townId(town);}
    private Town own(Player p){
 try(var languageScope=LocalText.use(p)){
var r=TownyAPI.getInstance().getResident(p);require(r!=null && r.hasTown(),LocalText.tr("Ви маєте бути жителем міста."));return r.getTownOrNull();
 }
}
    private boolean manager(Player p,Town t){var r=TownyAPI.getInstance().getResident(p);return p.hasPermission("empirecraft.feudal.admin") || r!=null && (t.isMayor(r) || t.hasResidentWithRank(r,"assistant"));}
    private static ProvisionClock.Plot plot(TownBlock b){return new ProvisionClock.Plot(b.getWorldCoord().getWorldName(),b.getX(),b.getZ());}
    private static List<ProvisionClock.Plot> plots(Town t){return t.getTownBlocks().stream().map(ProvisionController::plot).toList();}
    private long chunks(Town t){return ProvisionClock.chunks(plots(t),Coord.getCellSize());}
    private boolean settle(Town t,ProvisionClock c){return service.settle(id(t),chunks(t),service.s.now(),c);}
    public void register(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,40,40);
    }
    public void shutdown(){
        for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Chest)p.closeInventory();}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
 try(var languageScope=LocalText.use(sender)){

        try{
            require(sender.hasPermission("empirecraft.feudal.use"),LocalText.tr("Немає права користуватися провізією."));
            if(args.length>=2 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("history"))){
                require(sender.hasPermission("empirecraft.feudal.admin"),LocalText.tr("Потрібні права адміністратора."));Town t=TownyAPI.getInstance().getTown(args[1]);require(t!=null,LocalText.tr("Місто не знайдено."));var c=clock(t);require(settle(t,c),LocalText.tr("Незавершена передача: /commerce pending."));
                var row=service.town(id(t));plugin.message(sender,t.getName()+": "+chunks(t)+LocalText.tr(" чанків; запас ")+hunger(service.reserve(id(t)))+"; "+c.seasonName(service.s.now())+LocalText.tr("; голод від ")+number(row,"hungry_since")+LocalText.tr("; загалом ")+number(row,"hungry_ms")+LocalText.tr(" мс; недоотримано ")+hunger(ProvisionService.energy(row,"unmet"))+LocalText.tr(" од."));
                if(args[0].equalsIgnoreCase("history"))for(var a:service.db.query("SELECT * FROM provision_actions WHERE town=? ORDER BY created DESC LIMIT 15",id(t)))plugin.message(sender,string(a,"target")+" "+string(a,"status")+": "+string(a,"detail"));return true;
            }
            require(sender instanceof Player,LocalText.tr("Сховище доступне гравцеві. Історія — у provision_towns та provision_actions у H2."));Player p=(Player)sender;
            Town t=own(p);var c=clock(t);require(settle(t,c),LocalText.tr("Провізія заблокована незавершеною передачею: /commerce pending."));
            try(var menuLanguage=LocalText.useMenu(p)){Chest h=new Chest(t.getUUID());h.inventory=Bukkit.createInventory(h,54,LocalText.tr("Провізія: ")+t.getName());render(h,t,c);p.openInventory(h.inventory);}
        }catch(Exception ex){plugin.message(sender,"§c"+ex.getMessage());}return true;

 }
}
    private static ItemStack icon(Material material,String title,String... lore){var item=MenuIcons.item(material);var meta=item.getItemMeta();meta.setDisplayName("§6"+title);meta.setLore(Arrays.stream(lore).map(s->"§7"+s).toList());MenuSkin.apply(meta,material,-1,"provision");item.setItemMeta(meta);return item;}
    private static String hunger(BigInteger value){return new BigDecimal(value).divide(new BigDecimal(ProvisionClock.UNIT),2,RoundingMode.DOWN).toPlainString();}
    private void render(Chest h,Town t,ProvisionClock c)throws Exception {
        h.inventory.clear();h.versions.clear();
        for(var row:service.db.query("SELECT * FROM provision_stock WHERE town=? ORDER BY slot",id(t))){int slot=(int)number(row,"slot");h.versions.put(slot,number(row,"version"));if(number(row,"quantity")>0){var item=ItemTransactions.prototype(string(row,"item"));item.setAmount((int)number(row,"quantity"));h.inventory.setItem(slot,item);}}
        var state=service.town(id(t));long now=service.s.now(),since=number(state,"hungry_since");
        h.inventory.setItem(45,icon(Material.CHEST,LocalText.tr("Міське сховище"),LocalText.tr("Натисніть їжу у своєму інвентарі, щоб внести."),LocalText.tr("ЛКМ — стак або доступна частина; ПКМ — 1."),LocalText.tr("Мер і помічник можуть забирати їжу з комірок.")));
        h.inventory.setItem(47,icon(Material.WHEAT,LocalText.tr("Запас: ")+hunger(service.reserve(id(t)))+LocalText.tr(" од. голоду"),c.seasonName(now),LocalText.tr("Територія: ")+chunks(t)+LocalText.tr(" Minecraft-чанків"),LocalText.tr("Наступні 24 год: ")+hunger(c.cost(now,now+ProvisionClock.DAY,chunks(t)))+LocalText.tr(" од."),LocalText.tr("Кожен чанк: 8 × сезон / реальну добу.")));
        h.inventory.setItem(49,icon(since>0?Material.ROTTEN_FLESH:Material.BREAD,since>0?LocalText.tr("Місто голодує"):LocalText.tr("Місто забезпечене"),since>0?LocalText.tr("Без їжі: ")+((now-since)/3_600_000)+LocalText.tr(" год."):LocalText.tr("Провізія витрачається також під час простою."),LocalText.tr("Голод за всю історію: ")+number(state,"hungry_ms")/3_600_000+LocalText.tr(" год."),LocalText.tr("Втрата окраїн: після ")+lossDelay()/3_600_000+LocalText.tr(" год."),LocalText.tr("Розпад міста: після ")+collapseDelay()/3_600_000+LocalText.tr(" год. повного голоду.")));
        h.inventory.setItem(53,icon(Material.PAPER,LocalText.tr("Місткість залежить від запасу"),LocalText.tr("Для нових чанків потрібен запас на 24 години."),LocalText.tr("Оновити показники: натисніть тут."),LocalText.tr("Облік — поживність їжі, без насичення.")));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Chest h))return;e.setCancelled(true);
        if(e.isCancelled() && !(e.getWhoClicked() instanceof Player))return;Player p=(Player)e.getWhoClicked();
        try(var menuLanguage=LocalText.useMenu(p)){
        try{
            CommerceService.inventoryAvailable(service.db,p.getUniqueId().toString());Town t=TownyAPI.getInstance().getTown(h.town);require(t!=null && own(p)==t,LocalText.tr("Ви вже не житель цього міста."));
            var c=clock(t);require(settle(t,c),LocalText.tr("Незавершена передача провізії: зверніться до адміністратора."));
            var land=reservations.get(t.getUUID());if(land!=null){long now=service.s.now();land.values().removeIf(expiry->expiry<now);require(land.isEmpty(),LocalText.tr("Дочекайтеся завершення приватизації території перед передачею їжі."));}
            require(ItemTransactions.empty(p.getItemOnCursor()),LocalText.tr("Звільніть курсор."));
            if(e.getRawSlot()==53){render(h,t,c);return;}
            if(e.getClick()!=ClickType.LEFT && e.getClick()!=ClickType.RIGHT)return;
            ItemStack[] before=p.getInventory().getStorageContents(),after;String encoded;int slot,delta,nutrition,max;long expected;
            if(e.getClickedInventory()==p.getInventory() && e.getSlot()>=0 && e.getSlot()<before.length){
                ItemStack item=before[e.getSlot()];if(ItemTransactions.empty(item))return;
                nutrition=nutrition(item,p);require(nutrition>0,LocalText.tr("Цей предмет не є їжею з поживністю."));
                ItemStack prototype=item.clone();prototype.setAmount(1);max=prototype.getMaxStackSize();slot=-1;int existing=0;expected=0;encoded=GoldInventoryBridge.encode(new ItemStack[]{prototype});
                var rows=service.db.query("SELECT * FROM provision_stock WHERE town=? ORDER BY slot",id(t));
                Map<Integer,Map<String,Object>> bySlot=new HashMap<>();for(var row:rows)bySlot.put((int)number(row,"slot"),row);
                for(int pass=0;pass<2 && slot<0;pass++)for(int n=0;n<ProvisionService.SLOTS;n++){
                    var row=bySlot.get(n);int qty=row==null?0:(int)number(row,"quantity");
                    if(pass==0 && qty>0 && qty<max && number(row,"nutrition")==nutrition && ItemTransactions.prototype(string(row,"item")).isSimilar(prototype)){
                        slot=n;existing=qty;expected=number(row,"version");encoded=string(row,"item");break;
                    }if(pass==1 && qty==0){slot=n;expected=row==null?0:number(row,"version");break;}
                }
                require(slot>=0,LocalText.tr("Сховище заповнене."));delta=Math.min(e.isRightClick()?1:item.getAmount(),max-existing);after=ItemTransactions.copy(before);
                after[e.getSlot()].setAmount(item.getAmount()-delta);if(after[e.getSlot()].getAmount()==0)after[e.getSlot()]=null;
            }else if(e.getRawSlot()>=0 && e.getRawSlot()<ProvisionService.SLOTS){
                require(manager(p,t),LocalText.tr("Забирати провізію можуть мер, помічник або адміністратор."));slot=e.getRawSlot();
                var row=service.db.one("SELECT * FROM provision_stock WHERE town=? AND slot=?",id(t),slot);if(row==null || number(row,"quantity")==0){render(h,t,c);return;}
                expected=h.versions.getOrDefault(slot,0L);require(expected==number(row,"version"),LocalText.tr("Комірка змінилася; натисніть оновлення."));
                encoded=string(row,"item");nutrition=(int)number(row,"nutrition");max=(int)number(row,"max_stack");int count=e.isRightClick()?1:(int)number(row,"quantity");delta=-count;after=ItemTransactions.add(before,ItemTransactions.prototype(encoded),count);
            }else return;
            String op=service.transfer(new FeudalService.Actor(p.getUniqueId().toString(),p.hasPermission("empirecraft.feudal.admin")),id(t),slot,expected,encoded,delta,nutrition,max,items.change(p,before,after));
            items.apply(op);settle(t,c);render(h,t,c);
        }catch(Exception ex){plugin.message(p,"§c"+ex.getMessage());}
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Chest)e.setCancelled(true);}
    /** Forge FoodProperties also covers modded items; preserve their complete ItemStack/NBT in SQL. */
    static int nutrition(ItemStack item,Player player)throws Exception {
        String craft=Bukkit.getServer().getClass().getPackage().getName();Class<?> bridge=Class.forName(craft+".inventory.CraftItemStack");
        Object nms=bridge.getMethod("asNMSCopy",ItemStack.class).invoke(null,item);Object food=null;
        for(Method m:nms.getClass().getMethods())if(m.getName().equals("getFoodProperties") && m.getParameterCount()==1){Object handle=player.getClass().getMethod("getHandle").invoke(player);food=m.invoke(nms,handle);break;}
        if(food==null){Object nmsItem=invoke(nms,"getItem","m_41720_");food=invoke(nmsItem,"getFoodProperties","m_41473_");}
        return food==null?0:((Number)invoke(food,"getNutrition","m_38744_")).intValue();
    }
    private static Object invoke(Object target,String... names)throws Exception {
        for(String name:names)try{return target.getClass().getMethod(name).invoke(target);}catch(NoSuchMethodException ignored){}
        throw new IllegalStateException(LocalText.tr("Не вдалося прочитати поживність Forge-предмета."));
    }
    private static boolean foundingHome(TownPreClaimEvent e){
        Town town=e.getTown();TownBlock block=e.getTownBlock();
        // Towny 0.101.2 sets the candidate's owner AND home block before this event,
        // then assigns the mayor only after it succeeds. Existing towns and bulk
        // selections can also have isHomeBlock=true, so that flag alone is unsafe.
        return e.isHomeBlock() && !e.isOutpost() && !e.isOverClaim() && !town.hasMayor()
            && town.getNumTownBlocks()<=1
            && (town.getTownBlocks().isEmpty() || town.getTownBlocks().contains(block))
            && (town.getHomeBlockOrNull()==null || town.getHomeBlockOrNull()==block)
            && (block.getTownOrNull()==null || block.getTownOrNull()==town);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void claim(TownPreClaimEvent e){
        try{Town t=e.getTown();var c=clock(t);if(foundingHome(e))return;
            Town previous=e.getTownBlock().getTownOrNull();if(previous!=null && previous!=t)require(settle(previous,clock(previous)),LocalText.tr("У попереднього власника є незавершена передача провізії."));require(settle(t,c),LocalText.tr("Незавершена передача провізії."));
            long now=service.s.now();var pending=reservations.computeIfAbsent(t.getUUID(),x->new HashMap<>());pending.values().removeIf(expiry->expiry<now);
            var all=new HashSet<>(plots(t));all.addAll(pending.keySet());all.add(plot(e.getTownBlock()));
            require(service.canSupport(id(t),ProvisionClock.chunks(all,Coord.getCellSize()),now,c),LocalText.tr("Місту потрібен запас провізії на 24 реальні години для всієї нової території: /t provision."));
            pending.put(plot(e.getTownBlock()),now+60_000);
        }catch(Exception ex){e.setCancelled(true);e.setCancelMessage(LocalText.tr("Провізія: ")+ex.getMessage());}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void claimResult(TownPreClaimEvent e){if(e.isCancelled()){var m=reservations.get(e.getTown().getUUID());if(m!=null)m.remove(plot(e.getTownBlock()));}}
    @EventHandler(priority=EventPriority.MONITOR) public void claimed(TownClaimEvent e){var m=reservations.get(e.getTown().getUUID());if(m!=null)m.remove(plot(e.getTownBlock()));update(e.getTown());}
    @EventHandler(priority=EventPriority.MONITOR) public void founded(NewTownEvent e){update(e.getTown());}
    @EventHandler(priority=EventPriority.MONITOR) public void unclaimed(TownUnclaimEvent e){update(e.getTown());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void merge(TownPreMergeEvent e){
        try{Town t=e.getRemainingTown(),other=e.getSuccumbingTown();var c=clock(t);require(settle(t,c) && settle(other,clock(other)),LocalText.tr("Незавершена передача провізії."));require(service.stock(id(other)).isEmpty(),LocalText.tr("Перед злиттям перенесіть їжу зі сховища міста, яке приєднується, у сховище міста, що залишається."));var all=new HashSet<>(plots(t));all.addAll(plots(other));
            require(service.canSupport(id(t),ProvisionClock.chunks(all,Coord.getCellSize()),service.s.now(),c),LocalText.tr("Перед злиттям внесіть у місто, що залишається, запас їжі на 24 години для спільної території."));
        }catch(Exception ex){e.setCancelled(true);e.setCancelMessage(LocalText.tr("Провізія: ")+ex.getMessage());}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void unclaiming(TownPreUnclaimEvent e){
        try{if(e.getTown()!=null)service.available(id(e.getTown()));}catch(Exception ex){e.setCancelled(true);e.setCancelMessage(ex.getMessage());}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void deleting(PreDeleteTownEvent e){
        try{
            Town t=e.getTown();service.available(id(t));
            if(t.getUUID().equals(bridge.famineTown)){
                // Towny's bulk delete otherwise unregisters the town even if an individual unclaim is cancelled.
                // Remove through its native API first, keeping the capital until every other plot succeeds.
                var home=t.getHomeBlockOrNull();var blocks=new ArrayList<>(t.getTownBlocks());
                blocks.sort(Comparator.comparingInt(b->b==home?1:0));
                for(var block:blocks){TownyUniverse.getInstance().getDataSource().removeTownBlock(block);
                    if(t.getTownBlocks().contains(block)){e.setCancelled(true);e.setCancelMessage(LocalText.tr("Захищену ділянку не знято; розпад зупинено без видалення міста."));return;}
                }
            }
        }catch(Exception ex){e.setCancelled(true);e.setCancelMessage(ex.getMessage());}
    }
    private void update(Town town){try{settle(town,clock(town));}catch(Exception ex){plugin.failure(LocalText.tr("Облік території та провізії"),ex);}}
    private long lossDelay(){return Math.max(1,plugin.getConfig().getLong("provisions.loss-after-hours",48))*3_600_000L;}
    private long lossInterval(){return Math.max(1,plugin.getConfig().getLong("provisions.loss-every-hours",12))*3_600_000L;}
    private long collapseDelay(){return Math.max(1,plugin.getConfig().getLong("provisions.collapse-after-hours",96))*3_600_000L;}
    private void tick(){
        try{clockError="";long now=service.s.now();boolean consequences=now>=nextConsequences;if(consequences)nextConsequences=now+30_000;
            if(consequences)for(var action:service.db.query("SELECT * FROM provision_actions WHERE status='PENDING'")){
                String townId=string(action,"town");Town current=TownyAPI.getInstance().getTown(UUID.fromString(townId.substring(2)));
                var state=service.town(townId);
                if(current==null)service.action(string(action,"id"),"DONE",LocalText.tr("Місто вже відсутнє після відновлення"));
                else if(state==null || number(state,"hungry_since")!=number(action,"streak"))service.action(string(action,"id"),"CANCELLED",LocalText.tr("Період голоду завершено"));
            }
            for(Town town:List.copyOf(TownyAPI.getInstance().getTowns()))try{
                if(!settle(town,clock(town)))continue;var row=service.town(id(town));long since=number(row,"hungry_since");
                if(since>0 && !town.isRuined()){
                    debuffs(town,now-since);
                    if(consequences){if(now-since>=collapseDelay())collapse(town,since,now);else if(now-since>=lossDelay())lose(town,since,now);}
                }
            }catch(Exception ex){plugin.failure(LocalText.tr("Провізія міста ")+town.getName(),ex);}
            for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Chest h){
                Town t=TownyAPI.getInstance().getTown(h.town);
                if(t==null || ownOrNull(p)!=t)p.closeInventory();else if(!service.pending(id(t)))render(h,t,clock(t));
            }
        }catch(Exception ex){if(!Objects.equals(clockError,ex.getMessage())){clockError=ex.getMessage();plugin.getLogger().warning(LocalText.tr("Провізія призупинена: ")+clockError);}}
    }
    private void debuffs(Town town,long elapsed){int level=elapsed>=ProvisionClock.DAY?1:0;
        for(Player p:Bukkit.getOnlinePlayers())if(ownOrNull(p)==town && TownyAPI.getInstance().getTown(p.getLocation())==town){
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,100,level,true,true));p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING,100,level,true,true));
            if(elapsed>=2*ProvisionClock.DAY)p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,100,0,true,true));
        }
    }
    private Town ownOrNull(Player p){var r=TownyAPI.getInstance().getResident(p);return r==null?null:r.getTownOrNull();}
    private static String target(TownBlock b){var p=plot(b);return p.world()+"/"+p.x()+"/"+p.z();}
    private void backup(Town town,long since)throws Exception {
        Path dir=plugin.getDataFolder().toPath().resolve("famine-backups");Files.createDirectories(dir);Path path=dir.resolve(town.getUUID()+"-"+since+".yml");if(Files.exists(path))return;
        var y=new YamlConfiguration();y.set("uuid",town.getUUID().toString());y.set("name",town.getName());y.set("hunger-since",since);y.set("mayor",town.getMayor().getUUID().toString());y.set("claims",town.getTownBlocks().stream().map(ProvisionController::target).toList());y.set("home",town.getHomeBlockOrNull()==null?null:target(town.getHomeBlockOrNull()));y.set("residents",town.getResidents().stream().map(r->r.getUUID().toString()).toList());y.set("nation",town.getNationOrNull()==null?null:town.getNationOrNull().getUUID().toString());
        service.db.backup(dir.resolve(town.getUUID()+"-"+since+".zip").toAbsolutePath().toString());
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temporary,y.saveToString());Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE);
    }
    private void lose(Town t,long since,long now)throws Exception {
        backup(t,since);long current=(now-since-lossDelay())/lossInterval();
        // Replay each due period exactly once; persistent fixed targets survive interrupted native operations.
        for(long period=0;period<=current && period<400;period++){
            Set<ProvisionClock.Plot> territory=new HashSet<>(plots(t));var home=t.getHomeBlockOrNull();
            var candidates=new ArrayList<>(t.getTownBlocks());candidates.removeIf(b->b==home || !edge(plot(b),territory));
            candidates.sort(Comparator.<TownBlock>comparingInt(b->b.hasResident()?1:0).thenComparing(Comparator.comparingLong((TownBlock b)->distance(b,home)).reversed()).thenComparing(ProvisionController::target));
            int count=Math.min(candidates.size(),Math.max(1,(int)Math.ceil(t.getNumTownBlocks()*Math.max(0.01,Math.min(1,plugin.getConfig().getDouble("provisions.loss-fraction",0.1))))));
            var rows=service.plan(id(t),since,period,candidates.stream().limit(count).map(ProvisionController::target).toList(),now);
            for(var row:rows)if(string(row,"status").equals("PENDING")){
                String target=string(row,"target"),action=string(row,"id");TownBlock block=t.getTownBlocks().stream().filter(b->target(b).equals(target)).findFirst().orElse(null);
                if(block==null){service.action(action,"DONE",LocalText.tr("Ділянка вже вільна або відсутня"));continue;}
                if(block==t.getHomeBlockOrNull()){service.action(action,"BLOCKED",LocalText.tr("Нова столична ділянка захищена"));continue;}
                withoutRevert(List.of(block.getWorld()),()->TownyUniverse.getInstance().getDataSource().removeTownBlock(block));
                service.action(action,t.getTownBlocks().contains(block)?"BLOCKED":"DONE",LocalText.tr("Втрата території через голод"));
            }
        }
        t.save();Towny.getPlugin().resetCache();settle(t,clock(t));
    }
    private static boolean edge(ProvisionClock.Plot p,Set<ProvisionClock.Plot> set){return !set.contains(new ProvisionClock.Plot(p.world(),p.x()+1,p.z())) || !set.contains(new ProvisionClock.Plot(p.world(),p.x()-1,p.z())) || !set.contains(new ProvisionClock.Plot(p.world(),p.x(),p.z()+1)) || !set.contains(new ProvisionClock.Plot(p.world(),p.x(),p.z()-1));}
    private static long distance(TownBlock b,TownBlock home){if(home==null || b.getWorld()!=home.getWorld())return Long.MAX_VALUE;long dx=(long)b.getX()-home.getX(),dz=(long)b.getZ()-home.getZ();return dx*dx+dz*dz;}
    private void collapse(Town t,long since,long now)throws Exception {
        backup(t,since);var rows=service.plan(id(t),since,-1,List.of("COLLAPSE"),now);var row=rows.get(0);if(!string(row,"status").equals("PENDING"))return;
        for(TownBlock block:List.copyOf(t.getTownBlocks())){
            var pre=new TownPreUnclaimEvent(t,block);Bukkit.getPluginManager().callEvent(pre);
            if(pre.isCancelled()){service.action(string(row,"id"),"BLOCKED",LocalText.tr("Зняття привату заборонене іншим модулем: ")+pre.getCancelMessage());return;}
        }
        boolean[] removed={false};bridge.famineTown=t.getUUID();bridge.famineNation=t.getNationOrNull()==null?null:t.getNationOrNull().getUUID();
        try{withoutRevert(t.getTownBlocks().stream().map(TownBlock::getWorld).distinct().toList(),()->removed[0]=TownyUniverse.getInstance().getDataSource().removeTown(t,DeleteTownEvent.Cause.UNKNOWN,Bukkit.getConsoleSender(),false));}
        finally{bridge.famineTown=null;bridge.famineNation=null;}
        service.action(string(row,"id"),removed[0]?"DONE":"BLOCKED",LocalText.tr("Розпад після ")+collapseDelay()/3_600_000+LocalText.tr(" годин повного голоду"));
        if(removed[0]){bridge.sync();Bukkit.broadcastMessage(LocalText.tr("§6[Провізія] §cМісто ")+t.getName()+LocalText.tr(" припинило існування через тривалий голод."));}
    }
    /** Native Towny unclaims still fire cancellable events, while builds/entities are preserved. */
    private static void withoutRevert(Collection<TownyWorld> worlds,Runnable action){
        record Flags(TownyWorld world,boolean delete,boolean revert,boolean entities){}
        var flags=worlds.stream().map(w->new Flags(w,w.isUsingPlotManagementDelete(),w.isUsingPlotManagementRevert(),w.isDeletingEntitiesOnUnclaim())).toList();
        try{for(var f:flags){f.world.setUsingPlotManagementDelete(false);f.world.setUsingPlotManagementRevert(false);f.world.setDeletingEntitiesOnUnclaim(false);}action.run();}
        finally{for(var f:flags){f.world.setUsingPlotManagementDelete(f.delete);f.world.setUsingPlotManagementRevert(f.revert);f.world.setDeletingEntitiesOnUnclaim(f.entities);f.world.save();}}
    }
}
