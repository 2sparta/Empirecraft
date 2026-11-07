package ua.empirecraft.feudal;

import java.nio.file.*;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CityMarketTests.req;
import static ua.empirecraft.feudal.Database.*;
import ua.empirecraft.feudal.ShipRegistrationService.Anchor;

/** Real H2 application, approval, physical-ship binding and ownership transactions. */
public final class ShipRegistrationTests {
    static final Anchor ANCHOR=new Anchor("world",20,64,30);
    static final class F implements AutoCloseable {
        final FleetTests.Fixture fleet;final ShipRegistrationService ships;boolean valid=true;
        F()throws Exception {this("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE",true);}
        F(String url,boolean setup)throws Exception {fleet=new FleetTests.Fixture(url,setup);ships=new ShipRegistrationService(fleet.f.m,fleet.ships,anchor->valid);}
        String submit(){return ships.submit(d,req(),"Чайка",ANCHOR);}
        String approved(){String id=submit();ships.review(admin,req(),id,true,"Оглянуто");return id;}
        String assembled(){String id=approved();var permit=ships.begin(admin,ANCHOR);ships.complete(permit.get("permit").toString(),42);return id;}
        String state(String id)throws Exception{return string(ships.application(id),"state");}
        public void close()throws Exception {fleet.close();}
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Заявка з назвою й Discord-подія мають один коміт та стійкий ID",()->{try(var f=new F()){
            String request=req(),id=f.ships.submit(d,request,"Чайка",ANCHOR);eq(f.ships.submit(d,request,"Чайка",ANCHOR),id);
            var row=f.ships.application(id);eq(string(row,"name"),"Чайка");eq(string(row,"requested_town"),"t:D");eq(f.state(id),"PENDING");eq(row.get("ship"),null);
            eq(number(f.ships.db.one("SELECT COUNT(*) AS n FROM registration_outbox WHERE kind='SHIP'"),"n"),1L);
            eq(string(f.ships.db.one("SELECT payload FROM registration_outbox WHERE kind='SHIP'"),"payload").contains("/shipadmin approve "+id),true);
            no(()->f.ships.submit(d,request,"Підміна",ANCHOR));
            f.fleet.f.time.value+=60_000;
            no(()->f.ships.db.atomic(()->{f.ships.submit(d,req(),"Відкат",new Anchor("world",25,64,30));throw new IllegalArgumentException("rollback");}));
            eq(number(f.ships.db.one("SELECT COUNT(*) AS n FROM naval_applications"),"n"),1L);
            eq(number(f.ships.db.one("SELECT COUNT(*) AS n FROM registration_outbox WHERE kind='SHIP'"),"n"),1L);
        }});
        test("Збирання вимагає схвалення адміністратора й доступного блока",()->{try(var f=new F()){
            String id=f.submit();no(()->f.ships.begin(admin,ANCHOR));no(()->f.ships.review(d,req(),id,true,""));
            f.valid=false;no(()->f.ships.review(admin,req(),id,true,""));f.valid=true;f.ships.review(admin,req(),id,true,"Огляд");
            no(()->f.ships.begin(d,ANCHOR));f.valid=false;no(()->f.ships.begin(admin,ANCHOR));f.valid=true;
            var permit=f.ships.begin(admin,ANCHOR);eq(f.state(id),"ASSEMBLING");eq(permit.get("name"),"Чайка");eq(permit.get("applicant"),D);no(()->f.ships.begin(admin,ANCHOR));
        }});
        test("Назва, громадянство, інтервал і три відкриті заявки обмежуються сервером",()->{try(var f=new F()){
            for(String title:List.of(""," ","a".repeat(65),"§cФлаг","Рядок\nдва"))no(()->f.ships.submit(d,req(),title,ANCHOR));
            f.fleet.sea.homes.remove(D);no(()->f.submit());f.fleet.sea.homes.put(D,"t:D");String first=f.submit();
            no(()->f.ships.submit(e,req(),"Дубль блока",ANCHOR));no(()->f.ships.submit(d,req(),"Завчасно",new Anchor("world",21,64,30)));
            for(int x=21;x<23;x++){f.fleet.f.time.value+=60_000;f.ships.submit(d,req(),"Чайка "+x,new Anchor("world",x,64,30));}
            f.fleet.f.time.value+=60_000;no(()->f.ships.submit(d,req(),"Четверта",new Anchor("world",23,64,30)));
            f.ships.withdraw(d,req(),first);f.ships.submit(d,req(),"Заміна",new Anchor("world",23,64,30));
        }});
        test("Відхилені та відкликані заявки не дають дозволу збирання",()->{try(var f=new F()){
            String first=f.submit();no(()->f.ships.withdraw(e,req(),first));f.ships.review(admin,req(),first,false,"Переробіть корпус");
            no(()->f.ships.begin(admin,ANCHOR));eq(f.state(first),"REJECTED");
            f.fleet.f.time.value+=60_000;String second=f.approved();f.ships.withdraw(d,req(),second);eq(f.state(second),"CANCELLED");no(()->f.ships.begin(admin,ANCHOR));
        }});
        test("Дозвіл прив’язується до одного корабля; невдале збирання можна повторити",()->{try(var f=new F()){
            String id=f.approved();var first=f.ships.begin(admin,ANCHOR);String permit=first.get("permit").toString();
            f.ships.abort(D,permit);eq(f.state(id),"ASSEMBLING");f.ships.abort(admin.id(),permit);eq(f.state(id),"APPROVED");
            no(()->f.ships.complete(permit,42));String current=f.ships.begin(admin,ANCHOR).get("permit").toString();
            no(()->f.ships.complete("forged",42));f.ships.complete(current,0);eq(f.ships.pending(0),true);f.ships.complete(current,0);
            no(()->f.ships.complete(current,42));no(()->f.ships.withdraw(d,req(),id));no(()->f.ships.review(admin,req(),id,false,""));
        }});
        test("Оформлення атомарно призначає місто і назву, повтор безпечний",()->{try(var f=new F()){
            String id=f.assembled();no(()->f.ships.finalizeShip(d,req(),id,"t:D","Чайка"));
            no(()->f.ships.finalizeShip(admin,req(),id,"t:missing","Чайка"));eq(f.state(id),"ASSEMBLED");eq(string(f.fleet.ships.ship(42),"town"),"t:E");
            String request=req();f.ships.finalizeShip(admin,request,id,"t:D","Буревісник");f.ships.finalizeShip(admin,request,id,"t:D","Буревісник");
            eq(f.state(id),"ASSIGNED");eq(f.ships.pending(42),false);eq(string(f.fleet.ships.ship(42),"town"),"t:D");eq(string(f.fleet.ships.ship(42),"name"),"Буревісник");
            no(()->f.ships.finalizeShip(admin,request,id,"t:E","Підміна"));
            f.fleet.ships.assign(admin,req(),42,"t:E");eq(string(f.fleet.ships.ship(42),"town"),"t:E");
        }});
        test("До реального корабля оформлення неможливе",()->{try(var f=new F()){
            String id=f.approved();no(()->f.ships.finalizeShip(admin,req(),id,"t:D","Чайка"));eq(f.state(id),"APPROVED");
            f.ships.begin(admin,ANCHOR);no(()->f.ships.finalizeShip(admin,req(),id,"t:D","Чайка"));eq(f.state(id),"ASSEMBLING");
        }});
        test("Перезапуск зберігає дозвіл, корабель, ім’я та очікування оформлення",()->{
            Path directory=Files.createTempDirectory("ship-applications-");String url="jdbc:h2:"+directory.resolve("test")+";DATABASE_TO_LOWER=TRUE",id,permit;
            try(var f=new F(url,true)){id=f.approved();permit=f.ships.begin(admin,ANCHOR).get("permit").toString();}
            try(var f=new F(url,false)){eq(f.state(id),"ASSEMBLING");f.ships.complete(permit,42);}
            try(var f=new F(url,false)){eq(f.state(id),"ASSEMBLED");eq(string(f.ships.application(id),"name"),"Чайка");f.ships.finalizeShip(admin,req(),id,"t:D","Чайка");}
            try(var f=new F(url,false)){eq(f.state(id),"ASSIGNED");eq(string(f.fleet.ships.ship(42),"town"),"t:D");eq(string(f.fleet.ships.ship(42),"name"),"Чайка");eq(string(f.ships.complete(permit,42),"state"),"ASSIGNED");}
            try(var files=Files.walk(directory)){for(var path:files.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
        });
        System.out.println("SHIP_REGISTRATION_TESTS_PASS scenarios="+count);
    }
}
