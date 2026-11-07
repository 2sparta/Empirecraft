package ua.empirecraft.feudal;

import java.nio.file.*;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CityMarketTests.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import static ua.empirecraft.feudal.Database.*;
import ua.empirecraft.feudal.FeudalService.Actor;

public final class MarketFishingTests {
    static String draft(F f,MarketFishing.Rule rule)throws Exception {f.s.transfer(a,"e:n:A","e:t:D","KRN",10000);return f.m.fishing.create(d,req(),f.market,f.p(Department.GOODS),"Рибальський турнір","Риба міської річки",f.s.now()+600000,10,rule);}
    static void start(F f,String id)throws Exception {f.m.fishing.announce(d,req(),id);f.m.fishing.join(e,req(),id);f.m.fishing.join(b,req(),id);f.time.value=number(f.m.fishing.event(id),"starts");f.m.tick();}
    static boolean fish(F f,String person,long weight){return f.m.fishing.caught(req(),person,f.p(Department.GOODS),new Place(WORLD,20,63,10),"starcatcher:cod",weight);}
    static void end(F f,String id)throws Exception {f.time.value=number(f.m.fishing.event(id),"ends");f.m.tick();}
    static long wallet(F f,String person)throws Exception {return f.s.balance("p:"+person,"KRN");}
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Улов лише після реєстрації, у місті та протягом турніру; повтор гачка не рахується",()->{try(var f=new F()){
            String id=draft(f,MarketFishing.Rule.COUNT);f.m.fishing.money(d,req(),id,1,"KRN",1000,true);f.m.fishing.announce(d,req(),id);f.m.fishing.join(e,req(),id);eq(fish(f,E,1000),false);
            f.time.value=number(f.m.fishing.event(id),"starts");eq(fish(f,B,1000),false);String hook=req();eq(f.m.fishing.caught(hook,E,f.p(Department.GOODS),new Place("elsewhere",1,2,3),"starcatcher:cod",1000),false);
            eq(f.m.fishing.caught(hook,E,f.p(Department.GOODS),f.p(Department.GOODS),"starcatcher:cod",1000),true);eq(f.m.fishing.caught(hook,E,f.p(Department.GOODS),f.p(Department.GOODS),"starcatcher:cod",1000),false);eq(number(f.m.fishing.scores(id).get(0),"score"),1L);
            f.owns=false;eq(fish(f,E,1000),false);f.owns=true;end(f,id);eq(fish(f,E,1000),false);
        }});
        test("Вага й найбільша риба дають різний залік; нечисла та чужі предмети не проходять",()->{for(var rule:List.of(MarketFishing.Rule.TOTAL_WEIGHT,MarketFishing.Rule.LARGEST))try(var f=new F()){
            String id=draft(f,rule);f.m.fishing.money(d,req(),id,1,"KRN",1000,true);start(f,id);fish(f,E,3000);fish(f,E,5000);fish(f,E,1000);eq(number(f.m.fishing.scores(id).get(0),"score"),rule==MarketFishing.Rule.TOTAL_WEIGHT?9000L:5000L);
            eq(fish(f,E,-1),false);eq(f.m.fishing.caught(req(),E,f.p(Department.GOODS),f.p(Department.GOODS),"not a registry key",1000),false);
        }});
        test("Справжня невагома риба зараховується без вигаданої ваги й може виграти",()->{for(var rule:MarketFishing.Rule.values())try(var f=new F()){
            String id=draft(f,rule);long e0=wallet(f,E);f.m.fishing.money(d,req(),id,1,"KRN",1000,true);start(f,id);eq(fish(f,E,0),true);eq(number(f.m.fishing.scores(id).get(0),"score"),rule==MarketFishing.Rule.COUNT?1L:0L);eq(number(f.m.fishing.scores(id).get(0),"catches"),1L);eq(string(f.m.fishing.scores(id).get(0),"person"),E);end(f,id);eq(wallet(f,E),e0+1000);
        }});
        test("Внесок сплачується один раз і весь фонд отримує переможець",()->{try(var f=new F()){
            String id=draft(f,MarketFishing.Rule.COUNT);f.m.fishing.entryFee(d,req(),id,250,"KRN");long e0=wallet(f,E),b0=wallet(f,B);start(f,id);f.m.fishing.join(e,req(),id);eq(wallet(f,E),e0-250);eq(f.s.balance(MarketFishing.entries(id),"KRN"),500L);
            fish(f,E,1000);end(f,id);f.m.tick();eq(wallet(f,E),e0+250);eq(wallet(f,B),b0-250);eq(f.s.balance(MarketFishing.entries(id),"KRN"),0L);
        }});
        test("Недостатньо коштів — немає реєстрації; умови внеску незмінні після оголошення",()->{try(var f=new F()){
            String id=draft(f,MarketFishing.Rule.COUNT);no(()->f.m.fishing.entryFee(e,req(),id,100,"KRN"));no(()->f.m.fishing.entryFee(d,req(),id,-1,"KRN"));f.m.fishing.entryFee(d,req(),id,100,"KRN");f.m.fishing.announce(d,req(),id);
            no(()->f.m.fishing.join(c,req(),id));eq(f.m.fishing.scores(id).size(),0);eq(f.s.balance(MarketFishing.entries(id),"KRN"),0L);no(()->f.m.fishing.entryFee(d,req(),id,0,""));
        }});
        test("Безкоштовна участь не потребує гаманця, валюти або внеску",()->{try(var f=new F()){
            String id=draft(f,MarketFishing.Rule.COUNT);f.m.fishing.entryFee(d,req(),id,0,"-");f.m.fishing.money(d,req(),id,1,"KRN",100,true);f.m.fishing.announce(d,req(),id);f.m.fishing.join(c,req(),id);eq(number(f.m.fishing.scores(id).get(0),"paid"),0L);
        }});
        test("Скасування й відсутність залікового улову повертають внески та призи",()->{for(boolean cancel:List.of(false,true))try(var f=new F()){
            String id=draft(f,MarketFishing.Rule.COUNT);long city=f.s.balance("e:t:D","KRN"),e0=wallet(f,E),b0=wallet(f,B);f.m.fishing.entryFee(d,req(),id,100,"KRN");f.m.fishing.money(d,req(),id,1,"KRN",1000,true);start(f,id);
            if(cancel)f.m.fishing.cancel(d,req(),id);else end(f,id);f.m.tick();eq(wallet(f,E),e0);eq(wallet(f,B),b0);eq(f.s.balance("e:t:D","KRN"),city);eq(f.s.balance(MarketFishing.entries(id),"KRN"),0L);
        }});
        test("Предмети резервуються фактично, невдала передача не створює приз; видача рівно один раз",()->{try(var f=new F()){
            String id=draft(f,MarketFishing.Rule.COUNT);String abort=f.m.fishing.items(d,req(),id,1,f.iron,5,f.p(Department.GOODS),change(d));no(()->f.m.fishing.announce(d,req(),id));f.commerce.finish(abort,false);no(()->f.m.fishing.announce(d,req(),id));
            String op=f.m.fishing.items(d,req(),id,1,f.iron,10,f.p(Department.GOODS),change(d));f.commerce.finish(op,true);f.commerce.finish(op,true);start(f,id);fish(f,E,1000);end(f,id);f.m.tick();eq(f.items(E),10L);eq(f.m.fishing.prizes(id).stream().filter(p->string(p,"state").equals("PAID")).count(),1L);
        }});
        test("Скасована передача предметів повертається після завершення журналу",()->{try(var f=new F()){
            String id=draft(f,MarketFishing.Rule.COUNT),op=f.m.fishing.items(d,req(),id,1,f.iron,7,f.p(Department.GOODS),change(d));f.m.fishing.cancel(d,req(),id);f.commerce.finish(op,true);f.commerce.finish(op,true);eq(f.items(D),7L);
        }});
        test("Нічия вирішується часом досягнення, відсутнє третє місце повертає резерв",()->{try(var f=new F()){
            String id=draft(f,MarketFishing.Rule.LARGEST);f.m.fishing.money(d,req(),id,1,"KRN",1000,true);String third=f.m.fishing.money(d,req(),id,3,"KRN",300,true);long b0=wallet(f,B);start(f,id);fish(f,B,1000);f.time.value++;fish(f,E,1000);end(f,id);eq(wallet(f,B),b0+1000);eq(f.s.balance(MarketFishing.fund(third),"KRN"),0L);eq(string(f.m.fishing.prizes(id).stream().filter(p->string(p,"id").equals(third)).findFirst().orElseThrow(),"state"),"REFUNDED");
        }});
        test("Втрата території скасовує турнір; рибальські оголошення не змінюють ринкові тарифи",()->{try(var f=new F()){
            String id=draft(f,MarketFishing.Rule.COUNT);f.m.fishing.entryFee(d,req(),id,100,"KRN");long e0=wallet(f,E);start(f,id);eq(f.m.fairs.benefits(f.market,Department.GOODS,f.iron,"buyer").size(),0);f.owns=false;f.m.tick();eq(string(f.m.fishing.event(id),"state"),"CANCELLED");eq(wallet(f,E),e0);
        }});
        test("Запрошення Contact працює після оголошення, abort зберігає подію, повторна розсилка блокується",()->{try(var f=new F()){
            var mail=new MarketMailTests.Mail(2);mail.install(f);String id=draft(f,MarketFishing.Rule.COUNT);f.m.fishing.entryFee(d,req(),id,100,"KRN");f.m.fishing.announce(d,req(),id);String sample=MarketMailTests.sample(f,id),op=MarketMailTests.confirm(f,id,sample);f.commerce.finish(op,false);eq(string(f.m.fishing.event(id),"state"),"ANNOUNCED");
            f.commerce.finish(MarketMailTests.confirm(f,id,sample),true);f.m.fairs.mail.deliver();eq(mail.inserted,2);no(()->f.m.fairs.preview(d,id,f.p(Department.GOODS)));no(()->f.m.fairs.cancel(d,req(),id));
        }});
        test("Перезапуск зберігає залік, нагороди та вступний фонд без повторної виплати",()->{
            Path dir=Files.createTempDirectory("fishing-restart");String url="jdbc:h2:"+dir.resolve("db").toAbsolutePath()+";DATABASE_TO_LOWER=TRUE";String id;long e0,ends;
            try(var f=new F(url,true)){id=draft(f,MarketFishing.Rule.COUNT);e0=wallet(f,E);f.m.fishing.entryFee(d,req(),id,100,"KRN");f.m.fishing.money(d,req(),id,1,"KRN",1000,true);start(f,id);fish(f,E,1000);ends=number(f.m.fishing.event(id),"ends");}
            try(var f=new F(url,false)){eq(number(f.m.fishing.scores(id).get(0),"score"),1L);f.time.value=ends;f.m.tick();eq(wallet(f,E),e0+1100);}
            try(var f=new F(url,false)){f.time.value=ends+1;f.m.tick();eq(wallet(f,E),e0+1100);eq(string(f.m.fishing.event(id),"state"),"FINISHED");}
        });
        System.out.println("MARKET_FISHING_TESTS_PASS scenarios="+count);
    }
}
