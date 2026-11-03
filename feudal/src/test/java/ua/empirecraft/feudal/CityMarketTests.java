package ua.empirecraft.feudal;

import java.nio.file.*;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CurrencyTests.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Real SQL, fake clock and physical coordinates; no in-memory ledger substitute. */
public final class CityMarketTests {
    static final String WORLD="world";
    static String req(){return UUID.randomUUID().toString();}
    static CommerceService.InventoryChange change(Actor a){return new CommerceService.InventoryChange(a.id(),"before","after");}
    static final class F extends Fixture {
        final CommerceService commerce;final CityMarketService m;final String market,iron;boolean owns=true;
        F()throws Exception {this("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE",true);}
        F(String url,boolean setup)throws Exception {super(url);commerce=new CommerceService(s);m=new CityMarketService(s,commerce);m.land=(t,w,x,z)->owns;
            if(setup){currency(this,a,"n:A","KRN",1000,10000000);currency(this,b,"n:B","SOL",1000,10000000);s.transfer(a,"e:n:A","p:"+B,"KRN",1000000);s.transfer(a,"e:n:A","p:"+E,"KRN",1000000);s.transfer(b,"e:n:B","p:"+B,"SOL",1000000);s.transfer(b,"e:n:B","p:"+E,"SOL",1000000);
                market=m.create(d,req(),"t:D",WORLD,"Київський","Опис");for(int x=0;x<4;x++)m.claim(admin,req(),market,x,0,true,"Початкова зона");
                for(var dep:Department.values())building(dep);m.submit(d,req(),market);m.state(admin,req(),market,"OPEN","Оглянуто");m.permit(d,req(),market,"KRN","payment",true);iron=m.registerSpec("iron:all-tags",false);
                // Currency issuer authority is independently tested against CurrencyMarket below.
                for(String code:List.of("KRN","SOL"))db.execute("MERGE INTO cm_permits KEY(market,code) VALUES(?,?,TRUE,TRUE,TRUE)",market,code);
            }else {market=string(db.one("SELECT id FROM cm_markets LIMIT 1"),"id");iron=string(db.one("SELECT id FROM cm_specs LIMIT 1"),"id");}
        }
        void building(Department dep)throws Exception {int x=dep.ordinal()*16;m.building(admin,req(),market,dep,new Box(x,60,0,x+15,80,15),x+2,64,2,"STONE","Окрема будівля");m.reviewDepartment(admin,req(),market,dep,true,"Огляд споруди");}
        Place p(Department dep){return new Place(WORLD,dep.ordinal()*16+2.5,64,2.5);}
        String sell(Actor a,long n,long price)throws Exception {String op=m.order(a,req(),market,p(Department.GOODS),Department.GOODS,iron,"","KRN","SELL",n,price,3,number(m.tariff(market),"version"),change(a));commerce.finish(op,true);return string(db.one("SELECT related FROM cm_transfers WHERE op=?",op),"related");}
        String buy(Actor a,long n,long price)throws Exception {return m.order(a,req(),market,p(Department.GOODS),Department.GOODS,iron,"","KRN","BUY",n,price,3,number(m.tariff(market),"version"),null);}
        long items(String person)throws Exception {long n=0;for(var r:db.query("SELECT quantity FROM cm_assets WHERE owner=? AND spec=?","p:"+person,iron))n+=number(r,"quantity");return n;}
        long money(String person,String category)throws Exception {long n=0;for(var r:db.query("SELECT quantity FROM cm_assets WHERE owner=? AND code='KRN' AND category=?","p:"+person,category))n+=number(r,"quantity");return n;}
        String asset(String person,String category)throws Exception {return string(db.one("SELECT id FROM cm_assets WHERE owner=? AND category=? AND quantity>0","p:"+person,category),"id");}
        String loan()throws Exception {m.credit.license(d,req(),market,B,"KRN",iron,100000,200000,1000,30,2,true);m.credit.fund(b,req(),market,p(Department.CREDIT),"KRN",20000,true);String offer=m.credit.offer(b,req(),market,p(Department.CREDIT),"KRN",10000,800,14,iron,64);String loan=m.credit.apply(e,req(),offer,p(Department.CREDIT));m.credit.approve(b,req(),loan,p(Department.CREDIT));return loan;}
        void issue(String loan)throws Exception {String op=m.credit.take(e,req(),loan,p(Department.CREDIT),change(e));commerce.finish(op,true);}
        String work()throws Exception {return m.contracts.create(b,req(),market,p(Department.JOBS),"Збудувати міст; координати 10 64 10","KRN",10000,3,"",0,false,List.of(4000L,6000L),List.of("Опори","Прохід"),1);}
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Кожен відділ має власну 3D будівлю, стійку й стан",()->{try(var f=new F()){
            for(var dep:Department.values())f.m.access(f.market,dep,f.p(dep));no(()->f.m.access(f.market,Department.FX,f.p(Department.GOODS)));no(()->f.m.access(f.market,Department.GOODS,new Place(WORLD,2.5,59,2.5)));no(()->f.m.access(f.market,Department.GOODS,new Place("nether",2.5,64,2.5)));no(()->f.m.access(f.market,Department.GOODS,new Place(WORLD,14.5,64,14.5)));
            f.m.reviewDepartment(admin,req(),f.market,Department.FX,false,"Знесення");f.m.access(f.market,Department.GOODS,f.p(Department.GOODS));no(()->f.m.access(f.market,Department.FX,f.p(Department.FX)));no(()->f.m.observe(b,f.market,f.p(Department.FX)));
        }});
        test("Проєкт не торгує; спільні будівлі й перенесення діючого ринку заборонені",()->{try(var f=new F()){
            String draft=f.m.create(d,req(),"t:D",WORLD,"Новий","Опис");no(()->f.m.order(b,req(),draft,f.p(Department.GOODS),Department.GOODS,f.iron,"","KRN","BUY",1,100,1,1,null));no(()->f.m.claim(admin,req(),f.market,4,0,true,"Перенесення"));no(()->f.m.building(admin,req(),f.market,Department.FX,new Box(0,60,0,15,80,15),2,64,2,"STONE","Спільна будівля"));
        }});
        test("Приклад ТЗ: 256 заліза, купівля 128 до 2.50, правильні збори та повернення",()->{try(var f=new F()){
            String sell=f.sell(e,256,200),buy=f.buy(b,128,250);eq(number(f.m.order(sell),"remaining"),128L);eq(string(f.m.order(buy),"state"),"FILLED");eq(f.items(B),128L);eq(f.money(E,"PROCEEDS"),25088L);eq(f.money(B,"REFUND"),6400L);eq(f.s.balance("e:t:D","KRN"),512L);eq(f.s.balance(account(buy),"KRN"),0L);clean(f);
        }});
        test("Price/time і ціна старої заявки; покупець перший у книзі",()->{try(var f=new F()){
            String buy=f.buy(b,10,250),sell=f.sell(e,4,200);eq(number(f.m.order(buy),"remaining"),6L);eq(number(f.db.one("SELECT price FROM cm_fills"),"price"),250L);eq(f.items(B),4L);eq(f.money(E,"PROCEEDS"),980L);f.m.cancel(b,req(),buy);eq(f.money(B,"REFUND"),1500L);clean(f);
        }});
        test("Самозіставлення скасовує залишок без обсягу й без втрати майна",()->{try(var f=new F()){
            f.sell(b,10,100);String buy=f.buy(b,10,100);eq(string(f.m.order(buy),"state"),"SELF_CROSS");eq(f.db.query("SELECT * FROM cm_fills").size(),0);eq(f.money(B,"REFUND"),1000L);clean(f);
        }});
        test("Скасування поза ринком повертає в місцеве сховище один раз",()->{try(var f=new F()){
            String sell=f.sell(e,10,100);String r=req();f.m.cancel(e,r,sell);f.m.cancel(e,r,sell);eq(f.items(E),10L);no(()->f.m.collectItems(e,req(),f.asset(E,"REFUND"),10,new Place(WORLD,100,64,100),false,change(e)));clean(f);
        }});
        test("Ідемпотентність та повтор ID з іншими умовами",()->{try(var f=new F()){
            String request=req();String one=f.m.order(b,request,f.market,f.p(Department.GOODS),Department.GOODS,f.iron,"","KRN","BUY",5,100,1,1,null);eq(f.m.order(b,request,f.market,null,Department.GOODS,f.iron,"","KRN","BUY",5,100,1,1,null),one);no(()->f.m.order(e,request,f.market,null,Department.GOODS,f.iron,"","KRN","BUY",5,100,1,1,null));no(()->f.m.order(b,request,f.market,null,Department.GOODS,f.iron,"","KRN","BUY",6,100,1,1,null));clean(f);
        }});
        test("Зменшення залишку зберігає пріоритет, резерв і тариф",()->{try(var f=new F()){
            String buy=f.buy(b,10,100);long before=number(f.m.order(buy),"priority");f.m.reduce(b,req(),buy,4);eq(number(f.m.order(buy),"priority"),before);eq(f.money(B,"REFUND"),600L);eq(f.s.balance(account(buy),"KRN"),400L);f.sell(e,4,90);eq(f.items(B),4L);clean(f);
        }});
        test("Тариф діє через 24 години; старі заявки не переписуються",()->{try(var f=new F()){
            String old=f.sell(e,10,100);f.m.tariff(d,req(),f.market,500,500,300);eq(number(f.m.tariff(f.market),"version"),1L);f.time.value+=FeudalService.DAY;eq(number(f.m.tariff(f.market),"version"),2L);f.buy(b,5,100);eq(number(f.m.order(old),"bps"),200L);eq(f.s.balance("e:t:D","KRN"),35L);clean(f);
        }});
        test("Накопичене округлення збору однакове для часткових виконань",()->{try(var f=new F()){
            String buy=f.buy(b,100,1);for(int i=0;i<10;i++)f.sell(e,10,1);eq(number(f.m.order(buy),"volume"),100L);eq(f.items(B),100L);eq(f.s.balance("e:t:D","KRN"),0L);clean(f);
            eq(fee(99,200),1L);eq(fee(100,200)-fee(99,200),1L);
        }});
        test("Точні характеристики предметів створюють різні книги",()->{try(var f=new F()){
            f.sell(e,10,100);String damaged=f.m.registerSpec("iron:other-NBT",false);String buy=f.m.order(b,req(),f.market,f.p(Department.GOODS),Department.GOODS,damaged,"","KRN","BUY",10,100,1,1,null);eq(number(f.m.order(buy),"remaining"),10L);eq(f.items(B),0L);clean(f);
        }});
        test("Втрата території зупиняє економіку, строки й скасування працюють",()->{try(var f=new F()){
            String sell=f.sell(e,10,100);f.owns=false;no(()->f.buy(b,10,100));f.time.value+=4*FeudalService.DAY;f.m.tick();eq(string(f.m.market(f.market),"state"),"SUSPENDED");eq(string(f.m.order(sell),"state"),"EXPIRED");eq(f.items(E),10L);clean(f);
        }});
        test("Видача резервується до інвентарю; abort відновлює актив, повтор finish безпечний",()->{try(var f=new F()){
            String sell=f.sell(e,10,100);f.m.cancel(e,req(),sell);String asset=f.asset(E,"REFUND"),op=f.m.collectItems(e,req(),asset,4,f.p(Department.GOODS),false,change(e));eq(f.items(E),6L);f.commerce.finish(op,false);f.commerce.finish(op,false);eq(f.items(E),10L);String op2=f.m.collectItems(e,req(),asset,4,f.p(Department.GOODS),false,change(e));f.commerce.finish(op2,true);f.commerce.finish(op2,true);eq(f.items(E),6L);clean(f);
        }});
        test("Приватний FX не змінює емісію або золото; зворотної книги немає",()->{try(var f=new F()){
            long supply=number(f.s.currency("KRN"),"supply");var reserve=f.s.currencies.reserve("KRN");String sell=f.m.order(e,req(),f.market,f.p(Department.FX),Department.FX,"","KRN","SOL","SELL",100,200,3,1,null);f.m.order(b,req(),f.market,f.p(Department.FX),Department.FX,"","KRN","SOL","BUY",20,210,3,1,null);eq(number(f.m.order(sell),"remaining"),80L);eq(number(f.s.currency("KRN"),"supply"),supply);eq(f.s.currencies.reserve("KRN"),reserve);no(()->f.m.order(b,req(),f.market,f.p(Department.FX),Department.FX,"","SOL","KRN","BUY",1,100,3,1,null));clean(f);
        }});
        test("Відкликання допусків не змішує валютний і товарний облік",()->{try(var f=new F()){
            String goods=f.buy(b,2,100);String fx=f.m.order(b,req(),f.market,f.p(Department.FX),Department.FX,"","KRN","SOL","BUY",10,200,3,1,null);f.m.permit(d,req(),f.market,"KRN","host",false);eq(string(f.m.order(fx),"state"),"REVOKED");eq(string(f.m.order(goods),"state"),"OPEN");f.m.permit(d,req(),f.market,"KRN","payment",false);eq(string(f.m.order(goods),"state"),"REVOKED");clean(f);
        }});
        test("Знімки містять порожню книгу, передавання зберігає номер і дату",()->{try(var f=new F()){
            f.m.observe(b,f.market,f.p(Department.GOODS));var snapshot=f.db.one("SELECT * FROM cm_snapshots WHERE person=?",B);eq(string(snapshot,"data").contains("немає пропозицій"),true);f.time.value+=86400000;f.m.share(b,req(),E);var copy=f.db.one("SELECT * FROM cm_snapshots WHERE person=?",E);eq(number(copy,"observed"),number(snapshot,"observed"));eq(number(copy,"observation"),number(snapshot,"observation"));f.m.observe(e,f.market,f.p(Department.GOODS));long newer=number(f.db.one("SELECT observation FROM cm_snapshots WHERE person=?",E),"observation");f.time.value+=30000;f.m.share(b,req(),E);eq(number(f.db.one("SELECT observation FROM cm_snapshots WHERE person=?",E),"observation"),newer);
        }});
        test("Кредит резервує капітал і ліміт до видачі; схвалення не створює боргу",()->{try(var f=new F()){
            String loan=f.loan();eq(string(f.m.credit.loan(loan),"state"),"APPROVED");eq(number(f.m.credit.loan(loan),"due"),0L);eq(f.s.balance(account(loan),"KRN"),10000L);f.time.value+=FeudalService.DAY;f.m.tick();eq(string(f.m.credit.loan(loan),"state"),"CANCELLED");eq(f.s.balance(account(loan),"KRN"),0L);clean(f);
        }});
        test("Неможливо погодити дві позики на один фонд",()->{try(var f=new F()){
            String loan=f.loan();String offer=string(f.m.credit.loan(loan),"offer");String l2=f.m.credit.apply(e,req(),offer,f.p(Department.CREDIT));f.m.credit.approve(b,req(),l2,f.p(Department.CREDIT));String l3=f.m.credit.apply(e,req(),offer,f.p(Department.CREDIT));no(()->f.m.credit.approve(b,req(),l3,f.p(Department.CREDIT)));eq(f.s.balance(account(l3),"KRN"),0L);clean(f);
        }});
        test("Дистанційне часткове погашення: відсотки першими; застава повертається один раз",()->{try(var f=new F()){
            String loan=f.loan();f.issue(loan);f.m.credit.repay(e,req(),loan,800);eq(number(f.m.credit.loan(loan),"paid"),800L);eq(f.money(B,"LOAN_PAYMENT"),800L);String request=req();f.m.credit.repay(e,request,loan,10000);f.m.credit.repay(e,request,loan,10000);eq(string(f.m.credit.loan(loan),"state"),"REPAID");eq(f.items(E),64L);no(()->f.m.credit.foreclose(b,req(),loan));clean(f);
        }});
        test("Прострочення: застава погашає весь борг; пізніший платіж відхиляється",()->{try(var f=new F()){
            String loan=f.loan();f.issue(loan);f.time.value+=15*FeudalService.DAY+1;f.m.credit.foreclose(b,req(),loan);eq(string(f.m.credit.loan(loan),"state"),"SETTLED");eq(f.items(B),64L);no(()->f.m.credit.repay(e,req(),loan,100));clean(f);
        }});
        test("Відкликання ліцензії звільняє схвалення і зберігає виданий борг",()->{try(var f=new F()){
            String loan=f.loan();f.issue(loan);String offer=string(f.m.credit.loan(loan),"offer"),second=f.m.credit.apply(e,req(),offer,f.p(Department.CREDIT));f.m.credit.approve(b,req(),second,f.p(Department.CREDIT));f.m.credit.license(d,req(),f.market,B,"KRN",f.iron,100000,200000,1000,30,2,false);eq(string(f.m.credit.loan(loan),"state"),"ACTIVE");eq(string(f.m.credit.loan(second),"state"),"CANCELLED");f.m.credit.repay(e,req(),loan,10800);clean(f);
        }});
        test("Постачання: часткова точна оплата й місцеве отримання ресурсів",()->{try(var f=new F()){
            String c=f.m.contracts.create(b,req(),f.market,f.p(Department.JOBS),"Залізо для мосту","KRN",10000,3,f.iron,100,true,List.of(),List.of(),1);String op=f.m.contracts.deliver(e,req(),c,30,f.p(Department.JOBS),false,change(e));f.commerce.finish(op,true);eq(f.money(E,"WORK_PAYMENT"),2940L);eq(f.items(B),30L);f.m.contracts.cancel(b,req(),c);eq(f.money(B,"CONTRACT_REFUND"),7000L);clean(f);
        }});
        test("Роботи: після прийняття оплата не вилучається; мовчання створює спір",()->{try(var f=new F()){
            String c=f.work();f.m.contracts.accept(e,req(),c,f.p(Department.JOBS));no(()->f.m.contracts.cancel(b,req(),c));f.m.contracts.submit(e,req(),c,1,"Опори готові, координати та докази");f.time.value+=2*FeudalService.DAY;f.m.tick();eq(string(f.m.contracts.contract(c),"state"),"DISPUTE");eq(f.s.balance(account(c),"KRN"),10000L);no(()->f.m.contracts.arbitrate(new Actor(B,true),req(),c,5000,"Самоарбітраж"));f.m.contracts.arbitrate(admin,req(),c,3000,"Часткове виконання підтверджено");eq(f.money(E,"WORK_PAYMENT"),2940L);eq(f.money(B,"CONTRACT_REFUND"),7000L);clean(f);
        }});
        test("Зміна мера не передає особисті фонди й активи",()->{try(var f=new F()){
            String sell=f.sell(e,10,100);f.m.cancel(e,req(),sell);var estates=new ArrayList<>(f.entities);estates.set(3,new Model.Estate("t:D",Model.Kind.BARONY,"Київ","n:A",A,true));f.s.sync(new Model.Snapshot(estates,f.citizens));no(()->f.m.collectMoney(a,req(),f.asset(E,"REFUND"),f.p(Department.GOODS),false));no(()->f.m.manage(d,f.market));eq(f.items(E),10L);clean(f);
        }});
        test("Закриття зберігає предмети, борги й повернення грошей без доступу в місто",()->{try(var f=new F()){
            String loan=f.loan();f.issue(loan);String buy=f.buy(b,10,100);f.m.state(admin,req(),f.market,"CLOSED","Закриття");eq(string(f.m.order(buy),"state"),"CLOSED");String asset=f.asset(B,"REFUND");f.m.collectMoney(b,req(),asset,null,true);f.m.credit.repay(e,req(),loan,10800);eq(f.items(E),64L);clean(f);
        }});
        test("Перезапуск з PREPARED продажем та видачею не дублює результат",()->{
            Path root=Files.createTempDirectory("city-market-tests-");String url="jdbc:h2:"+root.resolve("db")+";DATABASE_TO_LOWER=TRUE";String op,market;
            try(var f=new F(url,true)){market=f.market;op=f.m.order(e,req(),market,f.p(Department.GOODS),Department.GOODS,f.iron,"","KRN","SELL",10,100,3,1,change(e));}
            try(var f=new F(url,false)){f.commerce.finish(op,true);f.commerce.finish(op,true);String sell=string(f.db.one("SELECT related FROM cm_transfers WHERE op=?",op),"related");f.m.cancel(e,req(),sell);eq(f.items(E),10L);String asset=f.asset(E,"REFUND");op=f.m.collectItems(e,req(),asset,4,f.p(Department.GOODS),false,change(e));}
            try(var f=new F(url,false)){f.commerce.finish(op,true);f.commerce.finish(op,true);eq(f.items(E),6L);clean(f);}remove(root);
        });
        test("Міграція повертає старий резерв один раз і зберігає предмети",()->{try(var f=new Fixture()){
            f.seed(b,1000);var commerce=new CommerceService(f.s);String old=commerce.create(b,"p:"+B,"old-item",100,"BASE",1000,7);String op=commerce.deliver(e,old,10,change(e));commerce.finish(op,true);var m=new CityMarketService(f.s,commerce);m.migrate();m.migrate();eq(f.s.balance("p:"+B,"BASE"),900L);eq(number(commerce.order(old),"unclaimed"),10L);eq(string(commerce.order(old),"status"),"MIGRATED");no(()->commerce.create(b,"p:"+B,"x",1,"BASE",1,1));no(()->commerce.deliver(e,old,1,change(e)));
        }});
        test("Емітентський допуск перевіряє реальні повноваження Банкіра",()->{try(var f=new F()){
            var bank=CurrencyMarketTests.market(f);CurrencyMarketTests.county(f);no(()->f.m.permit(e,req(),f.market,"KRN","issuer",false));f.m.permit(a,req(),f.market,"KRN","issuer",false);eq(f.m.db.one("SELECT issuer FROM cm_permits WHERE market=? AND code='KRN'",f.market).get("issuer"),false);
        }});
        test("Відновлений продаж отримує пріоритет лише після внесення предметів",()->{try(var f=new F()){
            String op=f.m.order(e,req(),f.market,f.p(Department.GOODS),Department.GOODS,f.iron,"","KRN","SELL",10,100,3,1,change(e));String buy=f.buy(b,10,200);f.commerce.finish(op,true);eq(number(f.db.one("SELECT price FROM cm_fills"),"price"),200L);eq(string(f.m.order(buy),"state"),"FILLED");clean(f);
        }});
        test("Призупинений відділ зберігає внесені резерви; відновлення зіставляє їх",()->{try(var f=new F()){
            String op=f.m.order(e,req(),f.market,f.p(Department.GOODS),Department.GOODS,f.iron,"","KRN","SELL",10,100,3,1,change(e));f.buy(b,10,200);f.m.reviewDepartment(admin,req(),f.market,Department.GOODS,false,"Пошкодження будівлі");f.commerce.finish(op,true);eq(f.db.query("SELECT * FROM cm_fills").size(),0);f.m.reviewDepartment(admin,req(),f.market,Department.GOODS,true,"Повторний огляд");eq(f.items(B),10L);clean(f);
        }});
        test("Журнал руху зберігає ID дії та точні баланси до й після",()->{try(var f=new F()){
            String request=req();f.m.order(b,request,f.market,f.p(Department.GOODS),Department.GOODS,f.iron,"","KRN","BUY",10,100,3,1,null);var ledger=f.db.one("SELECT * FROM cm_ledger WHERE action=?",request);eq(number(ledger,"source_before")-number(ledger,"source_after"),1000L);eq(number(ledger,"target_after")-number(ledger,"target_before"),1000L);eq(string(ledger,"actor"),B);clean(f);
        }});
        test("Погоджене продовження позики не змінює відсотки; одного підпису недостатньо",()->{try(var f=new F()){
            String loan=f.loan();f.issue(loan);long due=number(f.m.credit.loan(loan),"due");String proposal=f.m.agreements.propose(e,req(),"LOAN",loan,30,0,"Подовжити строк");eq(number(f.m.credit.loan(loan),"due"),due);no(()->f.m.agreements.accept(e,req(),proposal));no(()->f.m.agreements.accept(d,req(),proposal));f.m.agreements.accept(b,req(),proposal);eq(number(f.m.credit.loan(loan),"due"),f.time.value+30*FeudalService.DAY);eq(number(f.m.credit.loan(loan),"interest"),800L);clean(f);
        }});
        test("Погодження втраченої версії договору відхиляється",()->{try(var f=new F()){
            String c=f.work();f.m.contracts.accept(e,req(),c,f.p(Department.JOBS));String proposal=f.m.agreements.propose(b,req(),"WORK",c,10,0,"Новий опис");f.m.contracts.submit(e,req(),c,1,"Виконано опори");no(()->f.m.agreements.accept(e,req(),proposal));eq(string(f.m.contracts.contract(c),"description"),"Збудувати міст; координати 10 64 10");clean(f);
        }});
        test("Розірвання роботи за двома підписами розподіляє лише наявний резерв",()->{try(var f=new F()){
            String c=f.work();f.m.contracts.accept(e,req(),c,f.p(Department.JOBS));String proposal=f.m.agreements.propose(b,req(),"TERMINATE",c,0,4000,"Погоджене припинення");f.m.agreements.accept(e,req(),proposal);eq(string(f.m.contracts.contract(c),"state"),"TERMINATED");eq(f.money(E,"WORK_PAYMENT"),3920L);eq(f.money(B,"CONTRACT_REFUND"),6000L);clean(f);
        }});
        test("Неприйняті зразки очищуються; специфікації власних активів зберігаються",()->{try(var f=new F()){
            String unused=f.m.registerSpec("unused-preview",false),sell=f.sell(e,2,100);f.m.cancel(e,req(),sell);f.time.value+=600001;f.m.tick();eq(f.db.one("SELECT id FROM cm_specs WHERE id=?",unused),null);eq(f.m.spec(f.iron).get("item"),"iron:all-tags");eq(f.items(E),2L);
        }});
        test("Збільшення числа ринків вимагає окремого рішення адміністратора",()->{try(var f=new F()){
            no(()->f.m.cityLimit(d,req(),f.market,2,"Самостійне рішення мера"));f.m.cityLimit(admin,req(),f.market,2,"Дозволено другий комплекс");eq(number(f.db.one("SELECT amount FROM cm_city_limits WHERE town='t:D'"),"amount"),2L);no(()->f.m.cityLimit(admin,req(),f.market,0,"Некоректний ліміт"));
        }});
        test("Угоди відомих пов’язаних акаунтів позначаються без зміни розрахунків",()->{try(var f=new F()){
            f.m.related(admin,req(),f.market,B,E,"Встановлений адміністрацією зв’язок");f.sell(e,10,100);f.buy(b,10,100);eq(f.db.query("SELECT seq FROM cm_events WHERE event='FILL_REVIEW'").size(),1);eq(f.items(B),10L);clean(f);
        }});
        test("Відмова від неотриманої позики звільняє резерв, не зачіпаючи виданий борг",()->{try(var f=new F()){
            String loan=f.loan();no(()->f.m.credit.cancel(d,req(),loan));f.m.credit.cancel(e,req(),loan);eq(string(f.m.credit.loan(loan),"state"),"CANCELLED");eq(f.s.balance(account(loan),"KRN"),0L);String offer=string(f.m.credit.loan(loan),"offer");f.m.credit.closeOffer(b,req(),offer);no(()->f.m.credit.apply(e,req(),offer,f.p(Department.CREDIT)));clean(f);
        }});
        System.out.println("City market scenarios: "+count);
    }
}
