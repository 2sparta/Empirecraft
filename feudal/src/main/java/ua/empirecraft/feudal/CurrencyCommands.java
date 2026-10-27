package ua.empirecraft.feudal;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.CurrencyService.*;
import ua.empirecraft.feudal.FeudalService.Actor;

public final class CurrencyCommands implements CommandExecutor,TabCompleter,Listener {
    private final FeudalPlugin plugin;private final FeudalService s;private final CurrencyService c;private final TownyBridge towny;private final GoldInventoryBridge items;
    private final DateTimeFormatter date=DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.of("Europe/Kyiv"));
    public CurrencyCommands(FeudalPlugin p,FeudalService s,TownyBridge t){plugin=p;this.s=s;c=s.currencies;towny=t;items=new GoldInventoryBridge(c);}
    private Actor actor(CommandSender sender){return new Actor(sender instanceof Player p?p.getUniqueId().toString():"консоль",sender.hasPermission("empirecraft.feudal.admin"));}
    private Player player(CommandSender sender){require(sender instanceof Player,"Команда доступна гравцю в грі.");return (Player)sender;}
    private void say(CommandSender sender,String text){plugin.message(sender,text);}
    private String arg(String[] a,int i){require(a.length>i,"Недостатньо аргументів. /cu help; /cu help; /cu exchange help");return a[i];}
    private String code(String[] a,int i){return arg(a,i).toUpperCase(Locale.ROOT);}
    private int ingots(String text){try{require(text.matches("[0-9]{1,4}"),"Потрібна ціла кількість злитків.");int n=Integer.parseInt(text);require(n>0 && n<=2304,"Кількість злитків: 1–2304.");return n;}catch(NumberFormatException ex){throw new IllegalArgumentException("Потрібна ціла кількість злитків: 1–2304.");}}
    private String nation(CommandSender sender) throws Exception {
        return plugin.currencyEstate(player(sender));
    }
    private String ownCode(CommandSender sender) throws Exception {var r=s.db.one("SELECT code FROM currencies WHERE issuer=?",nation(sender));require(r!=null,"Держава ще не має валюти.");return string(r,"code");}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] a){
        try {
            towny.sync();s.market.politics.syncDomains();s.tick();String sub=a.length==0?"help":a[0].toLowerCase(Locale.ROOT);
            if(sub.equals("market") || sub.equals("chart") || sub.equals("menu") || a.length==0){plugin.currencyOpen(player(sender),"market",a.length>1?code(a,1):"@GOLD",a.length>2?code(a,2):"");return true;}
            if(sub.equals("print") || sub.equals("mint")){String own=a.length>1?code(a,1):plugin.currencyOwned(player(sender));require(!own.isEmpty(),"Спочатку створіть власну валюту.");s.market.manage(actor(sender),own);plugin.currencyOpen(player(sender),"print","@GOLD",own);return true;}
            if(sub.equals("balance")){for(var row:s.db.query("SELECT code,balance FROM accounts WHERE owner=? AND balance>0 ORDER BY code","p:"+actor(sender).id()))say(sender,format(number(row,"balance"))+" "+string(row,"code"));return true;}
            if(sub.equals("gold") || sub.equals("redeem")){bank(sender,sub,a);return true;}
            if(sub.equals("bank")){String[] nested=Arrays.copyOfRange(a,1,a.length);bank(sender,nested.length==0?"help":nested[0],nested);return true;}
            if(sub.equals("exchange")){String[] nested=Arrays.copyOfRange(a,1,a.length);exchange(sender,nested.length==0?"help":nested[0],nested);return true;}
            if(sub.equals("vault")){require(a.length==3 && Set.of("deposit","withdraw").contains(a[1]),"/cu vault deposit|withdraw <сума>");new VaultBridge(s).exchange(player(sender),a[1].equals("deposit"),money(a[2]));return true;}
            if(sub.equals("treasury")){stateTreasury(sender,a);return true;}
            if(sub.equals("banker")){require(a.length>=3 && Set.of("add","remove").contains(a[1]),"/cu banker add|remove <гравець> [володіння]");String estate=a.length>3?s.resolve(a[3]).id():nation(sender);s.market.banker(actor(sender),estate,towny.residentId(a[2]),a[1].equals("add"));say(sender,"Роль Банкір оновлено.");return true;}
            currency(sender,sub,a);
        }catch(IllegalArgumentException ex){say(sender,"§c"+ex.getMessage());}
        catch(Exception ex){plugin.failure("Валюта",ex);say(sender,"§cНе вдалося завершити операцію. Для операції зі злитками перевірте /cu gold pending.");}return true;
    }
    public void info(CommandSender sender,String code) throws Exception {
        var row=s.currency(code);say(sender,string(row,"name")+" ("+code+"). Обіг усіх рахунків: "+format(number(row,"supply")));
        if(code.equals("BASE")){say(sender,"BASE — облік Vault; золотого курсу не має.");return;}
        say(sender,"Емітент: "+s.estate(string(row,"issuer")).name());
        if("LEGACY_BASE".equals(string(row,"backing"))){say(sender,"Старий резерв: "+format(s.balance("r:"+code,"BASE"))+" BASE. Погашення 1:1 у BASE; новий випуск заборонено. Після повного погашення: /cu upgrade "+code);return;}
        say(sender,"Золотий резерв: "+show(c.reserve(code))+" ЗО. 1 ЗО = 1 золотий злиток.");
        if(number(row,"supply")==0)say(sender,"Обіг нульовий: курс ще не визначений; потрібен перший випуск.");
        else {say(sender,"1 "+code+" ≈ "+show(c.value(code))+" ЗО (R/M; до 12 знаків).");if(c.reserve(code).signum()==0)say(sender,"§cРезерв порожній: валюта незабезпечена; погашення та обмін недоступні.");}
    }
    private void stateTreasury(CommandSender sender,String[] a)throws Exception{
        String id=s.resolve(arg(a,1)).id();require(!id.startsWith("t:"),"Казна міста: /t treasury.");String treasury="e:"+id,owner="p:"+actor(sender).id();
        if(a.length==2){s.controlTreasury(actor(sender),s.estate(id));for(var row:s.db.query("SELECT code,balance FROM accounts WHERE owner=? AND balance>0",treasury))say(sender,format(number(row,"balance"))+" "+string(row,"code"));return;}
        require(a.length==5 && Set.of("deposit","withdraw").contains(a[2]),"/cu treasury <держава> deposit|withdraw <сума> <код>");s.transfer(actor(sender),a[2].equals("deposit")?owner:treasury,a[2].equals("deposit")?treasury:owner,code(a,4),money(a[3]));say(sender,"Казну оновлено.");
    }
    private void currency(CommandSender sender,String sub,String[] a) throws Exception {
        Actor who=actor(sender);String owner="p:"+who.id();
        switch(sub){
            case "help","допомога"->{
                say(sender,"/cu market [валюта] [інша]; /cu print [код]; /cu balance; /cu gold balance; /cu banker add|remove <гравець>");
                say(sender,"/cu list; /cu info|value|reserve|audit <код>; /cu history [код]; /cu policy");
                say(sender,"/cu pay <гравець> <сума> <код>");
                say(sender,"Король: /cu create <назва> <код> [держава]; /cu print <код>; /cu burn <сума> <код>");
                say(sender,"/cu reserve deposit <цілі_злитки> [код]; /cu reserve fund <сума_ЗО> [код]");
                say(sender,"/cu upgrade <код> — перевести повністю погашену стару валюту BASE на золотий облік.");
            }
            case "list"->{for(var r:s.db.query("SELECT * FROM currencies ORDER BY code"))say(sender,string(r,"code")+" — "+string(r,"name")+" ["+string(r,"backing")+"]");}
            case "info","value"->info(sender,code(a,1));
            case "reserve"->{
                if(a.length>1 && Set.of("deposit","fund").contains(a[1])){
                    String code=a.length>3?code(a,3):ownCode(sender);
                    if(a[1].equals("deposit"))items.exchange(player(sender),true,ingots(arg(a,2)),"r:"+code);
                    else c.depositReserve(who,owner,code,gold(arg(a,2)));
                    say(sender,"Резерв поповнено. ");info(sender,code);
                }else info(sender,code(a,1));
            }
            case "create"->{String id=a.length>3?s.resolve(a[3]).id():nation(sender);s.createCurrency(who,id,code(a,2),arg(a,1).replace('_',' '),code(a,2).substring(0,Math.min(4,code(a,2).length())));say(sender,"Валюту створено: R=0, M=0. Внесіть злитки до резерву та виконайте перший випуск.");}
            case "mint"->throw new IllegalArgumentException("/cu print <код> — друк через меню.");
            case "burn"->{String code=code(a,2);String treasury="e:"+string(c.goldCurrency(code),"issuer");c.burn(who,treasury,code,money(arg(a,1)));say(sender,"Монети вилучено зі скарбниці; золото залишилося в резерві.");info(sender,code);}
            case "pay"->{player(sender);s.transfer(who,owner,"p:"+towny.residentId(arg(a,1)),code(a,3),money(arg(a,2)));say(sender,"Переказ виконано; обіг не змінився.");}
            case "history"->{var rows=c.history(who,a.length>1?code(a,1):null);if(rows.isEmpty())say(sender,"Операцій поки немає.");for(var r:rows)say(sender,date.format(Instant.ofEpochMilli(number(r,"at_time")))+" "+string(r,"event")+": "+string(r,"detail"));}
            case "audit"->{String code=code(a,1);require(!code.equals("BASE"),"BASE — зовнішня економіка Vault; її обіг не ведеться як державна валюта.");info(sender,code);long sum=0;for(var r:s.db.query("SELECT balance FROM accounts WHERE code=?",code))sum=Math.addExact(sum,number(r,"balance"));say(sender,"Сума всіх гаманців і скарбниць: "+format(sum)+"; облік обігу: "+(sum==number(s.currency(code),"supply")?"§aузгоджений":"§cПОРУШЕННЯ"));if(who.admin())for(String issue:c.check())say(sender,"§c"+issue);}
            case "policy"->{say(sender,"V=R/M. Емісія збільшує M без списання золота. Резерв не витрачається скарбницею.");say(sender,"Погашення спалює монети й зараховує золото на рахунок; видача злитків — лише цілими одиницями.");say(sender,"Обмін атомарний, комісія 0; залишок від округлення монет надходить на золотий рахунок. Золото: 12 знаків, монети: 2.");say(sender,"При M=0 курс не визначений; при R=0 валюта не має золотого забезпечення. Спалення всіх монет залишає резерв до нового випуску.");}
            case "upgrade"->{c.upgrade(who,code(a,1));say(sender,"Валюта переведена на золотий облік. Внесіть фізичне золото до резерву.");}
            default->throw new IllegalArgumentException("Довідка: /cu help");
        }
    }
    private void bank(CommandSender sender,String sub,String[] a) throws Exception {
        Actor who=actor(sender);String owner="p:"+who.id();
        if(sub.equals("redeem")){
            player(sender);String code=code(a,2);boolean legacy="LEGACY_BASE".equals(string(s.currency(code),"backing"));
            BigDecimal result=c.redeem(who,owner,code,money(arg(a,1)));
            say(sender,legacy?"Старі монети погашено 1:1 у BASE.":"Зараховано "+show(result)+" ЗО на золотий рахунок. /cu gold withdraw <цілі_злитки>");return;
        }
        if(!sub.equals("gold")){
            say(sender,"/cu gold balance; /cu gold deposit|withdraw <цілі_злитки>; /cu redeem <сума> <код>");
            say(sender,"/cu gold pay <гравець> <сума_ЗО>; /cu gold treasury <володіння> [deposit|withdraw <сума_ЗО>]");
            say(sender,"/cu gold pending; адміністратор: /cu gold inspect <ID>; /cu gold resolve <ID> applied|failed — лише після перевірки збереженого інвентарю та журналу.");return;
        }
        String mode=a.length>1?a[1]:"balance";
        switch(mode){
            case "balance"->{player(sender);say(sender,"Золотий рахунок: "+show(c.goldBalance(owner))+" ЗО.");}
            case "deposit","withdraw"->{items.exchange(player(sender),mode.equals("deposit"),ingots(arg(a,2)),owner);say(sender,"Злитки "+(mode.equals("deposit")?"прийнято":"видано")+". Залишок: "+show(c.goldBalance(owner))+" ЗО.");}
            case "pay"->{player(sender);c.transferGold(who,owner,"p:"+towny.residentId(arg(a,2)),gold(arg(a,3)));say(sender,"Золото переказано.");}
            case "treasury"->{
                String id=s.resolve(arg(a,2)).id();require(!id.startsWith("t:"),"Золота казна міста: /t treasury gold.");String treasury="e:"+id;
                if(a.length==3){s.controlTreasury(who,s.estate(id));say(sender,"Золото скарбниці: "+show(c.goldBalance(treasury))+" ЗО.");}
                else {player(sender);String mode2=arg(a,3);require(Set.of("deposit","withdraw").contains(mode2),"Доступно deposit або withdraw.");c.transferGold(who,mode2.equals("deposit")?owner:treasury,mode2.equals("deposit")?treasury:owner,gold(arg(a,4)));say(sender,"Золоту скарбницю оновлено.");}
            }
            case "pending"->{
                var rows=who.admin()?s.db.query("SELECT * FROM gold_ops WHERE status='PREPARED'"):s.db.query("SELECT * FROM gold_ops WHERE status='PREPARED' AND actor=?",who.id());
                if(rows.isEmpty())say(sender,"Невизначених операцій немає.");
                for(var op:rows)say(sender,string(op,"id")+"; "+string(op,"actor")+"; "+string(op,"direction")+" "+number(op,"amount")+"; "+string(op,"target")+". Потрібна перевірка адміністратора; видачу не повторювати.");
            }
            case "inspect"->{
                require(who.admin(),"Лише адміністратор.");var op=s.db.one("SELECT * FROM gold_ops WHERE id=?",arg(a,2));require(op!=null,"Операцію не знайдено.");
                say(sender,string(op,"actor")+"; "+string(op,"direction")+" "+number(op,"amount")+"; "+string(op,"target")+"; "+string(op,"status")+"; "+date.format(Instant.ofEpochMilli(number(op,"created"))));
                Player p=Bukkit.getPlayer(UUID.fromString(string(op,"actor")));
                if(p==null)say(sender,"Гравець офлайн. Перевірте збережені playerdata і журнал сервера перед узгодженням.");
                else say(sender,"Поточний інвентар збігається: до="+GoldInventoryBridge.matches(p,string(op,"before_items"))+"; після="+GoldInventoryBridge.matches(p,string(op,"after_items"))+". Сам збіг не доводить результату після подальших змін інвентарю.");
            }
            case "resolve"->{require(who.admin(),"Лише адміністратор після перевірки інвентарю та журналу.");String status=arg(a,3);require(Set.of("applied","failed").contains(status),"Статус: applied або failed.");c.finishGold(who,arg(a,2),status.equals("applied"));say(sender,"Результат зафіксовано. Предмети повторно не видавалися.");}
            default->throw new IllegalArgumentException("Довідка: /cu help");
        }
    }
    private void exchange(CommandSender sender,String sub,String[] a) throws Exception {
        if(!Set.of("quote","convert").contains(sub)){say(sender,"/cu exchange quote <сума> <звідки> <куди>");say(sender,"/cu exchange convert <сума> <звідки> <куди> [мінімум_монет_на_виході]");return;}
        long n=money(arg(a,1));String from=code(a,2),to=code(a,3);
        CurrencyService.Quote q;
        if(sub.equals("convert")){player(sender);q=c.convert(actor(sender),n,from,to,a.length>4?money(a[4]):0);}else q=c.quote(n,from,to);
        say(sender,(sub.equals("quote")?"Розрахунок за поточним курсом: ":"Обмін виконано: ")+format(n)+" "+from+" → "+format(q.coins())+" "+to);
        say(sender,"Решта на золотий рахунок: "+show(q.change())+" ЗО. Комісія: 0.");
    }
    @EventHandler public void join(PlayerJoinEvent event){
        try{if(s.db.one("SELECT id FROM gold_ops WHERE actor=? AND status='PREPARED'",event.getPlayer().getUniqueId().toString())!=null)say(event.getPlayer(),"§cЄ незавершена операція зі злитками. /cu gold pending; зверніться до адміністратора.");}
        catch(Exception ex){plugin.failure("Журнал золота",ex);}
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String label,String[] a){
        if(a.length==1)return List.of("market","chart","print","balance","list","info","value","reserve","create","burn","pay","history","audit","policy","upgrade","gold","redeem","exchange","vault","treasury","banker").stream().filter(x->x.startsWith(a[0])).toList();
        try{if(a.length==2 && Set.of("print","info","value","reserve","audit","upgrade").contains(a[0]))return s.db.query("SELECT code FROM currencies ORDER BY code").stream().map(r->string(r,"code")).filter(x->x.startsWith(a[1].toUpperCase(Locale.ROOT))).toList();}catch(Exception ignored){}return List.of();
    }
}
