package ua.empirecraft.feudal;

import java.nio.file.*;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import ua.empirecraft.feudal.CommerceService.*;

/** Real H2: consent, escrow, partial fulfillment, crash boundaries and succession. */
public final class RealmCommerceTests {
    static List<InventoryChange> changes(){return List.of(new InventoryChange(A,"a0","a1"),new InventoryChange(B,"b0","b1"));}
    static String order(CommerceService c){return c.create(a,"p:"+A,"stone",2000,"BASE",10000,7);}
    public static void main(String[] args) throws Exception {
        count=0;Class.forName("org.h2.Driver");
        test("Дипломатія: чотири типи та обидва підписи, адміністратор не підміняє правителя",()->{try(var f=new Fixture()){
            var r=new RealmService(f.s);for(String type:List.of("alliance","nonaggression","trade","passage")){
                String id=r.offer(a,"n:A","n:B",type,14,"Дотримуватися узгоджених умов");eq(string(r.treaty(id),"status"),"DRAFT");no(()->r.sign(admin,id));no(()->r.sign(c,id));r.sign(b,id);eq(string(r.treaty(id),"status"),"ACTIVE");no(()->r.sign(b,id));
            }eq(r.treaties("n:A").size(),4);
        }});
        test("Текст і строки обмежені; дублікати та угода із собою заборонені",()->{try(var f=new Fixture()){
            var r=new RealmService(f.s);no(()->r.offer(a,"n:A","n:A","trade",1,"Торгівля"));no(()->r.offer(a,"n:A","n:B","trade",0,"Торгівля"));no(()->r.offer(a,"n:A","n:B","trade",1,"§cТоргівля"));r.offer(a,"n:A","n:B","trade",1,"Торгівля");no(()->r.offer(b,"n:B","n:A","trade",1,"Інша торгівля"));
        }});
        test("Зміна правителя скасовує старі підписи, чинні договори переходять державі",()->{try(var f=new Fixture()){
            var r=new RealmService(f.s);String draft=r.offer(a,"n:A","n:B","trade",7,"Торгівля без мита"),active=r.offer(a,"n:A","n:B","alliance",7,"Спільна оборона");r.sign(b,active);
            var changed=new ArrayList<>(f.entities);changed.set(1,new Estate("n:B",Kind.KINGDOM,"Візантія","n:B",C,true));f.s.sync(new Snapshot(changed,f.citizens));no(()->r.sign(b,draft));r.tick();eq(string(r.treaty(draft),"status"),"STALE");eq(string(r.treaty(active),"status"),"ACTIVE");
        }});
        test("Розірвання, причина й історія незмінні; завершення за часом одноразове",()->{try(var f=new Fixture()){
            var r=new RealmService(f.s);String id=r.offer(a,"n:A","n:B","passage",1,"Прохід через кордон");r.sign(b,id);no(()->r.end(c,id,"Порушення"));r.end(a,id,"Порушення умов");eq(string(r.treaty(id),"status"),"BROKEN");no(()->r.end(b,id,"Повтор"));eq(f.db.query("SELECT * FROM diplomacy_events WHERE treaty=?",id).size(),3);
            String expiry=r.offer(a,"n:A","n:B","passage",1,"Прохід через кордон");r.sign(b,expiry);f.time.value+=FeudalService.DAY;r.tick();r.tick();eq(string(r.treaty(expiry),"status"),"EXPIRED");eq(f.db.query("SELECT * FROM diplomacy_events WHERE treaty=?",expiry).size(),3);
        }});
        test("Торгівля резервує обидві валюти та зараховує рівно один раз",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);f.seed(a,1000);f.seed(b,500);String id=c.trade(a,B,changes(),List.of(new Payment("p:"+A,"p:"+B,"BASE",400),new Payment("p:"+B,"p:"+A,"BASE",100)));
            eq(f.s.balance("p:"+A,"BASE"),600L);eq(f.s.balance("p:"+B,"BASE"),400L);c.finish(id,true);eq(f.s.balance("p:"+A,"BASE"),700L);eq(f.s.balance("p:"+B,"BASE"),800L);no(()->c.finish(id,true));eq(f.s.check(),List.of());
        }});
        test("Помилка другого резервування відкочує перше та весь журнал",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);f.seed(a,1000);no(()->c.trade(a,B,changes(),List.of(new Payment("p:"+A,"p:"+B,"BASE",400),new Payment("p:"+B,"p:"+A,"BASE",100))));eq(f.s.balance("p:"+A,"BASE"),1000L);eq(f.db.query("SELECT * FROM item_ops").size(),0);
        }});
        test("Скасований намір повертає всі кошти; чужі рахунки недоступні",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);f.seed(a,1000);String id=c.trade(a,B,changes(),List.of(new Payment("p:"+A,"p:"+B,"BASE",1000)));c.finish(id,false);eq(f.s.balance("p:"+A,"BASE"),1000L);no(()->c.trade(a,B,changes(),List.of(new Payment("e:n:A","p:"+B,"BASE",1000))));
        }});
        test("Незавершена предметна передача блокує торгівлю, замовлення і банк",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);f.seed(a,10000);String id=c.trade(a,B,changes(),List.of());no(()->CommerceService.inventoryAvailable(f.db,A));no(()->c.trade(a,B,changes(),List.of()));no(()->order(c));no(()->f.s.currencies.prepareGold(a,"IN","p:"+A,1,"b","a"));c.finish(id,false);CommerceService.inventoryAvailable(f.db,A);
        }});
        test("Незавершений золотий банк блокує предметні операції",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);String op=f.s.currencies.prepareGold(a,"IN","p:"+A,1,"b","a");no(()->c.trade(a,B,changes(),List.of()));f.s.currencies.finishGold(admin,op,false);c.trade(a,B,changes(),List.of());
        }});
        test("Замовлення 2000 каменю за 100: резерв, часткова здача та сховище",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);f.seed(a,10000);String id=order(c);eq(f.s.balance("p:"+A,"BASE"),0L);String op=c.deliver(b,id,500,new InventoryChange(B,"b0","b1"));eq(number(c.order(id),"delivered"),0L);c.finish(op,true);eq(f.s.balance("p:"+B,"BASE"),2500L);eq(number(c.order(id),"unclaimed"),500L);
            String rest=c.deliver(b,id,1500,new InventoryChange(B,"b1","b2"));c.finish(rest,true);eq(f.s.balance("p:"+B,"BASE"),10000L);eq(string(c.order(id),"status"),"FULFILLED");eq(f.s.balance("o:"+id,"BASE"),0L);no(()->c.deliver(b,id,1,new InventoryChange(B,"b2","b3")));
        }});
        test("Округлення пропорційної оплати не створює монет",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);f.seed(a,1);String id=c.create(a,"p:"+A,"stone",3,"BASE",1,1);for(int i=0;i<3;i++)c.finish(c.deliver(b,id,1,new InventoryChange(B,"b"+i,"b"+(i+1))),true);eq(f.s.balance("p:"+B,"BASE"),1L);eq(number(c.order(id),"paid"),1L);eq(CommerceService.cumulative(900_000_000_000_000L,999999,1000000),899_999_100_000_000L);
        }});
        test("Скасування повертає лише несплачене, ресурси зберігаються",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);f.seed(a,10000);String id=order(c);c.finish(c.deliver(b,id,500,new InventoryChange(B,"b0","b1")),true);no(()->c.cancel(b,id));c.cancel(a,id);eq(f.s.balance("p:"+A,"BASE"),7500L);eq(number(c.order(id),"unclaimed"),500L);no(()->c.cancel(a,id));
            String collect=c.collect(a,id,200,new InventoryChange(A,"a0","a1"));eq(number(c.order(id),"unclaimed"),500L);c.finish(collect,true);eq(number(c.order(id),"unclaimed"),300L);no(()->c.finish(collect,true));
        }});
        test("Незавершена поставка блокує скасування, другу поставку й видачу",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);f.seed(a,10000);String id=order(c),op=c.deliver(b,id,500,new InventoryChange(B,"b0","b1"));no(()->c.cancel(a,id));no(()->c.deliver(d,id,1,new InventoryChange(D,"d0","d1")));no(()->c.collect(a,id,1,new InventoryChange(A,"a0","a1")));f.time.value+=8*FeudalService.DAY;c.tick();eq(string(c.order(id),"status"),"OPEN");c.finish(op,true);c.tick();eq(string(c.order(id),"status"),"EXPIRED");eq(f.s.balance("p:"+A,"BASE"),7500L);
        }});
        test("Невдала поставка не оплачується й не змінює сховище",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);f.seed(a,10000);String id=order(c);c.finish(c.deliver(b,id,500,new InventoryChange(B,"b0","b1")),false);eq(number(c.order(id),"delivered"),0L);eq(f.s.balance("p:"+B,"BASE"),0L);no(()->c.deliver(a,id,500,new InventoryChange(A,"a0","a1")));no(()->c.deliver(b,id,2001,new InventoryChange(B,"b0","b1")));
        }});
        test("Державні замовлення та видача доступні лише чинному розпоряднику",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);f.seed(a,10000);f.s.transfer(a,"p:"+A,"e:n:A","BASE",10000);no(()->c.create(b,"e:n:A","stone",1,"BASE",10000,1));String id=c.create(a,"e:n:A","stone",1,"BASE",10000,1);c.finish(c.deliver(b,id,1,new InventoryChange(B,"b0","b1")),true);no(()->c.collect(b,id,1,new InventoryChange(B,"b1","b2")));c.finish(c.collect(a,id,1,new InventoryChange(A,"a0","a1")),true);eq(number(c.order(id),"unclaimed"),0L);
        }});
        test("Спадкоємець має погодитися; чужа кандидатура та чужа згода відхиляються",()->{try(var f=new Fixture()){
            var r=new RealmService(f.s);String id=f.duchy();no(()->r.nominate(a,id,B));r.nominate(a,id,D);no(()->r.acceptHeir(e,id));no(()->r.prepareSuccession(a,id,false,30));r.acceptHeir(d,id);no(()->r.prepareSuccession(e,id,false,30));String op=r.prepareSuccession(a,id,false,30);r.finishSuccession(op);eq(f.s.estate(id).lord(),D);no(()->r.finishSuccession(op));eq(r.heir(id),null);
        }});
        test("Неактивність: перший запуск починає новий відлік, активність відсуває строк",()->{try(var f=new Fixture()){
            var r=new RealmService(f.s);String id=f.duchy();r.nominate(a,id,D);r.acceptHeir(d,id);r.tick();f.time.value+=29*FeudalService.DAY;no(()->r.prepareSuccession(admin,id,true,30));r.activity(A);f.time.value+=29*FeudalService.DAY;no(()->r.prepareSuccession(admin,id,true,30));f.time.value+=FeudalService.DAY;no(()->r.prepareSuccession(admin,id,true,0));r.finishSuccession(r.prepareSuccession(admin,id,true,30));eq(f.s.estate(id).lord(),D);
        }});
        test("Зміна правителя або вихід спадкоємця робить старе призначення недійсним",()->{try(var f=new Fixture()){
            var r=new RealmService(f.s);String id=f.duchy();r.nominate(a,id,D);r.acceptHeir(d,id);f.s.accept(e,f.s.nominate(a,id,E));no(()->r.prepareSuccession(e,id,false,30));r.nominate(e,id,D);r.acceptHeir(d,id);var citizens=new HashSet<>(f.citizens);citizens.remove(D+"@n:A");f.s.sync(new Snapshot(f.entities,citizens));no(()->r.prepareSuccession(e,id,false,30));
        }});
        test("Династія, кошти та ієрархія зберігаються при спадкуванні",()->{try(var f=new Fixture()){
            var r=new RealmService(f.s);String id=f.duchy();r.house(a,id,"Дім Черкаський");f.seed(a,700);f.s.transfer(a,"p:"+A,"e:"+id,"BASE",700);r.nominate(a,id,D);r.acceptHeir(d,id);r.finishSuccession(r.prepareSuccession(a,id,false,30));eq(f.s.balance("e:"+id,"BASE"),700L);eq(f.s.parent(id),"n:A");eq(string(f.db.one("SELECT * FROM dynasties WHERE estate=?",id),"name"),"Дім Черкаський");
        }});
        test("Журнал переживає перезапуск: резерв не повертається й не сплачується двічі",()->{
            Path dir=Files.createTempDirectory("empirecraft-commerce-");String url="jdbc:h2:"+dir.resolve("live")+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0",id;
            try(var f=new Fixture(url)){var c=new CommerceService(f.s);f.seed(a,1000);id=c.trade(a,B,changes(),List.of(new Payment("p:"+A,"p:"+B,"BASE",600)));}
            try(var f=new Fixture(url)){var c=new CommerceService(f.s);eq(f.s.balance("p:"+A,"BASE"),400L);eq(f.s.balance("p:"+B,"BASE"),0L);no(()->CommerceService.inventoryAvailable(f.db,B));c.finish(id,true);eq(f.s.balance("p:"+B,"BASE"),600L);no(()->c.finish(id,true));}
            try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        });
        System.out.println("Перевірок дипломатії, торгівлі та спадкування: "+count);
    }
}
