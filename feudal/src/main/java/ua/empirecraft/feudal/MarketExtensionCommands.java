package ua.empirecraft.feudal;

import java.time.*;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.MarketCommands.*;

/** Shared command and native-screen routes. The same expiring confirmation rechecks location and terms. */
public final class MarketExtensionCommands {
    final MarketCommands c;final CityMarketService m;
    record Pending(String id,String[] args,String spec,String context,long expires) {}
    final Map<UUID,Pending> pending=new HashMap<>();
    MarketExtensionCommands(MarketCommands c){this.c=c;m=c.m;}
    void say(Player p,String text){c.say(p,text);}
    static long date(String text){return LocalDateTime.parse(text).atZone(ZoneId.of("Europe/Kyiv")).toInstant().toEpochMilli();}
    String text(String[] a,int start){return String.join(" ",Arrays.copyOfRange(a,start,a.length));}
    void prepareVassal(Player p,String[] a)throws Exception {
        require(a.length>1&&a[0].equals("@vassal")&&Set.of("offer","book").contains(a[1]),"Невідома форма договору.");
        if(a[1].equals("offer")){var child=m.s.resolve(arg(a,2));var parent=m.s.resolve(arg(a,3));say(p,"Васал: "+child.name()+" ["+child.id()+"]; сюзерен: "+parent.name()+" ["+parent.id()+"]");say(p,"Данина "+format(money(arg(a,4)))+" "+code(a,5)+" кожні 7 діб; перший платіж через 7 діб після активації. Строк "+intArg(a,8)+" діб (0 — безстроково); автономія "+arg(a,6)+"; відіграш військової угоди "+arg(a,7)+". Пропозиція сама не активує договір: кожна сторона переглядає всі механічні умови й підписує токен.");}
        else say(p,"Книга "+arg(a,2)+" до договору "+arg(a,3)+". Авторський текст не змінює механічних умов: "+(a.length>4?a[4]:""));
        var q=new Pending(id(),a.clone(),"",context(p,a),m.s.now()+60000);pending.put(p.getUniqueId(),q);say(p,"Підтвердження форми: /market extension_confirm "+q.id);
    }
    String context(Player p,String[] a)throws Exception {
        if(a[0].equals("@vassal")){if(a[1].equals("book"))return m.documents.vassal(arg(a,3)).stamp();var child=m.s.resolve(arg(a,2));var parent=m.s.resolve(arg(a,3));return hash(child+"|"+parent+"|"+m.s.parent(child.id()));}
        if(a[0].equals("auction")&&Set.of("create","create_asset").contains(arg(a,1))){
            boolean city=a[1].equals("create_asset");var asset=city?m.ownAsset(actor(p),arg(a,2)):null;String market=city?string(asset,"market"):c.current(p),spec=city?string(asset,"spec"):c.spec(p),owner=city?string(asset,"owner"):wallet(actor(p));int offset=city?1:0;
            var rate=m.auctions.sellerRate(actor(p),owner,market,spec,money(arg(a,4+offset)),money(arg(a,6+offset)),Long.MAX_VALUE,code(a,3+offset));return hash(asset+"|"+spec+"|"+m.tariff(market)+"|"+rate);
        }
        if(a[0].equals("auction")&&Set.of("bid","buyout","cancel").contains(arg(a,1))){var row=m.auctions.auction(arg(a,2));if(a[1].equals("cancel"))return hash(row.toString());long amount=a[1].equals("buyout")?number(row,"buyout"):money(arg(a,3));int ownerIndex=a[1].equals("bid")?4:3;String owner=a.length>ownerIndex?a[ownerIndex]:wallet(actor(p));return m.auctions.stamp(actor(p),a[2],owner,amount);}
        if(a[0].equals("exchange")&&arg(a,1).equals("official"))return m.official.quote(actor(p),c.current(p),place(p),code(a,2),code(a,3),money(arg(a,4))).stamp();
        if(a[0].equals("procurement")&&a.length>2&&!a[1].equals("create"))return hash(m.procurement.mandate(a[2]).toString());
        if(a[0].equals("treaty")&&a.length>2&&!a[1].equals("create"))return hash(m.treaties.pending(a[a[1].equals("book")?3:2]).toString());
        if(a[0].equals("fair")&&a.length>2&&!a[1].equals("create"))return hash(m.fairs.fair(a[a[1].equals("letter")?3:2]).toString());
        try{return hash(m.tariff(c.current(p)).toString());}catch(IllegalArgumentException ex){return "";}
    }
    public boolean handle(Player p,String[] a)throws Exception {
        if(a.length==0)return false;String sub=a[0];
        if(sub.equals("extension_confirm")){
            var q=pending.get(p.getUniqueId());require(q!=null&&q.id.equals(arg(a,1))&&q.expires>m.s.now(),"Підтвердження прострочене.");
            var done=m.db.one("SELECT result FROM cm_actions WHERE id=? AND actor=?",q.id,p.getUniqueId().toString());
            if(done!=null){say(p,"Дію вже зафіксовано: "+string(done,"result"));return true;}
            require(q.context.equals(context(p,q.args)),"Умови змінилися. Перегляньте й підтвердьте новий розрахунок.");
            String result=mutate(p,q);say(p,"Дію зафіксовано. ID: "+result);return true;
        }
        if(!Set.of("treaty","procurement","auction","fair").contains(sub)&&!(sub.equals("exchange")&&a.length>1&&a[1].equals("official"))&&!(sub.equals("manage")&&a.length>2&&Set.of("official","official_deposit","official_withdraw","official_contribute").contains(a[2])))return false;
        String mode=a.length>1?a[1]:"list";
        if(sub.equals("treaty")&&mode.equals("view")){showDocument(p,m.documents.view(actor(p),m.treaties.document(arg(a,2)),a.length>3?intArg(a,3):1),"/market treaty",arg(a,2));return true;}
        if(sub.equals("treaty")&&mode.equals("accept")){say(p,"Підпис збережено: "+m.treaties.accept(actor(p),arg(a,2),numberArg(a,3),arg(a,4)));return true;}
        if(Set.of("auction","procurement","treaty","fair").contains(sub)&&Set.of("list","menu","view").contains(mode)){
            if(mode.equals("view")){String id=arg(a,2);var r=switch(sub){case "auction"->{var row=m.auctions.auction(id);m.access(string(row,"market"),Department.GOODS,place(p));yield row;}case "procurement"->{var row=m.procurement.mandate(id);m.manage(actor(p),string(row,"market"));yield row;}default->m.fairs.fair(id);};say(p,r.toString());return true;}
            if(sub.equals("auction")){String market=c.current(p);m.access(market,Department.GOODS,place(p));for(var r:m.db.query("SELECT * FROM cm_auctions WHERE market=? AND state='OPEN'",market))say(p,auctionDescription(r));}
            else if(sub.equals("procurement")){String market=c.current(p);m.manage(actor(p),market);for(var r:m.db.query("SELECT * FROM cm_procurements WHERE market=?",market))say(p,r.toString());}
            else if(sub.equals("treaty")){for(var r:m.db.query("SELECT id,active_version,pending_version FROM cm_treaties"))say(p,r.toString());}
            else for(var r:m.db.query("SELECT id,market,name,starts,ends,state FROM cm_fairs"))say(p,r.toString());return true;
        }
        if(sub.equals("fair")&&mode.equals("letter")&&arg(a,2).equals("preview")){m.fairs.preview(actor(p),arg(a,3),place(p));return true;}
        String spec="";
        if(sub.equals("auction")&&mode.equals("create")||sub.equals("procurement")&&mode.equals("create"))spec=c.spec(p);
        String fingerprint=context(p,a);var q=new Pending(id(),a.clone(),spec,fingerprint,m.s.now()+60000);
        say(p,"Перевірте дію: /market "+String.join(" ",a));
        if(sub.equals("auction")&&Set.of("create","create_asset").contains(mode)){
            boolean city=mode.equals("create_asset");var asset=city?m.ownAsset(actor(p),arg(a,2)):null;int offset=city?1:0;String market=city?string(asset,"market"):c.current(p),owner=city?string(asset,"owner"):wallet(actor(p));var rate=m.auctions.sellerRate(actor(p),owner,market,city?string(asset,"spec"):spec,money(arg(a,4+offset)),money(arg(a,6+offset)),Long.MAX_VALUE,code(a,3+offset));
            say(p,"Власник лота "+owner+"; збір продавця "+rate.bps()+" б.п. з кінцевої ціни; товар і виручка залишаються на ринку "+market+". Скасування лише до першої ставки; можливе продовження до 30 хвилин.");
            if(money(arg(a,6+offset))==0)say(p,"Без викупу ціна не має верхньої межі: обмежена лімітом пільга продавця не застосовується.");
        }
        if(sub.equals("exchange")){var quote=m.official.quote(actor(p),c.current(p),place(p),code(a,2),code(a,3),money(arg(a,4)));say(p,"Офіційний обмін: "+format(quote.input())+" "+quote.from()+" → "+format(quote.output())+" "+quote.to()+"; міський збір окремо "+format(quote.fee())+" "+quote.from()+"; разом списати "+format(quote.input()+quote.fee())+". Видача у FX цього ринку.");}
        if(sub.equals("auction")&&Set.of("bid","buyout").contains(mode)){var r=m.auctions.auction(arg(a,2));say(p,auctionDescription(r));long amount=mode.equals("buyout")?number(r,"buyout"):money(arg(a,3));int ownerIndex=mode.equals("bid")?4:3;String owner=a.length>ownerIndex?a[ownerIndex]:wallet(actor(p));int bps=m.auctions.rate(actor(p),owner,r,amount).bps();say(p,"Ставка "+format(amount)+" "+string(r,"code")+"; збір "+format(fee(amount,bps))+"; резерв "+format(amount+fee(amount,bps))+"; отримання на ринку "+string(r,"market"));}
        pending.put(p.getUniqueId(),q);say(p,"Підтвердіть протягом 60 секунд: /market extension_confirm "+q.id);
        if(c.uiMessages==null)InteractiveMessages.button(p,"[Підтвердити]","/market extension_confirm "+q.id,false);return true;
    }
    String auctionDescription(Map<String,Object> r){long bid=number(r,"bid");return "Аукціон "+string(r,"id")+" · лот "+number(r,"quantity")+" · ставка "+format(bid)+" "+string(r,"code")+" · наступна "+format(bid==0?number(r,"start_price"):bid+number(r,"step"))+" · викуп "+(number(r,"buyout")==0?"немає":format(number(r,"buyout")))+" · кінець "+time(number(r,"ends"))+"; ставки в останні 2 хв продовжують до 2 хв, максимум "+time(number(r,"maximum_end"))+"; збір продавця "+number(r,"seller_bps")+" б.п.";}
    void showDocument(Player p,MarketDocuments.Page page,String command,String id){
        for(String line:page.lines())say(p,line);say(p,"Сторінка "+page.page()+"/"+page.pages());
        if(page.page()<page.pages())InteractiveMessages.button(p,"[Наступна сторінка]",command+" view "+id+" "+(page.page()+1),false);
        if(!page.token().isEmpty()){
            say(p,"Прийняття лише механічних умов: "+command+" accept "+id+" "+page.version()+" "+page.token());
            InteractiveMessages.button(p,"[Прийняти механічні умови]",command+" accept "+id+" "+page.version()+" "+page.token(),false);
            InteractiveMessages.button(p,"[Відхилити]",command+" reject "+id,false);
        }
    }
    String mutate(Player p,Pending q)throws Exception {
        String[] a=q.args;String sub=a[0],mode=arg(a,1),req=q.id;var who=actor(p);
        if(sub.equals("@vassal")){
            if(mode.equals("offer"))return m.once(who,req,"native-vassal|"+String.join("|",a),()->m.s.offer(who,m.s.resolve(arg(a,2)).id(),m.s.resolve(arg(a,3)).id(),money(arg(a,4)),arg(a,5).equals("-")?"":code(a,5),arg(a,6),arg(a,7),intArg(a,8)));
            String id=arg(a,3),format=code(a,2),body=a.length>4?a[4]:"";String result=m.documents.vassalBook(who,req,id,format,body);writeBook(p,m.documents.vassal(id),format,body);return result;
        }
        if(sub.equals("exchange"))return m.official.exchange(who,req,c.current(p),place(p),code(a,2),code(a,3),money(arg(a,4)),q.context);
        if(sub.equals("manage"))return switch(arg(a,2)){
            case "official"->m.official.configure(who,req,a[1],place(p),flag(a,3));
            case "official_contribute"->m.official.contribute(who,req,a[1],place(p),code(a,3),money(arg(a,4)));
            case "official_deposit","official_withdraw"->m.official.capital(who,req,a[1],place(p),code(a,3),money(arg(a,4)),a[2].equals("official_deposit"));
            default->throw new IllegalArgumentException("Невідома дія фонду.");};
        if(sub.equals("auction"))return switch(mode){
            case "create"->{String op=m.auctions.create(who,req,c.current(p),place(p),q.spec,numberArg(a,2),code(a,3),money(arg(a,4)),money(arg(a,5)),money(arg(a,6)),intArg(a,7),c.take(p,q.spec,numberArg(a,2)));c.apply(op);yield op;}
            case "create_asset"->m.auctions.createAsset(who,req,arg(a,2),place(p),numberArg(a,3),code(a,4),money(arg(a,5)),money(arg(a,6)),money(arg(a,7)),intArg(a,8));
            case "bid","buyout"->{var r=m.auctions.auction(arg(a,2));String owner=a.length>(mode.equals("bid")?4:3)?arg(a,mode.equals("bid")?4:3):wallet(who);yield m.auctions.bid(who,req,a[2],place(p),owner,mode.equals("buyout")?number(r,"buyout"):money(arg(a,3)),mode.equals("buyout"),q.context);}
            case "cancel"->m.auctions.cancel(who,req,arg(a,2));default->throw new IllegalArgumentException("Аукціон: create, bid, buyout, cancel.");};
        if(sub.equals("procurement"))return switch(mode){
            case "create"->m.procurement.create(who,req,c.current(p),place(p),q.spec,code(a,2),numberArg(a,3),numberArg(a,4),money(arg(a,5)),money(arg(a,6)),money(arg(a,7)),numberArg(a,8),intArg(a,9));
            case "pause","resume","stop"->m.procurement.state(who,req,arg(a,2),mode.equals("pause")?"PAUSED":mode.equals("stop")?"STOPPED":"RUNNING");
            case "deposit"->m.procurement.deposit(who,req,arg(a,2),place(p),money(arg(a,3)));
            case "amend"->m.procurement.amend(who,req,arg(a,2),place(p),numberArg(a,3),numberArg(a,4),money(arg(a,5)),money(arg(a,6)),numberArg(a,7));
            default->throw new IllegalArgumentException("Закупівлі: create, pause, resume, stop, deposit, amend.");};
        if(sub.equals("treaty"))return switch(mode){
            case "create"->m.treaties.create(who,req,arg(a,2),arg(a,3),c.current(p),arg(a,4),place(p),intArg(a,5),intArg(a,6),intArg(a,7),money(arg(a,8)),code(a,9),intArg(a,10),arg(a,11),date(arg(a,12)),date(arg(a,13)),intArg(a,14));
            case "publish"->m.treaties.publish(who,req,arg(a,2),place(p));
            case "reject"->m.treaties.reject(who,req,arg(a,2));case "terminate"->m.treaties.terminate(who,req,arg(a,2));
            case "amend"->m.treaties.amend(who,req,arg(a,2),intArg(a,3),intArg(a,4),intArg(a,5),money(arg(a,6)),date(arg(a,7)));
            case "book"->{String id=arg(a,3),format=arg(a,2).toUpperCase(Locale.ROOT);String body=a.length>4?text(a,4):manual(p,format);String result=m.treaties.book(who,req,id,format,body);writeBook(p,m.treaties.document(id),format,body);yield result;}
            default->throw new IllegalArgumentException("Договір: create, publish, view, accept, reject, amend, terminate, book auto|manual ID.");};
        if(sub.equals("fair"))return switch(mode){
            case "create"->m.fairs.create(who,req,c.current(p),place(p),arg(a,2),arg(a,3),date(arg(a,4)),intArg(a,5),arg(a,6),arg(a,7),intArg(a,8),intArg(a,9),intArg(a,10));
            case "cancel"->m.fairs.cancel(who,req,arg(a,2));
            case "letter"->switch(arg(a,2)){case "edit"->m.fairs.edit(who,req,arg(a,3),text(a,4));case "cancel"->m.fairs.cancelLetter(who,req,arg(a,3));case "confirm"->m.fairs.confirm(who,req,arg(a,3),numberArg(a,4),arg(a,5),place(p));default->throw new IllegalArgumentException("Лист: edit, preview, confirm, cancel.");};
            default->throw new IllegalArgumentException("Ярмарок: create, view, cancel, letter.");};
        throw new IllegalArgumentException("Невідома дія.");
    }
    static String manual(Player p,String mode){if(!mode.equals("MANUAL"))return "";require(p.getInventory().getItemInMainHand().getItemMeta() instanceof BookMeta,"Тримайте власноруч написану книгу або введіть текст у формі.");return String.join("\n",((BookMeta)p.getInventory().getItemInMainHand().getItemMeta()).getPages());}
    static void writeBook(Player p,MarketDocuments.Document d,String mode,String body){
        var book=new ItemStack(org.bukkit.Material.WRITTEN_BOOK);var meta=(BookMeta)book.getItemMeta();meta.setTitle("Договір "+d.id().substring(0,8));meta.setAuthor(p.getName());
        String content=String.join("\n",d.lines())+"\n\n"+(mode.equals("MANUAL")?"Авторський текст (без механічного виконання):\n"+body:"");
        List<String> pages=new ArrayList<>();for(int start=0;start<content.length();start+=220)pages.add(content.substring(start,Math.min(start+220,content.length())));meta.setPages(pages);book.setItemMeta(meta);
        require(p.getInventory().firstEmpty()>=0,"Звільніть місце для книги. Договір збережено; можна повторити видачу за ID.");p.getInventory().addItem(book);p.saveData();
    }
}
