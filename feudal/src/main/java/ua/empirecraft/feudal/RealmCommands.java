package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import java.time.Instant;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;
import ua.empirecraft.feudal.FeudalService.Actor;

public final class RealmCommands implements CommandExecutor,TabCompleter,Listener {
    final FeudalPlugin plugin;final FeudalService s;final TownyBridge towny;final RealmService realm;final CommerceService commerce;final ItemTransactions items;final TradeCommands trades;
    private static final class InfoView implements InventoryHolder {final Inventory inventory;InfoView(){inventory=Bukkit.createInventory(this,27,"Володіння та дипломатія");}public Inventory getInventory(){return inventory;}}
    public RealmCommands(FeudalPlugin plugin,FeudalService s,TownyBridge towny,RealmService realm,CommerceService commerce,ItemTransactions items,TradeCommands trades) throws Exception {
        this.plugin=plugin;this.s=s;this.towny=towny;this.realm=realm;this.commerce=commerce;this.items=items;this.trades=trades;
        s.db.execute("CREATE TABLE IF NOT EXISTS map_points(estate VARCHAR(80), kind VARCHAR(16), world VARCHAR(64), x INT, y INT, z INT, name VARCHAR(48), PRIMARY KEY(estate,kind))");
    }
    private static Actor actor(CommandSender sender){return new Actor(sender instanceof Player p?p.getUniqueId().toString():"console",sender.hasPermission("empirecraft.feudal.admin"));}
    private static Player player(CommandSender sender){require(sender instanceof Player,"Команда доступна у грі.");return (Player)sender;}
    private String estate(String value) throws Exception{return s.resolve(value).id();}
    private static long count(String value){try{require(value.matches("[0-9]{1,7}"),"Кількість має бути цілим числом.");long n=Long.parseLong(value);require(n>0 && n<=1_000_000,"Кількість: 1–1000000.");return n;}catch(NumberFormatException e){throw new IllegalArgumentException("Неправильна кількість.");}}
    private static int days(String value,int max){long n=count(value);require(n<=max,"Завеликий строк: максимум "+max+" діб.");return (int)n;}
    private void say(CommandSender p,String text){plugin.message(p,text);}
    private static String date(long value){return value==0?"—":Instant.ofEpochMilli(value).toString();}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        try {
            towny.sync();realm.tick();commerce.tick();String sub=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
            switch(cmd.getName()){
                case "diplomacy" -> diplomacy(sender,actor(sender),sub,args);
                case "order" -> order(sender,actor(sender),sub,args);
                case "dynasty" -> dynasty(sender,actor(sender),sub,args);
                case "estateinfo" -> info(sender,sub,args);
                case "commerce" -> admin(sender,actor(sender),sub,args);
            }
        }catch(IllegalArgumentException ex){say(sender,"§c"+ex.getMessage());}catch(Exception ex){plugin.failure("Держави й торгівля",ex);say(sender,"§cОперацію не завершено. Адміністратор може перевірити журнал /commerce pending.");}return true;
    }
    private void diplomacy(CommandSender p,Actor a,String sub,String[] x) throws Exception {
        switch(sub){
            case "offer" -> {require(x.length>=6,"/diplomacy offer ваша_держава інша_держава тип дні умови...");String id=realm.offer(a,estate(x[1]),estate(x[2]),x[3],days(x[4],365),String.join(" ",Arrays.copyOfRange(x,5,x.length)));say(p,"Умови збережено та підписано вами. Другий правитель: /diplomacy show "+id+", потім /diplomacy sign "+id);}
            case "sign" -> {require(x.length==2,"/diplomacy sign ID");realm.sign(a,x[1]);say(p,"Договір підписано обома сторонами.");}
            case "break","reject" -> {require(x.length>=3,"/diplomacy break ID причина...");realm.end(a,x[1],String.join(" ",Arrays.copyOfRange(x,2,x.length)));say(p,"Договір завершено; причина збережена в історії.");}
            case "show","history" -> {require(x.length==2,"/diplomacy show ID");var t=realm.treaty(x[1]);say(p,s.estate(string(t,"a")).name()+" ↔ "+s.estate(string(t,"b")).name()+" · "+string(t,"kind")+" · "+string(t,"status"));say(p,"Умови: "+string(t,"terms"));say(p,"Перший підпис: "+towny.playerName(string(t,"a_lord"))+"; другий: "+towny.playerName(string(t,"b_lord"))+" ("+(number(t,"starts")>0?"підписано":"очікується")+")");say(p,"Строк: "+number(t,"days")+" діб. Початок: "+date(number(t,"starts"))+"; кінець: "+date(number(t,"ends"))+"; підписати до: "+date(number(t,"deadline")));for(var e:s.db.query("SELECT * FROM diplomacy_events WHERE treaty=? ORDER BY seq DESC LIMIT 12",x[1]))say(p,date(number(e,"at_time"))+" · "+string(e,"event")+" · "+towny.playerName(string(e,"actor"))+": "+string(e,"reason"));}
            case "list" -> {String id=x.length>1?estate(x[1]):towny.defaultEstate(player(p));Estate e=s.estate(id);String nation=e.nation();require(nation!=null,"Володіння не належить державі.");var list=realm.treaties(nation);if(list.isEmpty())say(p,"Договорів немає.");for(var t:list)say(p,string(t,"id")+" · "+string(t,"kind")+" · "+string(t,"status")+" · "+s.estate(string(t,"a")).name()+" ↔ "+s.estate(string(t,"b")).name());}
            default -> say(p,"/diplomacy offer від_держави до_держави alliance|nonaggression|trade|passage дні умови; sign ID; show ID; list [держава]; break ID причина.");
        }
    }
    private void order(CommandSender sender,Actor a,String sub,String[] x) throws Exception {
        switch(sub){
            case "create" -> {Player p=player(sender);require(x.length==5 || x.length==6,"/order create КОД оплата кількість дні [володіння]");ItemStack item=p.getInventory().getItemInMainHand();require(!ItemTransactions.empty(item),"Тримайте потрібний предмет у головній руці.");item=item.clone();item.setAmount(1);String owner=x.length==6?"e:"+estate(x[5]):"p:"+a.id();String id=commerce.create(a,owner,GoldInventoryBridge.encode(new ItemStack[]{item}),count(x[3]),x[1].toUpperCase(Locale.ROOT),money(x[2]),days(x[4],90));say(sender,"Замовлення № "+id+" створено. Повна оплата зарезервована.");}
            case "deliver","collect" -> {
                require(x.length==2 || x.length==3,"/order "+sub+" ID [кількість]");Player p=player(sender);require(!trades.active(p),"Спершу закрийте обмін.");var o=commerce.order(x[1]);ItemStack item=ItemTransactions.prototype(string(o,"item"));var before=p.getInventory().getStorageContents();long n;
                if(sub.equals("deliver")){
                    long have=Arrays.stream(before).filter(i->!ItemTransactions.empty(i) && i.isSimilar(item)).mapToLong(ItemStack::getAmount).sum();n=x.length==3?count(x[2]):Math.min(have,number(o,"total")-number(o,"delivered"));
                    var after=ItemTransactions.take(before,item,n);String id=commerce.deliver(a,x[1],n,items.change(p,before,after));items.apply(id);say(sender,"Здано "+n+" предметів. Належну частину оплати зараховано у гаманець.");
                }else{
                    long space=0;for(ItemStack i:before){if(ItemTransactions.empty(i))space+=item.getMaxStackSize();else if(i.isSimilar(item))space+=Math.max(0,i.getMaxStackSize()-i.getAmount());}
                    n=x.length==3?count(x[2]):Math.min(space,number(o,"unclaimed"));var after=ItemTransactions.add(before,item,n);String id=commerce.collect(a,x[1],n,items.change(p,before,after));items.apply(id);say(sender,"Отримано "+n+" предметів зі сховища замовлення.");
                }
            }
            case "cancel" -> {require(x.length==2,"/order cancel ID");commerce.cancel(a,x[1]);say(sender,"Залишок оплати повернуто. Здані ресурси доступні через /order collect.");}
            case "show" -> {require(x.length==2,"/order show ID");var o=commerce.order(x[1]);ItemStack item=ItemTransactions.prototype(string(o,"item"));say(sender,string(o,"id")+" · "+string(o,"status")+" · "+item.getType()+" · "+(item.hasItemMeta() && item.getItemMeta().hasDisplayName()?item.getItemMeta().getDisplayName():"звичайна назва"));say(sender,"Здано "+number(o,"delivered")+"/"+number(o,"total")+"; до отримання: "+number(o,"unclaimed")+"; оплата: "+format(number(o,"reward"))+" "+string(o,"code")+"; сплачено: "+format(number(o,"paid"))+"; строк: "+date(number(o,"ends")));say(sender,"Власник: "+string(o,"owner")+". Предмети мають точно збігатися за властивостями.");}
            case "list" -> {var rows=s.db.query("SELECT * FROM orders WHERE status='OPEN' ORDER BY created DESC LIMIT 30");if(rows.isEmpty())say(sender,"Відкритих замовлень немає.");for(var o:rows)say(sender,string(o,"id")+" · "+number(o,"delivered")+"/"+number(o,"total")+" · "+ItemTransactions.prototype(string(o,"item")).getType()+" · "+format(number(o,"reward"))+" "+string(o,"code"));}
            case "mine" -> {for(var o:s.db.query("SELECT * FROM orders WHERE owner=? OR creator=? ORDER BY created DESC LIMIT 30","p:"+a.id(),a.id()))say(sender,string(o,"id")+" · "+string(o,"status")+" · до отримання "+number(o,"unclaimed"));}
            default -> say(sender,"Предмет у руці → /order create КОД оплата кількість дні [володіння]. /order list; mine; show ID; deliver ID [кількість]; collect ID [кількість]; cancel ID.");
        }
    }
    private void dynasty(CommandSender p,Actor a,String sub,String[] x) throws Exception {
        switch(sub){
            case "name" -> {require(x.length>=3,"/dynasty name володіння назва...");realm.house(a,estate(x[1]),String.join(" ",Arrays.copyOfRange(x,2,x.length)));say(p,"Назву правлячого дому збережено.");}
            case "nominate" -> {require(x.length==3,"/dynasty nominate володіння гравець");String id=estate(x[1]),person=towny.residentId(x[2]);towny.validateSuccessor(s.estate(id),person);realm.nominate(a,id,person);say(p,"Спадкоємця призначено. Він має виконати /dynasty accept "+id);}
            case "accept" -> {require(x.length==2,"/dynasty accept володіння");String id=estate(x[1]);towny.validateSuccessor(s.estate(id),a.id());realm.acceptHeir(a,id);say(p,"Ви погодилися стати спадкоємцем.");}
            case "revoke" -> {require(x.length==2,"/dynasty revoke володіння");realm.revoke(a,estate(x[1]));say(p,"Призначення відкликано.");}
            case "abdicate" -> {require(x.length==3 && x[2].equalsIgnoreCase("confirm"),"Передача влади незворотна звичайною командою: /dynasty abdicate володіння confirm");succeed(a,estate(x[1]),false);say(p,"Владу передано підтвердженому спадкоємцю.");}
            case "show","history" -> {require(x.length==2,"/dynasty show володіння");String id=estate(x[1]);var e=s.estate(id);var h=realm.heir(id);var house=s.db.one("SELECT name FROM dynasties WHERE estate=?",id);say(p,e.name()+" · правитель: "+towny.playerName(e.lord())+" · дім: "+(house==null?"не названий":string(house,"name")));say(p,h==null?"Спадкоємця немає":"Спадкоємець: "+towny.playerName(string(h,"heir"))+"; згода: "+bool(h,"accepted")+"; чинність: "+e.lord().equals(string(h,"lord")));say(p,"Передача за неактивністю: "+plugin.getConfig().getLong("dynasty.inactivity-days",30)+" діб (0 — вимкнена).");for(var op:s.db.query("SELECT * FROM succession_ops WHERE estate=? ORDER BY created DESC LIMIT 15",id))say(p,date(number(op,"created"))+" · "+towny.playerName(string(op,"old_lord"))+" → "+towny.playerName(string(op,"new_lord"))+" · "+string(op,"reason")+" · "+string(op,"status"));}
            default -> say(p,"/dynasty name володіння назва; nominate володіння гравець; accept володіння; revoke володіння; show володіння; abdicate володіння confirm.");
        }
    }
    void succeed(Actor actor,String id,boolean inactive) throws Exception {
        Estate e=s.estate(id);var h=realm.heir(id);require(h!=null,"Спадкоємця немає.");towny.validateSuccessor(e,string(h,"heir"));
        String op=realm.prepareSuccession(actor,id,inactive,plugin.getConfig().getLong("dynasty.inactivity-days",30));
        try{towny.transferAuthority(e,string(h,"heir"));towny.sync();realm.finishSuccession(op);}catch(Exception ex){reconcileSuccession();throw ex;}
    }
    void reconcileSuccession() throws Exception {
        towny.sync();for(var op:s.db.query("SELECT * FROM succession_ops WHERE status='PREPARED'")){
            Estate e=s.estate(string(op,"estate"));String id=string(op,"id");
            if(e.active() && (e.lord().equals(string(op,"new_lord")) || (e.kind()==Kind.DUCHY || e.kind()==Kind.COUNTY) && e.lord().equals(string(op,"old_lord"))))realm.finishSuccession(id);
            else s.db.transaction(()->{s.db.execute("UPDATE succession_ops SET status=? WHERE id=?",e.lord().equals(string(op,"old_lord"))?"FAILED":"CONFLICT",id);s.audit(new Actor("succession",true),"SUCCESSION_CHECK",id+" фактичний правитель="+e.lord());return null;});
        }
    }
    void tick() throws Exception {
        for(Player p:Bukkit.getOnlinePlayers())realm.activity(p.getUniqueId().toString());realm.tick();commerce.tick();
        long inactivity=plugin.getConfig().getLong("dynasty.inactivity-days",30);if(inactivity<1)return;
        for(var h:s.db.query("SELECT h.* FROM heirs h JOIN ruler_activity a ON a.person=h.lord WHERE h.accepted=TRUE AND a.seen<=?",s.now()-Math.multiplyExact(inactivity,FeudalService.DAY))){
            Estate e=s.estate(string(h,"estate"));if(!e.active() || !e.lord().equals(string(h,"lord")) || Bukkit.getPlayer(UUID.fromString(e.lord()))!=null)continue;
            try{succeed(new Actor("succession",true),e.id(),true);}catch(IllegalArgumentException ex){plugin.getLogger().fine("Спадкування "+e.name()+": "+ex.getMessage());}
        }
    }
    private void admin(CommandSender p,Actor a,String sub,String[] x) throws Exception {
        if(sub.equals("pending")){
            var rows=a.admin()?s.db.query("SELECT * FROM item_ops WHERE status='PREPARED'"):s.db.query("SELECT o.* FROM item_ops o JOIN item_participants p ON p.op=o.id WHERE o.status='PREPARED' AND p.person=?",a.id());
            if(rows.isEmpty())say(p,"Незавершених передач предметів немає.");for(var row:rows)say(p,string(row,"id")+" · "+string(row,"kind")+" · "+date(number(row,"created")));return;
        }
        require(a.admin(),"Лише адміністратор.");
        switch(sub){
            case "inspect" -> {require(x.length==2,"/commerce inspect ID");var row=s.db.one("SELECT * FROM item_ops WHERE id=?",x[1]);require(row!=null,"Операцію не знайдено.");say(p,row.toString());for(var r:s.db.query("SELECT * FROM item_participants WHERE op=?",x[1])){Player who=Bukkit.getPlayer(UUID.fromString(string(r,"person")));String state=who==null?"офлайн":ItemTransactions.same(who.getInventory().getStorageContents(),ItemTransactions.decode(string(r,"after_items")))?"після":ItemTransactions.same(who.getInventory().getStorageContents(),ItemTransactions.decode(string(r,"before_items")))?"до":"відрізняється від обох";say(p,towny.playerName(string(r,"person"))+": "+state);}for(var r:s.db.query("SELECT * FROM item_payments WHERE op=?",x[1]))say(p,r.toString());if(string(row,"kind").startsWith("PROV_"))for(var r:s.db.query("SELECT town,slot,before_version,quantity,nutrition FROM provision_transfers WHERE op=?",x[1]))say(p,r.toString());}
            case "recover","abort" -> {require(x.length==2,"/commerce "+sub+" ID");for(var r:s.db.query("SELECT person FROM item_participants WHERE op=?",x[1])){Player who=Bukkit.getPlayer(UUID.fromString(string(r,"person")));if(who!=null)require(!trades.active(who),"Закрийте обміни учасників перед відновленням.");}items.recover(x[1],sub.equals("recover"));s.db.transaction(()->{s.audit(a,"ITEM_ADMIN",sub+" "+x[1]);return null;});say(p,"Результат зафіксовано один раз.");}
            default -> say(p,"/commerce pending; inspect ID; recover ID — довести передачу до кінця; abort ID — скасувати, лише якщо всі інвентарі відповідають стану до передачі.");
        }
    }
    private World dimension(String value){for(World w:Bukkit.getWorlds()){if(w.getName().equals(value) || w.getKey().toString().equals(value))return w;try{Object handle=w.getClass().getMethod("getHandle").invoke(w);Object key=handle.getClass().getMethod("dimension").invoke(handle);if(key.getClass().getMethod("location").invoke(key).toString().equals(value))return w;}catch(ReflectiveOperationException ignored){}}return null;}
    private void info(CommandSender sender,String sub,String[] x) throws Exception {
        Player p=player(sender);
        if(sub.equals("marker") || sub.equals("remove-marker")){
            require(x.length>2,"/estateinfo marker володіння trade|capital назва; /estateinfo remove-marker володіння trade|capital");String id=estate(x[1]),kind=x[2].toLowerCase(Locale.ROOT);s.control(actor(p),s.estate(id));require(Set.of("trade","capital").contains(kind),"Тип позначки: trade або capital.");
            s.db.transaction(()->{if(sub.equals("remove-marker"))s.db.execute("DELETE FROM map_points WHERE estate=? AND kind=?",id,kind);else{
                require(x.length>=4,"Укажіть назву позначки.");String name=validName(String.join(" ",Arrays.copyOfRange(x,3,x.length)));var t=TownyAPI.getInstance().getTown(p.getLocation());require(t!=null,"Позначка має стояти у приваті вашого володіння.");String cursor=TownyBridge.townId(t);boolean owns=false;for(int i=0;cursor!=null && i<16;i++,cursor=s.parent(cursor))if(cursor.equals(id))owns=true;require(owns,"Місце позначки не належить цьому володінню.");var l=p.getLocation();s.db.execute("MERGE INTO map_points KEY(estate,kind) VALUES(?,?,?,?,?,?,?)",id,kind,p.getWorld().getName(),l.getBlockX(),l.getBlockY(),l.getBlockZ(),name);
            }s.audit(actor(p),"MAP_POINT",sub+" "+id+" "+kind);return null;});say(p,"Позначку оновлено. Мапа синхронізується автоматично.");return;
        }
        String id;
        if(sub.equals("at")){require(x.length==4,"/estateinfo at вимір x z");World w=dimension(x[1]);require(w!=null,"Світ не знайдено.");int px=Integer.parseInt(x[2]),pz=Integer.parseInt(x[3]);require(Math.abs((long)px)<=30_000_000 && Math.abs((long)pz)<=30_000_000,"Координати за межами світу.");var t=TownyAPI.getInstance().getTown(new Location(w,px,64,pz));require(t!=null,"У цій точці немає володіння Towny.");id=TownyBridge.townId(t);}
        else if(x.length==0){var t=TownyAPI.getInstance().getTown(p.getLocation());require(t!=null,"Ви поза володінням. /estateinfo назва");id=TownyBridge.townId(t);}
        else id=estate(x[0]);
        InfoView view=new InfoView();int n=0;Set<String> seen=new HashSet<>();
        for(String cursor=id;cursor!=null && n<27 && seen.add(cursor);cursor=s.parent(cursor)){
            Estate e=s.estate(cursor);String parent=s.parent(cursor);ItemStack book=new ItemStack(e.kind()==Kind.KINGDOM?Material.GOLDEN_HELMET:Material.BOOK);var meta=book.getItemMeta();meta.setDisplayName("§6"+e.kind().label+": "+e.name());
            var lore=new ArrayList<>(List.of("§fПравитель: "+towny.playerName(e.lord()),"§fСюзерен: "+(parent==null?"Незалежне":s.estate(parent).name()),"§fДипломатія держави:"));
            String nation=e.nation();if(nation!=null){for(var treaty:realm.treaties(nation))if("ACTIVE".equals(string(treaty,"status")) && number(treaty,"ends")>s.now()){String other=string(treaty,"a").equals(nation)?string(treaty,"b"):string(treaty,"a");lore.add("§7"+string(treaty,"kind")+": "+s.estate(other).name());}if(lore.size()==3)lore.add("§7Немає чинних договорів");}else lore.add("§7Немає держави");
            var h=realm.heir(cursor);if(h!=null && e.lord().equals(string(h,"lord")))lore.add("§fСпадкоємець: "+towny.playerName(string(h,"heir"))+" ("+(bool(h,"accepted")?"погодився":"очікується")+")");meta.setLore(lore);book.setItemMeta(meta);view.inventory.setItem(n++,book);
        }p.openInventory(view.inventory);
    }
    public List<Map<String,Object>> mapEstates() throws Exception {
        var result=new ArrayList<Map<String,Object>>();for(Estate e:s.estates())if(e.active()){
            String parent=s.parent(e.id());var data=new LinkedHashMap<String,Object>();data.put("id",e.id());data.put("name",e.name());data.put("kind",e.kind().name());data.put("level",e.kind().level);data.put("parent",parent==null?"":parent);
            data.put("detail",e.kind().label+" "+e.name()+" · Правитель: "+towny.playerName(e.lord())+" · Сюзерен: "+(parent==null?"Незалежне":s.estate(parent).name())+" · "+(e.nation()==null?"":realm.relations(e.nation())));result.add(Map.copyOf(data));
        }return List.copyOf(result);
    }
    public List<Map<String,Object>> mapPoints() throws Exception {return s.db.query("SELECT p.* FROM map_points p JOIN estates e ON p.estate=e.id WHERE e.active=TRUE").stream().map(Map::copyOf).toList();}
    @EventHandler public void join(PlayerJoinEvent e){realm.activity(e.getPlayer().getUniqueId().toString());}
    @EventHandler public void quit(PlayerQuitEvent e){realm.activity(e.getPlayer().getUniqueId().toString());}
    @EventHandler public void click(InventoryClickEvent e){if(e.getView().getTopInventory().getHolder() instanceof InfoView)e.setCancelled(true);}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof InfoView)e.setCancelled(true);}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] x){
        List<String> options=switch(command.getName()){
            case "diplomacy" -> List.of("offer","sign","show","list","break","history");case "order" -> List.of("create","list","mine","show","deliver","collect","cancel");case "dynasty" -> List.of("name","nominate","accept","revoke","abdicate","show","history");case "estateinfo" -> List.of("marker","remove-marker");default -> sender.hasPermission("empirecraft.feudal.admin")?List.of("pending","inspect","recover","abort"):List.of("pending");};
        try{if(x.length==1)return options.stream().filter(v->v.startsWith(x[0].toLowerCase(Locale.ROOT))).toList();
            if(command.getName().equals("diplomacy") && x.length==4 && x[0].equals("offer"))return List.of("alliance","nonaggression","trade","passage");
            if(command.getName().equals("dynasty") && x.length==3 && x[0].equals("nominate"))return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(v->v.toLowerCase(Locale.ROOT).startsWith(x[2].toLowerCase(Locale.ROOT))).toList();
            if(command.getName().equals("order") && x.length==2 && x[0].equals("create"))return s.db.query("SELECT code FROM currencies ORDER BY code").stream().map(r->string(r,"code")).toList();
            if(x.length==2 && Set.of("dynasty","diplomacy","estateinfo").contains(command.getName()))return s.estates().stream().filter(Estate::active).map(e->e.id()).toList();
        }catch(Exception ignored){}return List.of();
    }
}
