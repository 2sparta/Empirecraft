package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Bukkit boundary: exact item plans, fresh physical checks, and explicit expiring confirmations. */
public final class MarketCommands implements CommandExecutor,TabCompleter,Listener {
    final FeudalPlugin plugin;final CityMarketService m;final ItemTransactions items;final TownyBridge towny;
    record Confirmation(String id,String[] args,String market,String spec,long tariff,long expires,String context) {}
    record Share(UUID sender,long expires) {}
    final Map<UUID,Confirmation> confirmations=new HashMap<>();final Map<UUID,Share> shares=new HashMap<>();
    final Map<UUID,Long> requestTimes=new HashMap<>();
    final Set<String> catalog;
    final MarketExtensionCommands extensions;
    MarketScreens screens;
    List<String> uiMessages;
    public MarketCommands(FeudalPlugin plugin,CityMarketService m,ItemTransactions items,TownyBridge towny){this.plugin=plugin;this.m=m;this.items=items;this.towny=towny;
        catalog=new HashSet<>(plugin.getConfig().getStringList("markets.catalog"));extensions=new MarketExtensionCommands(this);
        m.land=(town,world,x,z)->{var w=Bukkit.getWorld(UUID.fromString(world));if(w==null)return false;var t=TownyAPI.getInstance().getTown(new Location(w,x*16+.5,64,z*16+.5));return t!=null && TownyBridge.townId(t).equals(town);};
        m.buildingValid=this::buildingValid;m.buildingApproval=this::captureBuilding;
        m.counterRadius=Math.max(1,Math.min(16,plugin.getConfig().getDouble("markets.counter-radius",6)));m.maxClaims=Math.max(1,plugin.getConfig().getInt("markets.max-claims",64));m.maxDeposits=Math.max(1,plugin.getConfig().getInt("markets.max-deposits",200));
    }
    void captureBuilding(String market,Department d)throws Exception {
        var dep=m.department(market,d);World world=Bukkit.getWorld(UUID.fromString(string(m.market(market),"world")));require(world!=null,LocalText.tr("Вимір недоступний."));Box b=box(dep);List<Block> samples=new ArrayList<>();var random=new Random(0);int seen=0;
        for(int x=b.x1();x<=b.x2();x++)for(int z=b.z1();z<=b.z2();z++){
            require(world.isChunkLoaded(Math.floorDiv(x,16),Math.floorDiv(z,16)),LocalText.tr("Для огляду всі чанки будівлі мають бути завантажені."));
            for(int y=b.y1();y<=b.y2();y++){require(y>=world.getMinHeight()&&y<world.getMaxHeight(),LocalText.tr("Будівля виходить за висоту світу."));Block block=world.getBlockAt(x,y,z);if(block.getType().isAir()||block.isLiquid())continue;seen++;if(samples.size()<256)samples.add(block);else {int index=random.nextInt(seen);if(index<256)samples.set(index,block);}}
        }
        require(seen>=8,LocalText.tr("Порожня ділянка не є готовою будівлею."));
        require(world.getBlockAt((int)number(dep,"cx"),(int)number(dep,"cy"),(int)number(dep,"cz")).getType().name().equals(string(dep,"material")),LocalText.tr("Зареєстрованої стійки немає."));
        m.db.execute("DELETE FROM cm_structure WHERE market=? AND kind=?",market,d.name());for(Block block:samples)m.db.execute("INSERT INTO cm_structure VALUES(?,?,?,?,?,?)",market,d.name(),block.getX(),block.getY(),block.getZ(),block.getType().name());
    }
    boolean buildingValid(String market,Department d){try {
        var dep=m.department(market,d);World world=Bukkit.getWorld(UUID.fromString(string(m.market(market),"world")));if(world==null)return false;int x=(int)number(dep,"cx"),y=(int)number(dep,"cy"),z=(int)number(dep,"cz");
        if(world.isChunkLoaded(Math.floorDiv(x,16),Math.floorDiv(z,16))&&!world.getBlockAt(x,y,z).getType().name().equals(string(dep,"material")))return false;
        var samples=m.db.query("SELECT * FROM cm_structure WHERE market=? AND kind=?",market,d.name());int missing=0;
        for(var sample:samples){int sx=(int)number(sample,"x"),sy=(int)number(sample,"y"),sz=(int)number(sample,"z");if(world.isChunkLoaded(Math.floorDiv(sx,16),Math.floorDiv(sz,16))&&world.getBlockAt(sx,sy,sz).getType().isAir())missing++;}
        return samples.isEmpty()||missing*2<samples.size();
    }catch(Exception ex){throw new IllegalStateException(ex);}}
    static Actor actor(Player p){return new Actor(p.getUniqueId().toString(),p.hasPermission("empirecraft.feudal.admin"));}
    static Place place(Player p){var l=p.getLocation();return new Place(p.getWorld().getUID().toString(),l.getX(),l.getY(),l.getZ());}
    void say(CommandSender p,String text){if(uiMessages!=null)uiMessages.add(text);else plugin.message(p,text);}
    static String arg(String[] a,int i){require(a.length>i,LocalText.tr("Недостатньо аргументів. /market help"));return a[i];}
    static long numberArg(String[] a,int i){String n=arg(a,i);require(n.matches("[0-9]{1,15}"),LocalText.tr("Потрібне ціле додатне число."));return Long.parseLong(n);}
    static int intArg(String[] a,int i){return Math.toIntExact(numberArg(a,i));}
    static int coord(String[] a,int i){String n=arg(a,i);require(n.matches("-?[0-9]{1,8}"),LocalText.tr("Некоректна координата."));return Integer.parseInt(n);}
    static String code(String[] a,int i){return arg(a,i).toUpperCase(Locale.ROOT);}
    static boolean flag(String[] a,int i){String value=arg(a,i);require(Set.of("true","false").contains(value),LocalText.tr("Вкажіть true або false."));return Boolean.parseBoolean(value);}
    static Department dept(String text){try{return Department.valueOf(text.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException ex){throw new IllegalArgumentException(LocalText.tr("Відділ: GOODS, FX, JOBS, CREDIT."));}}
    String current(Player p)throws Exception {
 try(var languageScope=LocalText.use(p)){
var l=place(p);var row=m.db.one("SELECT market FROM cm_claims WHERE world=? AND x=? AND z=?",l.world(),l.cx(),l.cz());require(row!=null,LocalText.tr("Ви поза ринком. Для власних активів: /market assets; для керування вкажіть ID ринку."));return string(row,"market");
 }
}
    String spec(Player p)throws Exception {
 try(var languageScope=LocalText.use(p)){

        ItemStack item=p.getInventory().getItemInMainHand();require(!ItemTransactions.empty(item),LocalText.tr("Тримайте предмет у головній руці для точної специфікації."));item=item.clone();item.setAmount(1);
        require(!(item.getItemMeta() instanceof BlockStateMeta) && item.getType()!=Material.BUNDLE && !item.getType().name().contains("SHULKER") && item.getType()!=Material.FILLED_MAP,LocalText.tr("Контейнери та предмети із зовнішнім вмістом у першій версії недоступні."));
        boolean unique=item.getMaxStackSize()==1;
        require(unique || catalog.contains(item.getType().getKey().toString()),LocalText.tr("Цього ресурсу немає в дозволеному каталозі ринку."));
        return m.registerSpec(GoldInventoryBridge.encode(new ItemStack[]{item}),unique);

 }
}
    CommerceService.InventoryChange take(Player p,String spec,long n)throws Exception {var before=p.getInventory().getStorageContents();return items.change(p,before,ItemTransactions.take(before,ItemTransactions.prototype(string(m.spec(spec),"item")),n));}
    CommerceService.InventoryChange give(Player p,String spec,long n)throws Exception {var before=p.getInventory().getStorageContents();return items.change(p,before,ItemTransactions.add(before,ItemTransactions.prototype(string(m.spec(spec),"item")),n));}
    void apply(String id)throws Exception {var r=m.db.one("SELECT status FROM item_ops WHERE id=?",id);if(r!=null&&"PREPARED".equals(string(r,"status")))items.apply(id);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
 try(var languageScope=LocalText.use(sender)){

        try {args=CommandLanguage.normalize("market",args);require(sender instanceof Player,LocalText.tr("Команда доступна гравцю на сервері."));Player p=(Player)sender;require(p.hasPermission("empirecraft.feudal.use"),LocalText.tr("Недостатньо прав."));towny.sync();
            require(args.length<=80 && String.join(" ",args).length()<=8000,LocalText.tr("Завеликий запит."));String sub=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            args=args.clone();if(args.length>0)args[0]=sub;
            long previous=requestTimes.getOrDefault(p.getUniqueId(),0L);require(m.s.now()-previous>=250,LocalText.tr("Зачекайте перед наступним запитом ринку."));requestTimes.put(p.getUniqueId(),m.s.now());
            if(sub.equals("confirm")){var c=confirmations.get(p.getUniqueId());require(c!=null && c.expires>m.s.now() && c.id.equals(arg(args,1)),LocalText.tr("Підтвердження прострочене; відкрийте дію повторно."));var done=m.db.one("SELECT result FROM cm_actions WHERE id=? AND actor=?",c.id,p.getUniqueId().toString());if(done!=null){say(p,LocalText.tr("Дію вже зафіксовано. ID: ")+string(done,"result"));return true;}require(c.context.equals(context(p,c.args)),LocalText.tr("Умови або стан договору змінилися. Відкрийте дію повторно й перегляньте їх."));mutate(p,c);return true;}
            if(extensions.handle(p,args))return true;
            if(read(p,sub,args))return true;
            require(Set.of("buy","sell","cancel","reduce","load","recover","exchange","manage","jobs","contract","loan","admin","founding","agreement").contains(sub),LocalText.tr("Невідома команда. /market help"));
            String market="",sp="";long tariff=0;
            if(Set.of("buy","sell","jobs").contains(sub)||sub.equals("exchange")&&Set.of("buy","sell").contains(arg(args,1))) {market=current(p);tariff=number(m.tariff(market),"version");}
            else if(sub.equals("admin"))market=arg(args,2);
            else if(sub.equals("manage"))market=arg(args,1);
            else if(sub.equals("exchange")&&arg(args,1).equals("permit"))market=arg(args,2);
            else if(sub.equals("contract"))market=string(m.contracts.contract(arg(args,1)),"market");
            else if(Set.of("cancel","reduce").contains(sub))market=string(m.order(arg(args,1)),"market");
            else if(Set.of("load","recover").contains(sub))market=string(m.ownAsset(actor(p),arg(args,1)),"market");
            else if(sub.equals("loan")){String mode=arg(args,1);if(Set.of("license","offer","deposit","withdraw","license_apply").contains(mode))market=arg(args,2);else if(!Set.of("apply","offer_close").contains(mode))market=string(m.credit.loan(arg(args,2)),"market");else {var offer=m.db.one("SELECT market FROM cm_offers WHERE id=?",arg(args,2));require(offer!=null,LocalText.tr("Пропозицію не знайдено."));market=string(offer,"market");}}
            else if(sub.equals("founding")&&arg(args,1).equals("submit"))market=arg(args,2);
            if(sub.equals("buy")&&args.length>5){sp=args[5];m.spec(sp);}
            else if(Set.of("buy","sell").contains(sub)||sub.equals("jobs")&&arg(args,1).equals("supply")||sub.equals("loan")&&Set.of("license","offer").contains(arg(args,1)))sp=spec(p);
            if(sub.equals("jobs")&&arg(args,1).equals("work"))market=current(p);
            var c=new Confirmation(id(),args.clone(),market,sp,tariff,m.s.now()+60000,context(p,args));preview(p,c);confirmations.put(p.getUniqueId(),c);
            say(p,LocalText.tr("Підтвердити протягом 60 секунд: /market confirm ")+c.id+LocalText.tr(". Недоставлена пошта не зупиняє строки."));
        }catch(IllegalArgumentException|ArithmeticException ex){say(sender,"§c"+ex.getMessage());}
        catch(Exception ex){plugin.failure(LocalText.tr("Міський ринок"),ex);say(sender,LocalText.tr("§cДію не завершено. Якщо є передача предметів: /commerce pending."));}return true;

 }
}
    String context(Player p,String[] a)throws Exception {
        if(Set.of("buy","sell","jobs").contains(a[0]) || a[0].equals("exchange")&&a.length>1&&Set.of("buy","sell").contains(a[1])) {
            String market=current(p);return hash(m.market(market)+"|"+m.tariff(market)+"|"+m.s.membershipStamp(p.getUniqueId().toString())+"|"+m.db.query("SELECT id,active_version,pending_version,terminate_at FROM cm_treaties")+"|"+m.db.query("SELECT id,version,state FROM cm_fairs WHERE market=?",market)+"|"+String.join("|",a));
        }
        if(a[0].equals("contract"))return hash(m.agreements.fingerprint("WORK",arg(a,1))+"|"+m.s.membershipStamp(p.getUniqueId().toString())+"|"+m.db.query("SELECT * FROM cm_treaties ORDER BY id"));
        if(a[0].equals("loan")&&Set.of("approve","take","repay","foreclose","extend","cancel","reject").contains(arg(a,1)))return hash(m.credit.loan(arg(a,2)).toString());
        if(Set.of("load","recover").contains(a[0]))return hash(m.ownAsset(actor(p),arg(a,1)).toString());
        if(a[0].equals("agreement"))return hash(m.agreements.proposal(actor(p),arg(a,2)).toString());
        return "";
    }
    void preview(Player p,Confirmation c)throws Exception {
 try(var languageScope=LocalText.use(p)){

        String[] a=c.args;String sub=a[0];say(p,LocalText.tr("Підтвердження дії: /market ")+String.join(" ",a));
        if(!c.market.isEmpty())say(p,LocalText.tr("Ринок: ")+string(m.market(c.market),"name")+" ["+c.market+LocalText.tr("]; місце отримання — цей ринок."));
        if(sub.equals("buy")||sub.equals("sell")||sub.equals("exchange")&&arg(a,1).matches("buy|sell")){
            boolean fx=sub.equals("exchange"),buy=fx?arg(a,1).equals("buy"):sub.equals("buy");long quantity=numberArg(a,fx?4:1),price=money(arg(a,fx?5:3));String currency=code(a,fx?3:2);int days=intArg(a,fx?6:4);var t=m.tariff(c.market);long value=multiply(quantity,price);int bps=m.privilege(actor(p),c.market,fx?Department.FX:Department.GOODS,c.spec,buy?"buyer":"seller",value,m.s.now()+days*FeudalService.DAY,currency).bps();
            m.access(c.market,fx?Department.FX:Department.GOODS,place(p));say(p,(fx?LocalText.tr("Валюта ")+code(a,2):LocalText.tr("Предмет ")+ItemTransactions.prototype(string(m.spec(c.spec),"item")))+LocalText.tr("; кількість ")+quantity+LocalText.tr("; ліміт за одиницю ")+format(price)+" "+currency+LocalText.tr("; строк ")+days+LocalText.tr(" діб."));
            say(p,LocalText.tr("Тариф v")+c.tariff+": "+bps+LocalText.tr(" б.п.; збір ")+(buy?LocalText.tr("до "):LocalText.tr("за лімітною ціною "))+format(fee(value,bps))+" "+currency+"; "+(buy?LocalText.tr("резерв до ")+format(Math.addExact(value,fee(value,bps))):LocalText.tr("виручка за лімітною ціною ")+format(value-fee(value,bps)))+" "+currency+LocalText.tr(". Фактична ціна може бути кращою."));
            say(p,m.estimate(actor(p),c.market,fx?Department.FX:Department.GOODS,c.spec,fx?code(a,2):"",currency,buy,quantity,price,bps));
        }else if(sub.equals("loan")&&arg(a,1).equals("take")){var r=m.credit.loan(arg(a,2));require(p.getUniqueId().toString().equals(string(r,"borrower")),LocalText.tr("Чужа позика."));say(p,LocalText.tr("Отримання ")+format(number(r,"principal"))+" "+string(r,"code")+LocalText.tr("; повернення ")+format(number(r,"principal")+number(r,"interest"))+LocalText.tr("; строк ")+number(r,"days")+LocalText.tr(" діб від видачі; пільга 24 години; застава ")+number(r,"collateral")+" × "+ItemTransactions.prototype(string(m.spec(string(r,"spec")),"item")));say(p,string(r,"terms"));
        }else if(sub.equals("jobs")&&Set.of("work","supply").contains(arg(a,1))){var t=m.tariff(c.market);long reward=money(arg(a,3));say(p,LocalText.tr("Резерв ")+format(reward)+" "+code(a,2)+LocalText.tr("; збір виконавця ")+number(t,"jobs")+LocalText.tr(" б.п. від накопиченої виплати; тариф v")+c.tariff+LocalText.tr("; строк ")+intArg(a,4)+LocalText.tr(" діб."));
        }else if(sub.equals("contract")){var r=m.contracts.contract(arg(a,1));contractView(p,r,arg(a,2).equals("recover"));showContract(p,r);if(Set.of("accept","deliver","recover").contains(arg(a,2))){var rate=m.contracts.serviceRate(actor(p),r,number(r,"reward")-number(r,"paid"));say(p,LocalText.tr("Збір виконавця за чинною пільгою: ")+rate.bps()+LocalText.tr(" б.п.; не вище збереженого тарифу контракту. Після прийняття умов вища ставка не застосовується."));}}
        else if(sub.equals("agreement")){showAgreement(p,m.agreements.proposal(actor(p),arg(a,2)));}
        else if(sub.equals("load")||sub.equals("recover")){var r=m.ownAsset(actor(p),arg(a,1));say(p,LocalText.tr("Актив ринку ")+string(r,"market")+LocalText.tr("; відділ ")+string(r,"department")+"; "+(string(r,"code").isEmpty()?LocalText.tr("предметів ")+number(r,"quantity"):format(number(r,"quantity"))+" "+string(r,"code"))+LocalText.tr("; без збору за отримання."));}
        if(!c.spec.isEmpty())say(p,LocalText.tr("Специфікація предмета: ")+c.spec+LocalText.tr(". Зберігаються всі властивості."));

 }
}
    boolean read(Player p,String sub,String[] a)throws Exception {
 try(var languageScope=LocalText.use(p)){

        String person=p.getUniqueId().toString();
        switch(sub){
            case "menu" -> {menu(p);return true;}
            case "help" -> {help(p);return true;}
            case "list","info" -> {var rows=sub.equals("info")?List.of(m.market(a.length>1?a[1]:current(p))):m.db.query("SELECT * FROM cm_markets ORDER BY name");for(var r:rows){say(p,string(r,"id")+" · "+string(r,"name")+" · "+string(r,"state")+" · "+string(r,"world")+" · "+string(r,"description"));for(var c:m.db.query("SELECT x,z FROM cm_claims WHERE market=?",string(r,"id")))say(p,LocalText.tr("Чанк ")+number(c,"x")+", "+number(c,"z"));if(sub.equals("info")){for(Department d:Department.values()){var dep=m.department(string(r,"id"),d);say(p,d+": "+(dep==null?LocalText.tr("🔒 Не побудований"):string(dep,"state")+" "+box(dep)+LocalText.tr("; стійка ")+number(dep,"cx")+","+number(dep,"cy")+","+number(dep,"cz")));}say(p,LocalText.tr("Тарифи: ")+m.db.query("SELECT * FROM cm_tariffs WHERE market=? ORDER BY version",string(r,"id")));}}return true;}
            case "assets","load" -> {if(sub.equals("load")&&a.length>1)return false;var rows=m.db.query("SELECT * FROM cm_assets WHERE owner=? AND quantity>0 ORDER BY market,department","p:"+person);for(var r:rows)say(p,string(r,"id")+LocalText.tr(" · ринок ")+string(r,"market")+" · "+string(r,"department")+" · "+string(r,"category")+" · "+(string(r,"code").isEmpty()?number(r,"quantity")+" × "+ItemTransactions.prototype(string(m.spec(string(r,"spec")),"item")):format(number(r,"quantity"))+" "+string(r,"code")));if(rows.isEmpty())say(p,LocalText.tr("Активів до отримання немає."));say(p,LocalText.tr("Отримати: /market load ID [кількість]; за недоступного відділу: /market recover ID [кількість]."));return true;}
            case "orders" -> {for(var r:m.db.query("SELECT * FROM cm_orders WHERE owner=? ORDER BY priority DESC LIMIT 50",person))say(p,string(r,"id")+" · "+string(r,"market")+" · "+string(r,"side")+" · "+number(r,"remaining")+" @"+format(number(r,"price"))+" "+string(r,"code")+" · "+string(r,"state"));return true;}
            case "book" -> {String market=current(p);m.access(market,Department.GOODS,place(p));var rows=m.db.query("SELECT id,side,spec,code,remaining,price FROM cm_orders WHERE market=? AND department='GOODS' AND state='OPEN' AND ends>? ORDER BY side,price,priority LIMIT 100",market,m.s.now());for(var r:rows)say(p,string(r,"id")+" · "+(string(r,"side").equals("BUY")?LocalText.tr("Купівля"):LocalText.tr("Продаж"))+" · "+number(r,"remaining")+" × "+ItemTransactions.prototype(string(m.spec(string(r,"spec")),"item")).getType()+" · "+format(number(r,"price"))+" "+string(r,"code")+LocalText.tr(" за предмет"));if(rows.isEmpty())say(p,LocalText.tr("Немає пропозицій."));say(p,LocalText.tr("Перегляд точного предмета: /market inspect_lot ID"));return true;}
            case "inspect_lot" -> {var o=m.order(arg(a,1));require("GOODS".equals(string(o,"department"))&&"OPEN".equals(string(o,"state")),LocalText.tr("Лот недоступний."));m.access(string(o,"market"),Department.GOODS,place(p));require(screens!=null,LocalText.tr("Графічний міст запускається."));screens.open(p,string(o,"spec"));say(p,LocalText.tr("Лот ")+a[1]+LocalText.tr("; кількість ")+number(o,"remaining")+"; "+format(number(o,"price"))+" "+string(o,"code")+LocalText.tr(" за одиницю; купівля за точною специфікацією: /market buy 1 ")+string(o,"code")+" "+format(number(o,"price"))+" 1 "+string(o,"spec"));return true;}
            case "exchange" -> {
                String mode=a.length>1?a[1]:"rates";if(Set.of("buy","sell","permit").contains(mode))return false;
                if(mode.equals("permits")){say(p,LocalText.tr("Допуск емітента: /market exchange permit ID_ринку КОД issuer true|false; згода міста: /market manage ID_ринку host КОД true|false."));return true;}
                if(mode.equals("orders")){var rows=m.book(current(p),Department.FX,place(p));for(var r:rows)say(p,string(r,"base")+"/"+string(r,"code")+" · "+(string(r,"side").equals("BUY")?LocalText.tr("Купівля"):LocalText.tr("Продаж"))+" · "+format(number(r,"price"))+" "+string(r,"code")+LocalText.tr(" за 1 ")+string(r,"base")+LocalText.tr(" · обсяг ")+number(r,"quantity")+" "+string(r,"base"));if(rows.isEmpty())say(p,LocalText.tr("Немає пропозицій."));return true;}
                if(mode.equals("inverse")){var rows=m.book(current(p),Department.FX,place(p));for(var r:rows){var inverse=java.math.BigDecimal.valueOf(100).divide(java.math.BigDecimal.valueOf(number(r,"price")),12,java.math.RoundingMode.DOWN);say(p,string(r,"code")+"/"+string(r,"base")+" · "+(string(r,"side").equals("BUY")?"SELL":"BUY")+" · 1 "+string(r,"code")+" ≈ "+inverse.toPlainString()+" "+string(r,"base")+LocalText.tr(" · обсяг ")+format(multiply(number(r,"quantity"),number(r,"price")))) ;}if(rows.isEmpty())say(p,LocalText.tr("Немає пропозицій."));say(p,LocalText.tr("Це зворотний показ тієї самої книги; заявки подаються в основній парі."));return true;}
                if(mode.equals("history")||mode.equals("chart")){String market=current(p);m.access(market,Department.FX,place(p));long hours=a.length>2?numberArg(a,2):24;require(Set.of(1L,24L,168L,720L).contains(hours),LocalText.tr("Період: 1, 24, 168 або 720 годин."));chart(p,market,hours);return true;}
                try {String market=current(p);m.observe(actor(p),market,place(p));}catch(IllegalArgumentException unavailable) { /* Outside: only persisted knowledge. */ }
                var rows=m.db.query("SELECT * FROM cm_snapshots WHERE person=? ORDER BY observed DESC",person);for(var r:rows)say(p,string(r,"market")+" · "+string(r,"data")+LocalText.tr(" · спостереження ")+time(number(r,"observed"))+LocalText.tr(" · давність ")+((m.s.now()-number(r,"observed"))/60000)+LocalText.tr(" хв; поточні умови можуть відрізнятися."));if(rows.isEmpty())say(p,LocalText.tr("Курс невідомий. Відвідайте ринок з відкритим Валютним відділом."));return true;
            }
            case "jobs" -> {if(a.length>1)return false;String market=current(p);m.access(market,Department.JOBS,place(p));for(var r:m.db.query("SELECT * FROM cm_contracts WHERE market=? AND state='FUNDED'",market))showContract(p,r);return true;}
            case "contract" -> {if(a.length>2)return false;if(a.length==1){for(var r:m.db.query("SELECT id,market,kind,state FROM cm_contracts WHERE customer=? OR worker=?",person,person))say(p,r.toString());return true;}var r=m.contracts.contract(a[1]);contractView(p,r,false);showContract(p,r);return true;}
            case "loan" -> {String mode=a.length>1?a[1]:"mine";if(mode.equals("offers")){String market=current(p);m.access(market,Department.CREDIT,place(p));for(var r:m.db.query("SELECT o.* FROM cm_offers o JOIN cm_licenses l ON l.market=o.market AND l.lender=o.lender AND l.code=o.code WHERE o.market=? AND o.state='OPEN' AND l.active=TRUE",market))say(p,string(r,"id")+LocalText.tr(" · кредитор ")+towny.playerName(string(r,"lender"))+LocalText.tr(" · видача ")+format(number(r,"principal"))+" "+string(r,"code")+LocalText.tr(" · повернення ")+format(number(r,"principal")+fee(number(r,"principal"),(int)number(r,"rate")))+" "+string(r,"code")+LocalText.tr(" · строк ")+number(r,"days")+LocalText.tr(" діб · застава ")+number(r,"collateral")+" × "+ItemTransactions.prototype(string(m.spec(string(r,"spec")),"item")).getType());return true;}
                if(mode.equals("license_requests")){String market=arg(a,2);m.manage(actor(p),market);for(var r:m.db.query("SELECT * FROM cm_license_requests WHERE market=?",market))say(p,r.toString());return true;}
                if(mode.equals("mine")){for(var r:m.db.query("SELECT * FROM cm_loans WHERE borrower=? OR lender=?",person,person))showLoan(p,r);return true;}
                if(mode.equals("applications")){for(var r:m.db.query("SELECT * FROM cm_loans WHERE lender=? AND state='REQUESTED'",person))showLoan(p,r);return true;}
                if(mode.equals("funds")){for(var r:m.db.query("SELECT * FROM cm_funds WHERE lender=?",person))say(p,string(r,"market")+" · "+string(r,"code")+LocalText.tr(" · вільно ")+format(m.s.balance(account(string(r,"id")),string(r,"code"))));return true;}return false;}
            case "history" -> {for(var r:m.db.query("SELECT * FROM cm_events WHERE actor=? ORDER BY seq DESC LIMIT 40",person))say(p,r.toString());return true;}
            case "agreement" -> {if(a.length>1&&a[1].equals("accept"))return false;showAgreement(p,m.agreements.proposal(actor(p),arg(a,1)));return true;}
            case "mail" -> {say(p,LocalText.tr("Contact вимкнений: API власності ящика, екземпляра та підтвердження доставки ще не перевірено. Економіка працює незалежно; строки чинні без пошти."));return true;}
            case "share_info" -> {if(a.length>1&&a[1].equals("accept")){var share=shares.get(p.getUniqueId());require(share!=null&&share.expires>m.s.now(),LocalText.tr("Немає чинного запиту."));Player from=Bukkit.getPlayer(share.sender);near(p,from);String updated=m.share(actor(from),id(),person);shares.remove(p.getUniqueId());say(p,LocalText.tr("Оновлено знімків: ")+updated);say(from,LocalText.tr("Оновлено знімків: ")+updated);return true;}Player to=Bukkit.getPlayerExact(arg(a,1));near(p,to);require(!to.equals(p),LocalText.tr("Оберіть іншого гравця."));require(!shares.containsKey(to.getUniqueId())||shares.get(to.getUniqueId()).expires<m.s.now(),LocalText.tr("Гравець уже має запит."));shares.put(to.getUniqueId(),new Share(p.getUniqueId(),m.s.now()+30000));say(to,p.getName()+LocalText.tr(" пропонує відомі курси. Прийняти поруч: /market share_info accept"));say(p,LocalText.tr("Запит надіслано на 30 секунд."));return true;}
            case "founding" -> {if(a.length>1&&a[1].equals("status")){for(var r:m.db.query("SELECT id,name,state,reason FROM cm_markets WHERE town=?",town(p)))say(p,r.toString());return true;}return false;}
            case "manage" -> {if(a.length>2)return false;String market=a.length>1?a[1]:current(p);m.manage(actor(p),market);say(p,LocalText.tr("Ринок ")+market+LocalText.tr("; /market manage ID tariff покупець_бп продавець_бп послуги_бп; payment КОД true|false; staff гравець true|false."));return true;}
            case "admin" -> {if(a.length>1&&a[1].equals("inspect")){m.server(actor(p));String market=arg(a,2);say(p,m.market(market).toString());for(var r:m.db.query("SELECT * FROM cm_departments WHERE market=?",market))say(p,r.toString());for(var r:m.db.query("SELECT * FROM cm_events WHERE market=? ORDER BY seq DESC LIMIT 30",market))say(p,r.toString());return true;}return false;}
            default -> {return false;}
        }

 }
}
    void showLoan(Player p,Map<String,Object> r){
 try(var languageScope=LocalText.use(p)){

        String state=string(r,"state");long remaining=Set.of("REPAID","SETTLED","CANCELLED").contains(state)?0:number(r,"principal")+number(r,"interest")-number(r,"paid");say(p,string(r,"id")+" · "+state+LocalText.tr(" · ринок ")+string(r,"market")+LocalText.tr(" · основна сума ")+format(number(r,"principal"))+" "+string(r,"code")+" · "+(Set.of("REQUESTED","APPROVED","PREPARED").contains(state)?LocalText.tr("погоджене повернення після видачі "):LocalText.tr("залишок боргу "))+format(remaining)+" "+string(r,"code")+LocalText.tr(" · дата платежу ")+time(number(r,"due"))+LocalText.tr(" · схвалення до ")+time(number(r,"approved_until")));

 }
}
    void showContract(Player p,Map<String,Object> r)throws Exception {
 try(var languageScope=LocalText.use(p)){

        String id=string(r,"id");say(p,id+" · "+string(r,"kind")+" · "+string(r,"state")+" · "+string(r,"description"));say(p,LocalText.tr("Ринок ")+string(r,"market")+LocalText.tr(" · повна винагорода ")+format(number(r,"reward"))+" "+string(r,"code")+LocalText.tr(" · вже прийнято оплату ")+format(number(r,"paid"))+LocalText.tr(" · збір ")+number(r,"bps")+LocalText.tr(" б.п. · строк ")+time(number(r,"ends")));
        for(var st:m.db.query("SELECT * FROM cm_stages WHERE contract=? ORDER BY stage",id))say(p,LocalText.tr("Етап ")+number(st,"stage")+" · "+format(number(st,"amount"))+" "+string(r,"code")+" · "+string(st,"state")+LocalText.tr(" · критерій: ")+string(st,"criteria")+LocalText.tr(" · докази: ")+string(st,"evidence"));

 }
}
    void contractView(Player p,Map<String,Object> r,boolean recovery)throws Exception {
 try(var languageScope=LocalText.use(p)){

        String person=p.getUniqueId().toString(),market=string(r,"market");if(person.equals(string(r,"customer"))||person.equals(string(r,"worker")))return;
        if(actor(p).admin()){m.db.transaction(()->{m.event(actor(p),market,"ADMIN_VIEW",string(r,"id"));return null;});return;}
        require("FUNDED".equals(string(r,"state"))&&m.inMarket(market,place(p)),LocalText.tr("Це чужий приватний контракт."));
        if(!recovery)m.access(market,Department.JOBS,place(p));else require(!m.open(market,Department.JOBS)&&m.db.one("SELECT seq FROM cm_events WHERE market=? AND event='RECOVERY_POINT'",market)!=null,LocalText.tr("Потрібен пункт повернення."));

 }
}
    void showAgreement(Player p,Map<String,Object> r)throws Exception {
 try(var languageScope=LocalText.use(p)){

        String kind=string(r,"kind"),related=string(r,"related");var contract=kind.equals("LOAN")?m.credit.loan(related):m.contracts.contract(related);say(p,string(r,"id")+LocalText.tr(" · договір ")+related+LocalText.tr(" · ринок ")+string(contract,"market")+" · "+kind+" · "+string(r,"state")+" · "+string(r,"description"));
        if(kind.equals("TERMINATE"))say(p,LocalText.tr("Додаткова виплата виконавцю до збору: ")+format(number(r,"amount"))+" "+string(contract,"code")+LocalText.tr("; решта резерву повертається замовнику."));else say(p,LocalText.tr("Нова дата: ")+time(number(r,"deadline"))+LocalText.tr(". Грошові суми й застава не змінюються."));say(p,LocalText.tr("Підпис другої сторони потрібен до ")+time(number(r,"expires")));

 }
}
    String town(Player p){
 try(var languageScope=LocalText.use(p)){
var t=TownyAPI.getInstance().getTown(p);require(t!=null,LocalText.tr("Потрібне місто."));return TownyBridge.townId(t);
 }
}
    void near(Player a,Player b){
 try(var languageScope=LocalText.use(a)){
require(b!=null&&b.isOnline()&&a.getWorld().equals(b.getWorld())&&a.getLocation().distanceSquared(b.getLocation())<=64,LocalText.tr("Обидва гравці мають бути онлайн поруч (до 8 блоків) в одному вимірі."));
 }
}
    void mutate(Player p,Confirmation c)throws Exception {
 try(var languageScope=LocalText.use(p)){

        Actor who=actor(p);String req=c.id;String[] a=c.args;String sub=a[0],market=c.market,result;
        switch(sub){
            case "founding" -> {String mode=arg(a,1);if(mode.equals("create"))result=m.create(who,req,town(p),p.getWorld().getUID().toString(),arg(a,2).replace('_',' '),a.length>3?String.join(" ",Arrays.copyOfRange(a,3,a.length)):"");else {require(mode.equals("submit"),LocalText.tr("/market founding create назва [опис]; submit ID; status"));result=m.submit(who,req,arg(a,2));}}
            case "buy","sell" -> {long n=numberArg(a,1);result=m.order(who,req,market,place(p),Department.GOODS,c.spec,"",code(a,2),sub.equals("buy")?"BUY":"SELL",n,money(arg(a,3)),intArg(a,4),c.tariff,sub.equals("sell")?take(p,c.spec,n):null);apply(result);}
            case "cancel" -> result=m.cancel(who,req,arg(a,1));
            case "reduce" -> result=m.reduce(who,req,arg(a,1),numberArg(a,2));
            case "load","recover" -> {var r=m.ownAsset(who,arg(a,1));boolean recovery=sub.equals("recover");if(!string(r,"code").isEmpty())result=m.collectMoney(who,req,a[1],place(p),recovery);else {long n=a.length>2?numberArg(a,2):number(r,"quantity");result=m.collectItems(who,req,a[1],n,place(p),recovery,give(p,string(r,"spec"),n));apply(result);}}
            case "exchange" -> {String mode=arg(a,1);if(mode.equals("permit")){require(arg(a,4).equals("issuer"),LocalText.tr("Згода міста: /market manage ID host КОД true|false; тут потрібен issuer."));result=m.permit(who,req,arg(a,2),code(a,3),"issuer",flag(a,5));}
                else {require(Set.of("buy","sell").contains(mode),LocalText.tr("/market exchange buy|sell BASE QUOTE кількість_цілих_монет ціна дні"));result=m.order(who,req,market,place(p),Department.FX,"",code(a,2),code(a,3),mode.toUpperCase(Locale.ROOT),numberArg(a,4),money(arg(a,5)),intArg(a,6),c.tariff,null);}}
            case "manage" -> {market=arg(a,1);String mode=arg(a,2);result=switch(mode){case "tariff" -> m.tariff(who,req,market,intArg(a,3),intArg(a,4),intArg(a,5));case "payment","host" -> m.permit(who,req,market,code(a,3),mode,flag(a,4));case "staff" -> m.staff(who,req,market,towny.residentId(arg(a,3)),flag(a,4));default -> throw new IllegalArgumentException(LocalText.tr("Керування: tariff, payment, host, staff."));};}
            case "jobs" -> {String mode=arg(a,1);require(Set.of("work","supply").contains(mode),"/market jobs work|supply ...");String description;List<Long> amounts=new ArrayList<>();List<String> criteria=new ArrayList<>();
                if(mode.equals("work")){for(String st:arg(a,5).split(";")){String[] fields=st.split(":",2);require(fields.length==2,LocalText.tr("Етапи: сума:критерій;сума:критерій (пробіли замініть _)."));amounts.add(money(fields[0]));criteria.add(fields[1].replace('_',' '));}description=String.join(" ",Arrays.copyOfRange(a,6,a.length));}
                else description=String.join(" ",Arrays.copyOfRange(a,7,a.length));
                result=m.contracts.create(who,req,market,place(p),description,code(a,2),money(arg(a,3)),intArg(a,4),mode.equals("supply")?c.spec:"",mode.equals("supply")?numberArg(a,5):0,mode.equals("supply")&&flag(a,6),amounts,criteria,c.tariff);}
            case "contract" -> {String key=arg(a,1),mode=arg(a,2);result=switch(mode){
                case "accept" -> m.contracts.accept(who,req,key,place(p));
                case "deliver","recover" -> {var r=m.contracts.contract(key);String op=m.contracts.deliver(who,req,key,numberArg(a,3),place(p),mode.equals("recover"),take(p,string(r,"spec"),numberArg(a,3)));apply(op);yield op;}
                case "submit" -> m.contracts.submit(who,req,key,intArg(a,3),String.join(" ",Arrays.copyOfRange(a,4,a.length)));
                case "approve" -> m.contracts.review(who,req,key,true,LocalText.tr("Прийнято замовником"));
                case "dispute" -> m.contracts.dispute(who,req,key,String.join(" ",Arrays.copyOfRange(a,3,a.length)));
                case "cancel" -> m.contracts.cancel(who,req,key);
                case "arbitrate" -> m.contracts.arbitrate(who,req,key,money(arg(a,3)),String.join(" ",Arrays.copyOfRange(a,4,a.length)));
                case "amend" -> m.agreements.propose(who,req,"WORK",key,intArg(a,3),0,String.join(" ",Arrays.copyOfRange(a,4,a.length)));
                case "terminate" -> m.agreements.propose(who,req,"TERMINATE",key,0,money(arg(a,3)),String.join(" ",Arrays.copyOfRange(a,4,a.length)));
                default -> throw new IllegalArgumentException(LocalText.tr("Контракт: accept, deliver, submit, approve, dispute, cancel, arbitrate."));};}
            case "loan" -> {String mode=arg(a,1);result=switch(mode){
                case "license" -> m.credit.license(who,req,arg(a,2),towny.residentId(arg(a,3)),code(a,4),c.spec,money(arg(a,5)),money(arg(a,6)),intArg(a,7),intArg(a,8),intArg(a,9),flag(a,10));
                case "license_apply" -> m.credit.requestLicense(who,req,arg(a,2),place(p),code(a,3));
                case "deposit","withdraw" -> m.credit.fund(who,req,arg(a,2),place(p),code(a,3),money(arg(a,4)),mode.equals("deposit"));
                case "offer" -> m.credit.offer(who,req,arg(a,2),place(p),code(a,3),money(arg(a,4)),intArg(a,5),intArg(a,6),c.spec,numberArg(a,7));
                case "apply" -> m.credit.apply(who,req,arg(a,2),place(p));
                case "approve" -> m.credit.approve(who,req,arg(a,2),place(p));
                case "take" -> {var r=m.credit.loan(arg(a,2));String op=m.credit.take(who,req,a[2],place(p),take(p,string(r,"spec"),number(r,"collateral")));apply(op);yield op;}
                case "repay" -> m.credit.repay(who,req,arg(a,2),money(arg(a,3)));
                case "foreclose" -> m.credit.foreclose(who,req,arg(a,2));
                case "cancel","reject" -> m.credit.cancel(who,req,arg(a,2));
                case "offer_close" -> m.credit.closeOffer(who,req,arg(a,2));
                case "extend" -> m.agreements.propose(who,req,"LOAN",arg(a,2),intArg(a,3),0,LocalText.tr("Продовження строку без зміни основного боргу, відсотків і застави"));
                default -> throw new IllegalArgumentException(LocalText.tr("Кредит: license, deposit, withdraw, offer, offers, apply, approve, take, repay, foreclose, mine, funds."));};}
            case "agreement" -> {require(arg(a,1).equals("accept"),"/market agreement accept ID");result=m.agreements.accept(who,req,arg(a,2));}
            case "admin" -> {m.server(who);String mode=arg(a,1);market=arg(a,2);
                switch(mode){
                    case "claims" -> {require(Set.of("add","remove").contains(arg(a,3)),LocalText.tr("Дія з чанками: add або remove."));result=m.claim(who,req,market,coord(a,4),coord(a,5),arg(a,3).equals("add"),String.join(" ",Arrays.copyOfRange(a,6,a.length)));}
                    case "city_limit" -> result=m.cityLimit(who,req,market,intArg(a,3),String.join(" ",Arrays.copyOfRange(a,4,a.length)));
                    case "related" -> result=m.related(who,req,market,towny.residentId(arg(a,3)),towny.residentId(arg(a,4)),String.join(" ",Arrays.copyOfRange(a,5,a.length)));
                    case "department" -> {Department d=dept(arg(a,3));String action=arg(a,4);if(action.equals("building")){var b=new Box(coord(a,5),coord(a,6),coord(a,7),coord(a,8),coord(a,9),coord(a,10));Block counter=p.getTargetBlockExact(6);require(counter!=null && !counter.getType().isAir(),LocalText.tr("Дивіться на блок стійки в межах 6 блоків."));require(p.getWorld().getUID().toString().equals(string(m.market(market),"world")),LocalText.tr("Інший вимір."));result=m.building(who,req,market,d,b,counter.getX(),counter.getY(),counter.getZ(),counter.getType().name(),String.join(" ",Arrays.copyOfRange(a,11,a.length)));}
                        else {require(Set.of("approve","suspend").contains(action),LocalText.tr("Дія: building, approve, suspend."));if(action.equals("approve"))require(m.inMarket(market,place(p)) && box(m.department(market,d)).contains(place(p)),LocalText.tr("Огляньте будівлю особисто зсередини."));result=m.reviewDepartment(who,req,market,d,action.equals("approve"),String.join(" ",Arrays.copyOfRange(a,5,a.length)));}}
                    case "recovery" -> {String key=market;result=m.once(who,req,"recovery|"+key,()->{require(m.inMarket(key,place(p)),LocalText.tr("Пункт повернення — на початковій території."));m.event(who,key,"RECOVERY_POINT",place(p).toString());return key;});}
                    case "legacy" -> {String old=arg(a,3),key=market,reason=String.join(" ",Arrays.copyOfRange(a,4,a.length));require(!reason.isBlank(),LocalText.tr("Укажіть підставу підтвердження початкового місця зберігання."));result=m.once(who,req,"legacy|"+key+old+reason,()->{m.market(key);require(m.inMarket(key,place(p)),LocalText.tr("Повернення старих предметів — на підтвердженому місці їх зберігання."));var o=m.commerce.order(old);require(m.db.one("SELECT id FROM item_ops WHERE related=? AND status='PREPARED'",old)==null,LocalText.tr("Спершу завершіть передачу."));var done=m.db.one("SELECT status FROM cm_legacy WHERE old_id=?",old);require(done==null||!"ITEMS_RETURNED".equals(string(done,"status")),LocalText.tr("Предмети вже перенесені до обліку повернення."));String sp=m.registerSpec(string(o,"item"),false);String asset=m.asset(key,Department.GOODS,string(o,"owner"),sp,"",number(o,"unclaimed"),old,"LEGACY_RETURN");m.db.execute("UPDATE orders SET unclaimed=0 WHERE id=?",old);m.db.execute("MERGE INTO cm_legacy KEY(old_id) VALUES(?,?,'ITEMS_RETURNED')",old,asset);m.event(who,key,"LEGACY_RETURN",old+" location confirmed "+place(p)+" "+reason);return asset;});}
                    default -> {String state=switch(mode){case "approve" -> "OPEN";case "reject" -> "REVISION";case "suspend" -> "SUSPENDED";case "close" -> "CLOSED";case "archive" -> "ARCHIVED";default -> throw new IllegalArgumentException(LocalText.tr("Адміністрування: inspect, claims, department, approve, reject, suspend, close, archive, recovery, legacy."));};result=m.state(who,req,market,state,String.join(" ",Arrays.copyOfRange(a,3,a.length)));}
                }
            }
            default -> throw new IllegalArgumentException(LocalText.tr("Невідома команда. /market help"));
        }
        say(p,LocalText.tr("Дію зафіксовано. ID: ")+result);

 }
}
    void chart(Player p,String market,long hours)throws Exception {
 try(var languageScope=LocalText.use(p)){

        long start=m.s.now()-hours*3600000,step=Math.max(1,hours*3600000/24);var pairs=m.db.query("SELECT DISTINCT base,code FROM cm_fills WHERE market=? AND department='FX' AND at_time>=?",market,start);
        if(pairs.isEmpty()){say(p,LocalText.tr("За вибраний період угод немає; графік порожній."));return;}
        for(var pair:pairs){String base=string(pair,"base"),code=string(pair,"code");var ticks=m.db.query("SELECT quantity,price,at_time FROM cm_fills WHERE market=? AND department='FX' AND base=? AND code=? AND at_time>=? ORDER BY seq",market,base,code,start);long min=Long.MAX_VALUE,max=0,volume=0;Long[] values=new Long[24];
            for(var tick:ticks){int slot=(int)Math.min(23,Math.max(0,(number(tick,"at_time")-start)/step));long price=number(tick,"price");values[slot]=price;min=Math.min(min,price);max=Math.max(max,price);volume=Math.addExact(volume,number(tick,"quantity"));}
            String bars="▁▂▃▄▅▆▇█";StringBuilder plot=new StringBuilder();for(Long value:values)plot.append(value==null?'·':bars.charAt(max==min?3:(int)java.math.BigInteger.valueOf(value-min).multiply(java.math.BigInteger.valueOf(7)).divide(java.math.BigInteger.valueOf(max-min)).longValueExact()));
            say(p,base+"/"+code+" · "+hours+LocalText.tr(" год · ")+plot+LocalText.tr(" · · = без угод; остання ціна кожного проміжку"));say(p,LocalText.tr("Ціни ")+format(min)+"–"+format(max)+" "+code+LocalText.tr(" за 1 ")+base+LocalText.tr("; обсяг ")+volume+" "+base+LocalText.tr("; остання угода ")+time(number(ticks.get(ticks.size()-1),"at_time")));
        }

 }
}
    void menu(Player p)throws Exception {
 try(var languageScope=LocalText.use(p)){
require(screens!=null,LocalText.tr("Графічний міст ще запускається."));screens.open(p);
 }
}
    /** Registered counter loss is definite; unloading never calls this path. */
    void broken(Block block){try {for(var dep:m.db.query("SELECT d.* FROM cm_departments d JOIN cm_markets m ON m.id=d.market WHERE m.world=? AND d.cx=? AND d.cy=? AND d.cz=? AND d.state='OPEN'",block.getWorld().getUID().toString(),block.getX(),block.getY(),block.getZ()))m.reviewDepartment(new Actor("building-guard",true),id(),string(dep,"market"),dept(string(dep,"kind")),false,LocalText.tr("Знищено зареєстровану стійку"));}catch(Exception ex){plugin.failure(LocalText.tr("Будівля ринку"),ex);}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e){broken(e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explode(EntityExplodeEvent e){for(Block b:e.blockList())broken(b);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explode(BlockExplodeEvent e){for(Block b:e.blockList())broken(b);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void burn(BlockBurnEvent e){broken(e.getBlock());}
    public void tick(){
        confirmations.entrySet().removeIf(e->e.getValue().expires<m.s.now());shares.entrySet().removeIf(e->e.getValue().expires<m.s.now());requestTimes.entrySet().removeIf(e->e.getValue()<m.s.now()-60000);m.tick();
        try {for(Player p:Bukkit.getOnlinePlayers()){try{String market=current(p);if(m.open(market,Department.FX))m.observe(actor(p),market,place(p));}catch(IllegalArgumentException ignored){}}
            for(var dep:m.db.query("SELECT d.*,m.world FROM cm_departments d JOIN cm_markets m ON m.id=d.market WHERE d.state='OPEN'"))if(!buildingValid(string(dep,"market"),dept(string(dep,"kind"))))m.reviewDepartment(new Actor("building-guard",true),id(),string(dep,"market"),dept(string(dep,"kind")),false,LocalText.tr("Втрачено стійку або щонайменше половину контрольних блоків споруди"));
        }catch(Exception ex){plugin.failure(LocalText.tr("Огляд ринків"),ex);}
    }
    void help(Player p){
 try(var languageScope=LocalText.use(p)){

        say(p,LocalText.tr("/market menu; list; info [ID]; book; buy|sell кількість КОД ціна_за_предмет 1|3|7 (точний предмет у руці); orders; cancel ID; reduce ID залишок; load [актив] [кількість]; recover актив [кількість]."));
        say(p,LocalText.tr("/market exchange rates; orders; history 1|24|168|720; buy|sell BASE QUOTE кількість_цілих_монет ціна_в_QUOTE 1|3|7. BASE < QUOTE за абеткою; одна книга на пару. /market share_info гравець; share_info accept."));
        say(p,LocalText.tr("/market jobs supply КОД винагорода дні кількість true|false опис; jobs work КОД винагорода дні сума:критерій;сума:критерій опис. Предмет постачання в руці. /market contract [ID] [accept|deliver кількість|submit етап докази|approve|dispute причина|cancel|arbitrate сума причина]."));
        say(p,LocalText.tr("/market loan offers; mine; applications; funds; deposit|withdraw ринок КОД сума; offer ринок КОД сума ставка_бп дні кількість_застави (предмет у руці); apply|approve|take ID; repay ID сума; foreclose ID."));
        say(p,LocalText.tr("Мер: /market founding create назва [опис]; founding submit ID; manage ID; loan license ринок кредитор КОД макс_позика макс_борг макс_ставка_бп макс_дні макс_кількість true|false (застава в руці). Банкір: /market exchange permit ринок КОД issuer true|false."));
        say(p,LocalText.tr("Точний лот: /market inspect_lot ID; buy кількість КОД ціна дні [specID]. Графік: /market exchange chart години; зворотна книга: exchange inverse. Ліцензія: loan license_apply ринок КОД; мер: loan license_requests ринок."));
        say(p,LocalText.tr("Погодження: /market loan extend ID дні; contract ID amend дні опис; contract ID terminate виплата_виконавцю причина; agreement ID — перегляд; agreement accept ID — підпис другої сторони. Новий строк рахується від подання пропозиції."));
        if(p.hasPermission("empirecraft.feudal.admin"))say(p,LocalText.tr("Адмін: /market admin inspect ID; claims ID add|remove чанкX чанкZ причина; department ID GOODS|FX|JOBS|CREDIT building x1 y1 z1 x2 y2 z2 причина (дивитися на стійку); department ID тип approve|suspend причина; approve|reject|suspend|close|archive ID причина; recovery ID; legacy ринок старе_замовлення."));

 }
}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
 try(var languageScope=LocalText.use(sender)){
var options=args.length==1?List.of("menu","help","list","info","buy","sell","book","orders","cancel","reduce","load","assets","recover","exchange","jobs","contract","loan","share_info","mail","manage","founding","admin","history","confirm"):List.<String>of();return options.stream().filter(x->x.startsWith(args[args.length-1])).toList();
 }
}
}
