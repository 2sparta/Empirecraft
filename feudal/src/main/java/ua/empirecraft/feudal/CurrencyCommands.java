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
    private String arg(String[] a,int i){require(a.length>i,"Недостатньо аргументів. /cu help; /cu exchange help");return a[i];}
    private String code(String[] a,int i){return arg(a,i).toUpperCase(Locale.ROOT);}
    private int ingots(String text){try{require(text.matches("[0-9]{1,4}"),"Потрібна ціла кількість злитків.");int n=Integer.parseInt(text);require(n>0 && n<=2304,"Кількість злитків: 1–2304.");return n;}catch(NumberFormatException ex){throw new IllegalArgumentException("Потрібна ціла кількість злитків: 1–2304.");}}
    private String nation(CommandSender sender) throws Exception {
        return plugin.currencyEstate(player(sender));
    }
    private String ownCode(CommandSender sender) throws Exception {String estate=nation(sender);var r=s.db.one("SELECT code FROM currencies WHERE issuer=? OR issuer=? ORDER BY code",estate,s.kingdomCapitals.get(estate));require(r!=null,"Держава ще не має валюти.");return string(r,"code");}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] a){
        try {
            a=CommandLanguage.normalize("currency",a);towny.sync();s.market.politics.syncDomains();s.tick();s.market.exchange.tick();String sub=a.length==0?"help":a[0].toLowerCase(Locale.ROOT);
            if(sub.equals("market") || sub.equals("chart") || sub.equals("menu") || a.length==0){require(a.length<=1,"/cu market або /cu menu — без аргументів.");plugin.currencyOpen(player(sender),"market","","");return true;}
            if(sub.equals("print") || sub.equals("mint")){String own=a.length>1?code(a,1):plugin.currencyOwned(player(sender));require(!own.isEmpty(),"Спочатку створіть власну валюту.");s.market.printing(actor(sender),own);plugin.currencyOpen(player(sender),"print","",own);return true;}
            if(sub.equals("fees") || sub.equals("settings")){plugin.currencyOpen(player(sender),"policy","@GOLD",a.length>1?code(a,1):ownCode(sender));return true;}
            if(sub.equals("redemptions")){plugin.currencyOpen(player(sender),"redeem","@GOLD","");return true;}
            if(sub.equals("log")){journal(sender,code(a,1),a.length>2?Long.parseLong(a[2]):0);return true;}
            if(sub.equals("balance")){var balances=s.db.query("SELECT code,balance FROM accounts WHERE owner=? AND balance>0 ORDER BY code","p:"+actor(sender).id());if(balances.isEmpty())say(sender,"Баланс: 0.00. Золото до видачі: /cu collect.");for(var row:balances)say(sender,format(number(row,"balance"))+" "+string(row,"code"));return true;}
            if(sub.equals("collect")){items.collect(player(sender),a.length>1?ingots(a[1]):null);say(sender,"Золоті злитки отримано в інвентар.");return true;}
            if(sub.equals("gold") || sub.equals("redeem")){bank(sender,sub,a);return true;}

            if(sub.equals("exchange")){String[] nested=Arrays.copyOfRange(a,1,a.length);exchange(sender,nested.length==0?"help":nested[0],nested);return true;}
            if(sub.equals("bank")){String[] nested=Arrays.copyOfRange(a,1,a.length);require(nested.length>=4 && nested[2].equalsIgnoreCase("@GOLD"),"Банк: /cu bank quote|convert кількість_злитків @GOLD КОД. Погашення: /cu redeem сума КОД.");bankExchange(sender,nested[0],nested);return true;}
            if(sub.equals("vault"))throw new IllegalArgumentException("Vault/BASE вимкнено. Використовуйте валюту міста.");
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
                say(sender,"/cu market; /cu menu; /cu print [код]; /cu balance; /cu collect; /cu banker add|remove <гравець>");
                say(sender,"/cu list; /cu info|value|reserve|audit <код>; /cu history [код]; /cu policy");
                say(sender,"/cu pay <гравець> <сума> <код>");
                say(sender,"Емітент: /cu create <назва> <код> [держава]; /cu print <код>; /cu burn <сума> <код>");
                say(sender,"/cu reserve deposit gold <цілі_злитки> [код]");
            }
            case "list"->{for(var r:s.db.query("SELECT * FROM currencies ORDER BY code"))say(sender,string(r,"code")+" — "+string(r,"name")+" ["+string(r,"backing")+"]");}
            case "info","value"->info(sender,code(a,1));
            case "reserve"->{
                if(a.length>1 && a[1].equals("deposit")){
                    int i=a.length>2 && a[2].equals("gold")?3:2;String code=a.length>i+1?code(a,i+1):ownCode(sender);
                    items.exchange(player(sender),true,ingots(arg(a,i)),"r:"+code);
                    say(sender,"Резерв поповнено. ");info(sender,code);
                }else info(sender,code(a,1));
            }
            case "create"->{String id=a.length>3?s.resolve(a[3]).id():nation(sender);s.createCurrency(who,id,code(a,2),arg(a,1).replace('_',' '),code(a,2).substring(0,Math.min(4,code(a,2).length())));say(sender,"Валюту створено: R=0, M=0. Внесіть злитки до резерву та виконайте перший випуск.");}
            case "mint"->throw new IllegalArgumentException("/cu print <код> — друк через меню.");
            case "burn"->{String code=code(a,2);String treasury="e:"+string(c.goldCurrency(code),"issuer");c.burn(who,treasury,code,money(arg(a,1)));say(sender,"Монети вилучено зі скарбниці; золото залишилося в резерві.");info(sender,code);}
            case "pay"->{player(sender);s.transfer(who,owner,"p:"+towny.residentId(arg(a,1)),code(a,3),money(arg(a,2)));say(sender,"Переказ виконано; обіг не змінився.");}
            case "history"->{var rows=c.history(who,a.length>1?code(a,1):null);if(rows.isEmpty())say(sender,"Операцій поки немає.");for(var r:rows)say(sender,date.format(Instant.ofEpochMilli(number(r,"at_time")))+" "+string(r,"event")+(string(r,"actor_name")==null?"":" ["+string(r,"actor_name")+"]")+": "+string(r,"detail"));}
            case "audit"->{String code=code(a,1);require(!code.equals("BASE"),"BASE — зовнішня економіка Vault; її обіг не ведеться як державна валюта.");info(sender,code);long sum=0;for(var r:s.db.query("SELECT balance FROM accounts WHERE code=?",code))sum=Math.addExact(sum,number(r,"balance"));say(sender,"Сума всіх гаманців і скарбниць: "+format(sum)+"; облік обігу: "+(sum==number(s.currency(code),"supply")?"§aузгоджений":"§cПОРУШЕННЯ"));if(who.admin())for(String issue:c.check())say(sender,"§c"+issue);}
            case "policy"->{say(sender,"Комісії та ліміти: /cu fees [код]; публічний журнал: /cu log <код>; погашення: /cu redemptions.");say(sender,"V=R/M. Емісія збільшує M без списання золота. Резерв не витрачається скарбницею.");say(sender,"Погашення повертає монети казні через пакетну чергу 10–20 хв; спалюється лише оголошена комісія. Золото видається за min_out.");say(sender,"Обмін інтегральний через казни; комісії обох емітентів додаються. Залишок округлення зберігається до фізичної видачі через /cu collect. Золото: 12 знаків, монети: 2.");say(sender,"При M=0 курс не визначений; при R=0 валюта не має золотого забезпечення. Спалення всіх монет залишає резерв до нового випуску.");}
            case "upgrade"->{c.upgrade(who,code(a,1));say(sender,"Валюта переведена на золотий облік. Внесіть фізичне золото до резерву.");}
            default->throw new IllegalArgumentException("Довідка: /cu help");
        }
    }
    private void bank(CommandSender sender,String sub,String[] a) throws Exception {
        Actor who=actor(sender);String owner="p:"+who.id();
        if(sub.equals("redeem")){
            player(sender);String code=code(a,2);boolean legacy="LEGACY_BASE".equals(string(s.currency(code),"backing"));
            if(!legacy){var q=s.market.exchange.quote(code,"@GOLD",arg(a,1));BigDecimal min=CurrencyExchange.minimum(a.length>3?a[3]:"",q);String id=s.market.exchange.request(who,owner,code,money(a[1]),min,q.stamp());say(sender,"Заявка "+id+" у черзі (10–20 хв). Прогноз "+show(q.output())+" ЗО; min_out="+show(min)+". /cu redemptions");return;}
            BigDecimal result=c.redeem(who,owner,code,money(arg(a,1)));
            say(sender,legacy?"Старі монети погашено 1:1 у BASE.":"Золото доступне до фізичної видачі: /cu collect");return;
        }
        require(sub.equals("gold"),"/cu redeem <сума> <код> [мінімум_ЗО]");
        String mode=a.length>1?a[1]:"help";
        switch(mode){
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
            default->throw new IllegalArgumentException("Особистий золотий банк прибрано. Золото в резерв: /cu reserve deposit gold <кількість> [код]. Фізична видача: /cu collect.");
        }
    }
    void journal(CommandSender sender,String code,long before)throws Exception{
        var rows=s.market.exchange.journal(code,before);if(rows.isEmpty())say(sender,"Записів поки немає.");for(var r:rows)say(sender,"#"+number(r,"seq")+" "+date.format(Instant.ofEpochMilli(number(r,"at_time")))+(string(r,"actor_name")==null?"":" ["+string(r,"actor_name")+"]")+" "+string(r,"event")+": "+string(r,"detail"));
        if(!rows.isEmpty())say(sender,"Старіші: /cu log "+code+" "+number(rows.get(rows.size()-1),"seq"));
    }
    private void exchange(CommandSender sender,String sub,String[] a) throws Exception {
        if(s.cityMarkets!=null){say(sender,"Місцева біржа: /market exchange. Банківська купівля за фізичне золото: /cu bank quote|convert кількість @GOLD КОД; погашення: /cu redeem сума КОД. Банківська оцінка не є котируванням місцевої біржі.");return;}
        bankExchange(sender,sub,a);
    }
    private void bankExchange(CommandSender sender,String sub,String[] a) throws Exception {
        if(!Set.of("quote","convert").contains(sub)){say(sender,"/cu bank quote|convert <сума> <звідки> <куди> [min_out]; золото: @GOLD");return;}
        String from=code(a,2),to=code(a,3);var q=s.market.exchange.quote(from,to,arg(a,1));BigDecimal min=CurrencyExchange.minimum(a.length>4?a[4]:"",q);
        if(sub.equals("convert")){player(sender);if(to.equals("@GOLD")){String id=s.market.exchange.request(actor(sender),"p:"+actor(sender).id(),from,money(a[1]),min,q.stamp());say(sender,"Погашення у черзі: "+id+". /cu redemptions");return;}if(from.equals("@GOLD")){items.purchase(player(sender),ingots(a[1]),to,min,q.stamp());say(sender,"Купівля за фізичні злитки виконана.");}else q=s.market.exchange.execute(actor(sender),from,to,a[1],min,q.stamp());}
        say(sender,show(q.input())+" "+from+" → "+show(q.output())+" "+to+"; min_out="+show(min));say(sender,"Комісії: "+show(q.feeA())+" + "+show(q.feeB())+" ЗО; спалення: "+format(q.sourceBurn())+" / "+format(q.targetBurn())+"; вплив: "+show(q.slippage())+"%; після: 1 "+from+" ≈ "+show(q.after())+" "+to);
    }
    @EventHandler public void join(PlayerJoinEvent event){
        try{if(s.db.one("SELECT id FROM gold_ops WHERE actor=? AND status='PREPARED'",event.getPlayer().getUniqueId().toString())!=null)say(event.getPlayer(),"§cЄ незавершена операція зі злитками. /cu gold pending; зверніться до адміністратора.");}
        catch(Exception ex){plugin.failure("Журнал золота",ex);}
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String label,String[] a){return CommandHints.complete(s,sender,"currency",a);}
}

