package ua.empirecraft.feudal;

import java.util.*;
import java.nio.file.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CurrencyTests.*;
import static ua.empirecraft.feudal.CityMarketTests.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Real H2 and persisted inventory intents: extended economy, authority and replay regression coverage. */
public final class MarketExtensionTests {
    static void bank(F f)throws Exception {
        CurrencyMarketTests.market(f);f.db.execute("UPDATE political_states SET capital='t:D' WHERE estate='n:A'");
        f.m.official.configure(d,req(),f.market,f.p(Department.FX),true);
        f.s.transfer(b,"p:"+B,"p:"+D,"SOL",100000);f.m.official.contribute(d,req(),f.market,f.p(Department.FX),"SOL",100000);
        f.s.transfer(b,"p:"+B,"p:"+D,"KRN",100000);f.m.official.contribute(d,req(),f.market,f.p(Department.FX),"KRN",100000);
    }
    static String auction(F f)throws Exception {String op=f.m.auctions.create(e,req(),f.market,f.p(Department.GOODS),f.iron,10,"KRN",1000,100,10000,1,change(e));f.commerce.finish(op,true);return string(f.db.one("SELECT related FROM cm_transfers WHERE op=?",op),"related");}
    static String secondMarket(F f)throws Exception {
        String id=f.m.create(e,req(),"t:E",WORLD,"Черкаський","Опис");for(int x=4;x<8;x++)f.m.claim(admin,req(),id,x,0,true,"Нова зона");
        for(var dep:Department.values()){int x=64+dep.ordinal()*16;f.m.building(admin,req(),id,dep,new Box(x,60,0,x+15,80,15),x+2,64,2,"STONE","Власна будівля");f.m.reviewDepartment(admin,req(),id,dep,true,"Огляд");}
        f.m.submit(e,req(),id);f.m.state(admin,req(),id,"OPEN","Схвалено");f.m.permit(e,req(),id,"KRN","payment",true);
        var citizens=new HashSet<>(f.citizens);citizens.add(E+"@t:E");citizens.add(D+"@t:D");f.s.sync(new Model.Snapshot(f.entities,citizens));return id;
    }
    static String treaty(F f,boolean activate)throws Exception {
        String other=secondMarket(f);String id=f.m.treaties.create(d,req(),"t:D","t:E",f.market,other,f.p(Department.JOBS),0,50,50,100000,7,"*",f.s.now(),f.s.now()+30*FeudalService.DAY,24);
        f.m.treaties.publish(d,req(),id,f.p(Department.JOBS));if(activate){sign(f,d,id);sign(f,e,id);}return id;
    }
    static String token(F f,Actor who,MarketDocuments.Document doc)throws Exception {MarketDocuments.Page page=null;for(int i=1;i<=Math.max(1,(doc.lines().size()+7)/8);i++)page=f.m.documents.view(who,doc,i);return page.token();}
    static void sign(F f,Actor who,String id)throws Exception {var doc=f.m.treaties.document(id);f.m.treaties.accept(who,id,doc.version(),token(f,who,doc));}
    static String procurement(F f,long budget,long daily,long batch)throws Exception {f.s.transfer(b,"p:"+B,"e:t:D","KRN",budget);return f.m.procurement.create(d,req(),f.market,f.p(Department.GOODS),f.iron,"KRN",5,20,100,budget,daily,batch,1);}
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Офіційний фонд використовує оцінки обох валют, не біржову заявку",()->{try(var f=new F()){
            bank(f);var q=f.m.official.quote(f.market,f.p(Department.FX),"KRN","SOL",1000);eq(q.output(),1000L);
            f.m.order(e,req(),f.market,f.p(Department.FX),Department.FX,"","KRN","SOL","SELL",10,99999,1,1,null);
            eq(f.m.official.quote(f.market,f.p(Department.FX),"KRN","SOL",1000).output(),1000L);
            var ra=f.s.currencies.reserve("KRN");var rb=f.s.currencies.reserve("SOL");long sa=number(f.s.currency("KRN"),"supply"),sb=number(f.s.currency("SOL"),"supply");
            String request=req();f.m.official.exchange(b,request,f.market,f.p(Department.FX),"KRN","SOL",1000,q.stamp());f.m.official.exchange(b,request,f.market,null,"KRN","SOL",1000,q.stamp());
            eq(f.s.balance(MarketOfficialExchange.fund(f.market),"KRN"),101000L);eq(f.s.currencies.reserve("KRN"),ra);eq(f.s.currencies.reserve("SOL"),rb);eq(number(f.s.currency("KRN"),"supply"),sa);eq(number(f.s.currency("SOL"),"supply"),sb);clean(f);
        }});
        test("Офіційне підтвердження відхиляє нову оцінку, брак фонду й інший відділ",()->{try(var f=new F()){
            bank(f);var q=f.m.official.quote(f.market,f.p(Department.FX),"KRN","SOL",1000);f.gold(b,1000);f.back(b,"SOL","1000");
            no(()->f.m.official.exchange(b,req(),f.market,f.p(Department.FX),"KRN","SOL",1000,q.stamp()));no(()->f.m.official.quote(f.market,f.p(Department.GOODS),"KRN","SOL",1000));no(()->f.m.official.quote(f.market,f.p(Department.FX),"KRN","SOL",1000000));clean(f);
        }});
        test("Втрата статусу столиці анулює офіційний обмін, але не приватну книгу",()->{try(var f=new F()){
            bank(f);var q=f.m.official.quote(f.market,f.p(Department.FX),"KRN","SOL",1000);f.db.execute("UPDATE political_states SET capital='t:E' WHERE estate='n:A'");no(()->f.m.official.exchange(b,req(),f.market,f.p(Department.FX),"KRN","SOL",1000,q.stamp()));f.m.order(b,req(),f.market,f.p(Department.FX),Department.FX,"","KRN","SOL","BUY",1,100,1,1,null);clean(f);
        }});
        test("Максимальна торговельна республіка відкриває допущені пари, нижчий рівень ні",()->{try(var f=new F()){
            bank(f);f.db.execute("UPDATE political_states SET capital='t:E',government='REPUBLIC',stage='TRADE_REPUBLIC' WHERE estate='n:A'");eq(f.m.official.quote(f.market,f.p(Department.FX),"KRN","SOL",1000).output(),1000L);
            f.db.execute("UPDATE political_states SET stage='FREE_CITY' WHERE estate='n:A'");no(()->f.m.official.quote(f.market,f.p(Department.FX),"KRN","SOL",1000));clean(f);
        }});
        test("Офіційний трикутник не генерує прибуток округленням",()->{try(var f=new F()){
            currency(f,c,"n:C","DUK",333,10000000);bank(f);f.db.execute("MERGE INTO cm_permits KEY(market,code) VALUES(?,'DUK',TRUE,TRUE,TRUE)",f.market);f.s.transfer(c,"e:n:C","p:"+D,"DUK",100000);f.m.official.contribute(d,req(),f.market,f.p(Department.FX),"DUK",100000);f.db.execute("UPDATE political_states SET government='REPUBLIC',stage='TRADE_REPUBLIC' WHERE estate='n:A'");
            long amount=1000;for(String[] pair:new String[][]{{"KRN","SOL"},{"SOL","DUK"},{"DUK","KRN"}})amount=f.m.official.quote(f.market,f.p(Department.FX),pair[0],pair[1],amount).output();eq(amount<=1000,true);clean(f);
        }});
        test("Купівля монет за золото закрита в сервісному шарі, погашення лишається",()->{try(var f=new F()){
            bank(f);f.gold(a,100000);f.back(a,"KRN","100000");no(()->f.s.market.exchange.quote("@GOLD","KRN","1"));no(()->f.s.market.exchange.executePhysical(b,req(),CurrencyService.ZERO,""));eq(f.s.market.exchange.quote("KRN","@GOLD","1").output().signum()>0,true);
        }});
        test("Аукціон приймає фізичний лот лише після підтвердження інвентарю",()->{try(var f=new F()){
            String op=f.m.auctions.create(e,req(),f.market,f.p(Department.GOODS),f.iron,10,"KRN",1000,100,0,1,change(e));String id=string(f.db.one("SELECT related FROM cm_transfers WHERE op=?",op),"related");no(()->f.m.auctions.bid(b,req(),id,f.p(Department.GOODS),wallet(b),1000,false,hash(f.m.auctions.auction(id).toString())));f.commerce.finish(op,true);f.commerce.finish(op,true);eq(string(f.m.auctions.auction(id),"state"),"OPEN");clean(f);
        }});
        test("Перевищена ставка повертається один раз, підвищення своєї резервує різницю",()->{try(var f=new F()){
            String id=auction(f);long before=f.s.balance(wallet(b),"KRN");f.m.auctions.bid(b,req(),id,f.p(Department.GOODS),wallet(b),1000,false,hash(f.m.auctions.auction(id).toString()));f.m.auctions.bid(b,req(),id,f.p(Department.GOODS),wallet(b),1200,false,hash(f.m.auctions.auction(id).toString()));eq(before-f.s.balance(wallet(b),"KRN"),1200L);
            f.s.transfer(b,wallet(b),wallet(d),"KRN",2000L);f.m.auctions.bid(d,req(),id,f.p(Department.GOODS),wallet(d),1300,false,hash(f.m.auctions.auction(id).toString()));eq(f.money(B,"OUTBID_REFUND"),1200L);clean(f);
        }});
        test("Викуп має окреме підтвердження й одного переможця",()->{try(var f=new F()){
            String id=auction(f);String stamp=hash(f.m.auctions.auction(id).toString());no(()->f.m.auctions.bid(b,req(),id,f.p(Department.GOODS),wallet(b),10001,false,stamp));String request=req();f.m.auctions.bid(b,request,id,f.p(Department.GOODS),wallet(b),10000,true,stamp);f.m.auctions.bid(b,request,id,null,wallet(b),10000,true,stamp);eq(f.items(B),10L);eq(f.money(E,"AUCTION_PROCEEDS"),9800L);eq(f.s.balance("e:t:D","KRN"),200L);no(()->f.m.auctions.bid(d,req(),id,f.p(Department.GOODS),wallet(d),10000,true,hash(f.m.auctions.auction(id).toString())));clean(f);
        }});
        test("Самоставка й скасування лота після ставки заборонені",()->{try(var f=new F()){
            String id=auction(f);no(()->f.m.auctions.bid(e,req(),id,f.p(Department.GOODS),wallet(e),1000,false,hash(f.m.auctions.auction(id).toString())));f.m.auctions.bid(b,req(),id,f.p(Department.GOODS),wallet(b),1000,false,hash(f.m.auctions.auction(id).toString()));no(()->f.m.auctions.cancel(e,req(),id));clean(f);
        }});
        test("Продовження аукціону обмежене 30 хв, закриття зберігає чинну ставку",()->{try(var f=new F()){
            String id=auction(f);long maximum=number(f.m.auctions.auction(id),"maximum_end");f.time.value=number(f.m.auctions.auction(id),"ends")-1000;f.m.auctions.bid(b,req(),id,f.p(Department.GOODS),wallet(b),1000,false,hash(f.m.auctions.auction(id).toString()));eq(number(f.m.auctions.auction(id),"ends"),f.s.now()+120000);eq(number(f.m.auctions.auction(id),"ends")<=maximum,true);
            f.m.state(admin,req(),f.market,"CLOSED","Закриття");f.m.tick();f.m.tick();eq(f.items(B),10L);eq(f.money(E,"AUCTION_PROCEEDS"),980L);clean(f);
        }});
        test("Немає ставок: товар повертається, прострочена ставка не приймається",()->{try(var f=new F()){
            String id=auction(f);f.time.value=number(f.m.auctions.auction(id),"ends");no(()->f.m.auctions.bid(b,req(),id,f.p(Department.GOODS),wallet(b),1000,false,hash(f.m.auctions.auction(id).toString())));f.m.tick();eq(f.items(E),10L);clean(f);
        }});
        test("Закупівлі враховують уже замовлене й активні резерви добового ліміту",()->{try(var f=new F()){
            String id=procurement(f,10000,1000,20);f.m.procurement.replenish(id);f.m.procurement.replenish(id);eq(number(f.db.one("SELECT SUM(remaining) AS n FROM cm_orders WHERE owner=?",MarketProcurement.fund(id)),"n"),10L);eq(f.s.balance(MarketProcurement.fund(id),"KRN"),9000L);clean(f);
        }});
        test("Краща ціна повертає резерв у міський фонд, товари належать місту",()->{try(var f=new F()){
            String id=procurement(f,10000,10000,20);f.sell(e,20,80);f.m.procurement.replenish(id);eq(number(f.db.one("SELECT SUM(quantity) AS n FROM cm_assets WHERE owner='e:t:D' AND spec=?",f.iron),"n"),20L);eq(f.s.balance(MarketProcurement.fund(id),"KRN"),8400L);eq(number(f.db.one("SELECT SUM(amount) AS n FROM cm_proc_spend WHERE mandate=?",id),"n"),1600L);f.m.procurement.replenish(id);eq(f.db.query("SELECT * FROM cm_proc_orders").size(),1);clean(f);
        }});
        test("Пауза повертає резерв у фонд; зупинка повертає тільки вільний фонд казні",()->{try(var f=new F()){
            String id=procurement(f,10000,10000,20);f.m.procurement.replenish(id);f.m.procurement.state(d,req(),id,"PAUSED");eq(f.s.balance(MarketProcurement.fund(id),"KRN"),10000L);eq(f.money(D,"REFUND"),0L);f.m.procurement.state(d,req(),id,"STOPPED");eq(f.s.balance("e:t:D","KRN"),10000L);clean(f);
        }});
        test("Ринкове сховище й нестача коштів не створюють незабезпеченої закупівлі",()->{try(var f=new F()){
            String id=procurement(f,50,10000,20);f.m.procurement.replenish(id);eq(f.db.query("SELECT * FROM cm_proc_orders").size(),0);eq(f.s.balance(MarketProcurement.fund(id),"KRN"),50L);clean(f);
        }});
        test("Торговий договір не активується без усіх сторін і перегляду всіх сторінок",()->{try(var f=new F()){
            String id=treaty(f,false);no(()->f.m.treaties.accept(d,id,1,req()));var doc=f.m.treaties.document(id);eq(f.m.documents.view(d,doc,1).token(),"");sign(f,d,id);eq(number(f.db.one("SELECT active_version FROM cm_treaties WHERE id=?",id),"active_version"),0L);sign(f,e,id);eq(number(f.db.one("SELECT active_version FROM cm_treaties WHERE id=?",id),"active_version"),1L);clean(f);
        }});
        test("Чужий, старий і прострочений токени договору відхиляються",()->{try(var f=new F()){
            String id=treaty(f,false);var doc=f.m.treaties.document(id);String token=token(f,d,doc);no(()->f.m.treaties.accept(e,id,1,token));f.time.value+=60001;no(()->f.m.treaties.accept(d,id,1,token));f.m.treaties.book(d,req(),id,"MANUAL","Вільний текст не змінює комісію");no(()->f.m.treaties.accept(d,id,1,token));clean(f);
        }});
        test("Пільга зберігає тариф, резервує ліміт і не складається в від’ємний збір",()->{try(var f=new F()){
            treaty(f,true);String sell=f.sell(e,100,100);eq(number(f.m.order(sell),"bps"),50L);eq(number(f.db.one("SELECT reserved FROM cm_privilege_reservations WHERE order_id=?",sell),"reserved"),10000L);f.buy(b,10,100);eq(f.money(E,"PROCEEDS"),995L);eq(number(f.db.one("SELECT reserved FROM cm_privilege_reservations WHERE order_id=?",sell),"reserved"),9000L);f.m.cancel(e,req(),sell);eq(number(f.db.one("SELECT reserved FROM cm_privilege_reservations WHERE order_id=?",sell),"reserved"),0L);clean(f);
        }});
        test("Утрата громадянства скасовує залишок пільгової заявки, виручка зберігається",()->{try(var f=new F()){
            treaty(f,true);String sell=f.sell(e,100,100);f.buy(b,10,100);f.s.sync(new Model.Snapshot(f.entities,f.citizens));f.m.tick();eq(string(f.m.order(sell),"state"),"PRIVILEGE_ENDED");eq(f.items(E),90L);eq(f.money(E,"PROCEEDS"),995L);clean(f);
        }});
        test("Поправка не змінює чинних умов до нового погодження",()->{try(var f=new F()){
            String id=treaty(f,true);f.m.treaties.amend(d,req(),id,0,25,25,100000,f.s.now()+20*FeudalService.DAY);eq(number(f.db.one("SELECT active_version FROM cm_treaties WHERE id=?",id),"active_version"),1L);String sell=f.sell(e,10,100);eq(number(f.m.order(sell),"bps"),50L);clean(f);
        }});
        test("Книга сама не підписує васалітет і не скасовує обрану данину",()->{try(var f=new F()){
            String id=f.s.offer(a,"n:B","n:A",100,"KRN","висока","оборона",30);eq(f.s.parent("n:B"),null);f.m.documents.vassalBook(a,req(),id,"MANUAL","Без данини!");var doc=f.m.documents.vassal(id);eq(doc.lines().stream().anyMatch(s->s.contains("1.00 KRN")),true);no(()->f.s.accept(b,id));f.m.documents.acceptVassal(a,id,doc.version(),token(f,a,doc));var next=f.m.documents.vassal(id);f.m.documents.acceptVassal(b,id,next.version(),token(f,b,next));eq(f.s.parent("n:B"),"n:A");eq(number(f.s.contract("n:B"),"amount"),100L);clean(f);
        }});
        test("Contact не імітує лист-зразок чи успішне доставлення",()->{try(var f=new F()){
            String id=f.m.fairs.create(d,req(),f.market,f.p(Department.GOODS),"Ярмарок","Залізо",f.s.now()+2*FeudalService.DAY,24,"GOODS","*",0,50,50);f.m.fairs.edit(d,req(),id,"Власноруч написане запрошення");no(()->f.m.fairs.preview(d,id,f.p(Department.GOODS)));no(()->f.m.fairs.confirm(d,req(),id,2,req(),f.p(Department.GOODS)));eq(string(f.m.fairs.fair(id),"state"),"DRAFT");f.m.fairs.cancelLetter(d,req(),id);eq(string(f.m.fairs.fair(id),"state"),"DRAFT");f.m.fairs.cancel(d,req(),id);eq(string(f.m.fairs.fair(id),"state"),"CANCELLED");clean(f);
        }});
        test("Towny маршрути включають іменовані, локалізовані та форпостові варіанти",()->{
            for(String root:List.of("/t","towny:town","n","nation","місто","нація"))for(String sub:List.of("spawn","outpost"))eq(TownTravelGuard.route(root,new String[]{sub}),true);
            eq(TownTravelGuard.route("town",new String[]{"new","Town"}),false);eq(TownTravelGuard.route("home",new String[]{"spawn"}),false);
        });
        test("Міська закупівля з таймера приєднується до однієї транзакції",()->{try(var f=new F()){
            String id=procurement(f,10000,10000,20);f.time.value+=60001;f.m.tick();eq(f.db.query("SELECT * FROM cm_proc_orders WHERE mandate=?",id).size(),1);clean(f);
        }});
        test("Помилка вкладеної дії відкочує її зміни до savepoint",()->{try(var f=new F()){
            long before=f.s.balance(wallet(b),"KRN");f.db.transaction(()->{no(()->f.db.atomic(()->{f.m.move(wallet(b),wallet(e),"KRN",100);throw new IllegalArgumentException("Перевірка відкату");}));return null;});eq(f.s.balance(wallet(b),"KRN"),before);clean(f);
        }});
        test("Графік містить тільки фактичні угоди, точний обсяг і прогалини",()->{try(var f=new F()){
            f.m.order(e,req(),f.market,f.p(Department.FX),Department.FX,"","KRN","SOL","SELL",10,100,1,1,null);f.m.order(b,req(),f.market,f.p(Department.FX),Department.FX,"","KRN","SOL","BUY",5,100,1,1,null);
            var chart=f.m.charts.chart(f.market,f.p(Department.FX),"KRN/SOL",1);eq(chart.get("volume"),5L);eq(((List<?>)chart.get("points")).size(),1);f.time.value+=7200000;
            var empty=f.m.charts.chart(f.market,f.p(Department.FX),"KRN/SOL",1);eq(empty.get("volume"),0L);eq(((List<?>)empty.get("points")).size(),0);eq(((Map<?,?>)empty.get("latest")).get("price"),100L);no(()->f.m.charts.chart(f.market,f.p(Department.GOODS),"KRN/SOL",1));clean(f);
        }});
        System.out.println("Extended market scenarios: "+count);
    }
}
