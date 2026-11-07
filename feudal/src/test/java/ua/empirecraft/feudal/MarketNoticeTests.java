package ua.empirecraft.feudal;

import java.nio.file.*;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CityMarketTests.req;
import static ua.empirecraft.feudal.Database.*;
import ua.empirecraft.feudal.CityMarketService.Place;

/** Real H2 fee escrow, municipal moderation and public visibility. */
public final class MarketNoticeTests {
    static final Place BOARD=new Place("world",3,64,3);
    static final class F extends CityMarketTests.F {
        F()throws Exception {super();m.notices.bind(d,req(),market,BOARD);}
        long version()throws Exception {return number(m.notices.settings(market),"version");}
        String post(ua.empirecraft.feudal.FeudalService.Actor actor)throws Exception {return m.notices.submit(actor,req(),market,BOARD,"Продаю човен","Справжній човен біля пристані",7,version());}
        long wallet(String person)throws Exception {return s.balance("p:"+person,"KRN");}
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Публікація сплачує місту один раз і з’являється на фізичній дошці",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,1250,"KRN",false);long before=f.wallet(E),city=f.s.balance("e:t:D","KRN");String request=req();
            String id=f.m.notices.submit(e,request,f.market,BOARD,"Човен","Опис човна",7,f.version());f.m.notices.submit(e,request,f.market,BOARD,"Човен","Опис човна",7,f.version());
            eq(f.wallet(E),before-1250);eq(f.s.balance("e:t:D","KRN"),city+1250);eq(f.m.notices.publicEntries(f.market,BOARD,1).size(),1);eq(string(f.m.notices.notice(id),"state"),"PUBLISHED");
        }});
        test("Безкоштовне оголошення не потребує валюти чи коштів",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,0,"-",false);String id=f.post(c);eq(number(f.m.notices.notice(id),"fee"),0L);eq(string(f.m.notices.notice(id),"code"),"");eq(f.m.notices.publicEntries(f.market,BOARD,1).size(),1);
        }});
        test("Перевірка резервує плату, приховує текст і відмова повертає весь резерв",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,700,"KRN",true);long before=f.wallet(E),city=f.s.balance("e:t:D","KRN");String id=f.post(e);
            eq(f.wallet(E),before-700);eq(f.s.balance("e:t:D","KRN"),city);eq(f.s.balance(MarketNotices.fund(id),"KRN"),700L);eq(f.m.notices.publicEntries(f.market,BOARD,1).size(),0);
            no(()->f.m.notices.read(b,id,BOARD));no(()->f.m.notices.review(b,req(),id,true,""));eq(string(f.m.notices.read(e,id,BOARD),"body"),"Справжній човен біля пристані");
            String request=req();f.m.notices.review(d,request,id,false,"Уточніть місце");f.m.notices.review(d,request,id,false,"Уточніть місце");eq(f.wallet(E),before);eq(f.s.balance(MarketNotices.fund(id),"KRN"),0L);no(()->f.m.notices.review(d,req(),id,true,""));
        }});
        test("Схвалення використовує оплачену ціну, зміна тарифу не переоцінює заявку",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,700,"KRN",true);String id=f.post(e);long city=f.s.balance("e:t:D","KRN");f.m.notices.configure(d,req(),f.market,0,"",false);
            f.m.notices.review(d,req(),id,true,"Дозволено");eq(f.s.balance("e:t:D","KRN"),city+700);eq(f.m.notices.publicEntries(f.market,BOARD,1).size(),1);no(()->f.m.notices.review(d,req(),id,false,""));
        }});
        test("Вісника призначає лише мер; керівник базару не отримує цю посаду автоматично",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,0,"",true);f.m.staff(d,req(),f.market,B,true);String id=f.post(e);
            no(()->f.m.notices.appoint(b,req(),f.market,B,true));no(()->f.m.notices.review(b,req(),id,true,""));f.m.notices.appoint(d,req(),f.market,B,true);f.m.notices.review(b,req(),id,true,"Оглянуто");
            f.m.notices.appoint(d,req(),f.market,B,false);String second=f.post(e);no(()->f.m.notices.review(b,req(),second,true,""));
        }});
        test("Зміна мера відкликає старе призначення вісника",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,0,"",true);f.m.notices.appoint(d,req(),f.market,B,true);String id=f.post(e);f.db.execute("UPDATE estates SET lord=? WHERE id='t:D'",C);
            no(()->f.m.notices.review(b,req(),id,true,""));no(()->f.m.notices.configure(d,req(),f.market,1,"KRN",false));f.m.notices.review(c,req(),id,true,"Новий мер");
        }});
        test("Перевірка не забирає оплачений строк публікації",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,100,"KRN",true);String id=f.post(e);f.time.value+=2*FeudalService.DAY;f.m.notices.review(d,req(),id,true,"");eq(number(f.m.notices.notice(id),"ends"),f.s.now()+7*FeudalService.DAY);
        }});
        test("Автор відкликає очікування з поверненням, опублікована плата вже належить місту",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,100,"KRN",true);long before=f.wallet(E);String id=f.post(e);no(()->f.m.notices.remove(b,req(),id,""));f.m.notices.remove(e,req(),id,"");eq(f.wallet(E),before);
            String published=f.post(e);f.m.notices.review(d,req(),published,true,"");f.m.notices.remove(e,req(),published,"");eq(f.wallet(E),before-100);eq(f.m.notices.publicEntries(f.market,BOARD,1).size(),0);
        }});
        test("Строк або закриття ринку повертають лише нерозглянуті кошти",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,100,"KRN",true);long before=f.wallet(E);String expired=f.post(e);f.time.value+=8*FeudalService.DAY;f.db.atomic(()->{f.m.notices.tick();return null;});eq(f.wallet(E),before);eq(string(f.m.notices.notice(expired),"state"),"EXPIRED");
            String pending=f.post(e);f.db.execute("UPDATE cm_markets SET state='CLOSED' WHERE id=?",f.market);f.db.atomic(()->{f.m.notices.tick();return null;});eq(f.wallet(E),before);eq(string(f.m.notices.notice(pending),"state"),"EXPIRED");
        }});
        test("Знята дошка повертає резерв, віддалена публікація та застаріла ціна блокуються",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,100,"KRN",true);long before=f.wallet(E),version=f.version();String id=f.post(e);
            no(()->f.m.notices.submit(e,req(),f.market,new Place("world",100,64,100),"Назва","Текст",1,version));no(()->f.m.notices.bind(e,req(),f.market,BOARD));
            f.m.notices.configure(d,req(),f.market,200,"KRN",true);no(()->f.m.notices.submit(e,req(),f.market,BOARD,"Назва","Текст",1,version));
            f.db.atomic(()->{f.m.notices.unbind(f.market);return null;});eq(f.wallet(E),before);eq(string(f.m.notices.notice(id),"state"),"CANCELLED");no(()->f.post(e));
        }});
        test("Ліміт, межі тексту та відкат усієї операції не створюють прихованих списань",()->{try(var f=new F()){
            f.m.notices.configure(d,req(),f.market,100,"KRN",false);long before=f.wallet(E);
            no(()->f.m.notices.submit(e,req(),f.market,BOARD,"§cНі","Текст",1,f.version()));no(()->f.m.notices.submit(e,req(),f.market,BOARD,"Ні","Текст",31,f.version()));
            no(()->f.db.atomic(()->{f.post(e);throw new IllegalArgumentException("rollback");}));eq(f.wallet(E),before);eq(f.m.notices.publicEntries(f.market,BOARD,1).size(),0);
            for(int i=0;i<10;i++)f.post(e);no(()->f.post(e));eq(f.wallet(E),before-1000);
        }});
        System.out.println("MARKET_NOTICE_TESTS_PASS scenarios="+count);
    }
}
