package ua.empirecraft.feudal;

import java.util.*;
import java.nio.file.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CityMarketTests.req;
import static ua.empirecraft.feudal.Database.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Real H2 money/ownership transactions, fake clock and explicit hull geometry. */
public final class FleetTests {
    static final class Sea implements FleetService.World {
        final Map<Long,FleetService.Hull> hulls=new HashMap<>();
        final Map<String,String> homes=new HashMap<>(Map.of(D,"t:D",E,"t:E",B,"t:D",C,"t:C"));
        Set<String> claims=Set.of("t:D"),allies=new HashSet<>(),wars=new HashSet<>();boolean sight=true,near=true,inCombat,cleared;
        public boolean combat(long ship){return inCombat;}
        public FleetService.Hull hull(long id){return hulls.get(id);}
        public String hometown(String person){return homes.get(person);}
        public boolean person(String id){return homes.containsKey(id);}
        public boolean allied(String a,String b){return Objects.equals(a,b)||allies.contains(ArmyPolicy.pair(a,b));}
        public boolean war(String a,String b){return wars.contains(ArmyPolicy.pair(a,b));}
        public boolean republic(String town){return town.equals("t:D");}
        public boolean direct(String seller,String buyer){return near;}
        public boolean visible(String person,FleetService.Hull hull){return sight;}
        public boolean empty(String person,Map<String,Object> old){return cleared;}
        public Set<String> territories(FleetService.Hull hull){return claims;}
        public boolean docked(FleetService.Hull hull,String town,CityMarketService.Place p){return FleetGeometry.docked(hull.footprint(),p.x(),p.z(),16,200,(x,z)->claims.contains(town));}
        void boat(double x){hulls.put(42L,new FleetService.Hull(42,"world",FleetGeometry.hull(List.of(new FleetGeometry.Point(x,0),new FleetGeometry.Point(x+200,0),new FleetGeometry.Point(x+200,5),new FleetGeometry.Point(x,5))),x+100,64,2,"t:E","Чайка","Приблизна інформація",true));}
    }
    static final class Fixture implements AutoCloseable {
        final CityMarketTests.F f;final Sea sea=new Sea();final FleetService ships;
        Fixture()throws Exception {this("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE",true);}
        Fixture(String url,boolean setup)throws Exception {
            f=new CityMarketTests.F(url,setup);ships=new FleetService(f.m,sea);sea.boat(190);
            ships.register(sea.hull(42),"t:E",E);
            if(setup){f.db.atomic(()->{f.s.restore("p:"+B,"e:t:D","KRN",100000);f.s.restore("p:"+E,"e:t:E","KRN",100000);return null;});ships.port(d,req(),f.market,true);}
        }
        String sale(String kind){return ships.list(e,req(),42,kind.equals("DIRECT")?"":f.market,kind,"t:D","KRN",10000,1000,1,"Опис продавця");}
        long balance(String town)throws Exception{return f.s.balance("e:"+town,"KRN");}
        public void close()throws Exception {f.close();}
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Зникле місто не блокує імпорт флоту; права закриті до відновлення або перепризначення",()->{try(var f=new Fixture()){
            var original=f.sea.hull(42);var orphan=new FleetService.Hull(43,original.world(),original.footprint(),original.x(),original.y(),original.z(),"t:missing","Старий корабель","",true);f.sea.hulls.put(43L,orphan);
            no(()->f.ships.register(orphan,"t:missing",E));
            f.ships.importNative(orphan,E,E,List.of(C));f.ships.importNative(orphan,D,D,List.of(B));
            eq(string(f.ships.ship(43),"town"),"t:missing");eq(string(f.ships.ship(43),"founder"),E);eq(string(f.ships.ship(43),"captain"),E);
            eq(f.f.db.query("SELECT person FROM naval_crew WHERE ship=43").size(),1);
            eq(f.ships.controls(C,43),false);eq(f.ships.controls(E,43),false);eq(f.ships.manages(e,"t:missing"),false);
            var policy=f.ships.policy(43,C);eq(policy.protect(),true);eq(policy.locked(),true);eq(policy.allow(),false);
            eq(f.ships.choices(new Actor(E,true)).stream().anyMatch(c->c.id()==43&&c.uses().contains(FleetService.ShipUse.ADMIN)),true);
            eq(f.ships.choices(new Actor(C,false)).stream().anyMatch(c->c.id()==43&&c.uses().contains(FleetService.ShipUse.CONTROL)),false);
            f.ships.tick();eq(string(f.ships.ship(43),"captain"),E);eq(f.ships.policy(42,E).allow(),true);
            f.f.db.execute("INSERT INTO estates VALUES('t:missing','BARONY','Restored',NULL,?,TRUE)",E);f.sea.homes.put(E,"t:missing");
            eq(f.ships.controls(C,43),true);eq(f.ships.controls(E,43),true);
            f.f.db.execute("UPDATE estates SET active=FALSE WHERE id='t:missing'");eq(f.ships.controls(C,43),false);eq(f.ships.policy(43,C).locked(),true);
            no(()->f.ships.assign(e,req(),43,"t:D"));f.ships.assign(new Actor("admin",true),req(),43,"t:D");
            eq(string(f.ships.ship(43),"town"),"t:D");eq(string(f.ships.ship(43),"captain"),"");eq(f.f.db.query("SELECT person FROM naval_crew WHERE ship=43").size(),0);
            eq(f.ships.controls(D,43),true);eq(f.ships.controls(C,43),false);eq(f.ships.policy(43,D).locked(),false);
        }});
        test("Зниклі сторони торгів повертають резерв і не обривають обробку інших кораблів",()->{try(var f=new Fixture()){
            String offer=f.sale("AUCTION");f.ships.bid(d,req(),offer,"t:D",10000,f.ships.stamp(offer));
            f.f.db.execute("DELETE FROM estates WHERE id='t:E'");f.ships.tick();
            eq(string(f.ships.offer(offer),"state"),"CANCELLED");eq(f.balance("t:D"),100000L);eq(f.ships.policy(42,E).locked(),true);
        }});
        test("Відстань від носа, точна межа 200 та повернутий корпус",()->{
            var rectangle=FleetGeometry.hull(List.of(new FleetGeometry.Point(190,-2),new FleetGeometry.Point(390,-2),new FleetGeometry.Point(390,2),new FleetGeometry.Point(190,2)));
            eq(FleetGeometry.distance(rectangle,0,0),190.0);eq(FleetGeometry.docked(rectangle,-10,0,16,200,(x,z)->true),true);eq(FleetGeometry.docked(rectangle,-10.01,0,16,200,(x,z)->true),false);
            var diamond=FleetGeometry.hull(List.of(new FleetGeometry.Point(100,250),new FleetGeometry.Point(250,100),new FleetGeometry.Point(400,250),new FleetGeometry.Point(250,400)));
            eq(FleetGeometry.distance(diamond,0,0)>200,true);eq(FleetGeometry.docked(rectangle,0,0,16,200,(x,z)->false),false);
        });
        test("Мер, капітан, команда й адмірал мають окремі права",()->{try(var f=new Fixture()){
            f.sea.allies.add(ArmyPolicy.pair("t:E","t:D"));
            f.ships.admiral(e,req(),"t:E",E);f.ships.edit(e,req(),42,"captain",B);f.ships.edit(e,req(),42,"crew-add",C);
            eq(f.ships.controls(B,42),true);eq(f.ships.controls(C,42),true);eq(f.ships.controls(D,42),false);
            f.ships.admiral(d,req(),"t:D",B);no(()->f.ships.admiral(e,req(),"t:D",E));
            f.ships.admiral(e,req(),"t:E",E);f.sea.homes.put(B,"t:E");f.ships.admiral(e,req(),"t:E",B);
            f.ships.edit(new Actor(B,false),req(),42,"name","Буревісник");no(()->f.ships.list(new Actor(B,false),req(),42,f.f.market,"SALE","","KRN",10000,100,1,""));
            f.sea.homes.put(B,"t:D");no(()->f.ships.edit(new Actor(B,false),req(),42,"captain",C));
        }});
        test("Мирна гавань захищає нейтральних, війна скасовує захист",()->{try(var f=new Fixture()){
            eq(f.ships.policy(42,null).protect(),true);eq(f.ships.policy(42,D).allow(),false);eq(f.ships.policy(42,E).allow(),true);
            f.sea.wars.add(ArmyPolicy.pair("t:D","t:E"));eq(f.ships.policy(42,null).protect(),false);
            f.sea.wars.clear();f.ships.port(d,req(),f.f.market,false);eq(f.ships.policy(42,null).protect(),false);
            f.sea.claims=Set.of("t:E");eq(f.ships.policy(42,null).protect(),true);eq(f.ships.policy(42,C).allow(),false);
        }});
        test("Союзний капітан не змінює власника; розрив союзу й війна відкликають командування",()->{try(var f=new Fixture()){
            no(()->f.ships.edit(e,req(),42,"captain",B));
            f.sea.allies.add(ArmyPolicy.pair("t:E","t:D"));f.ships.edit(e,req(),42,"captain",B);
            eq(f.ships.controls(B,42),true);eq(string(f.ships.ship(42),"town"),"t:E");
            f.sea.wars.add(ArmyPolicy.pair("t:E","t:D"));eq(f.ships.controls(B,42),false);f.ships.tick();eq(string(f.ships.ship(42),"captain"),"");
            f.sea.wars.clear();eq(f.ships.controls(B,42),false);f.ships.edit(e,req(),42,"captain",B);f.sea.allies.clear();
            eq(f.ships.controls(B,42),false);f.ships.tick();eq(string(f.ships.ship(42),"captain"),"");
            f.ships.edit(e,req(),42,"captain",E);eq(f.ships.controls(E,42),true);
        }});
        test("Продаж атомарно передає місто, ціну і збори; повтор не платить двічі",()->{try(var f=new Fixture()){
            f.sea.allies.add(ArmyPolicy.pair("t:E","t:C"));
            f.ships.edit(e,req(),42,"captain",C);String offer=f.sale("SALE"),stamp=f.ships.stamp(offer),request=req();
            eq(f.ships.policy(42,E).locked(),true);no(()->f.ships.edit(e,req(),42,"name","Підміна"));no(()->f.sale("SALE"));
            f.ships.buy(d,request,offer,"t:D",stamp);eq(string(f.ships.ship(42),"town"),"t:D");eq(string(f.ships.ship(42),"captain"),"");
            eq(f.balance("t:D"),90200L);eq(f.balance("t:E"),109800L);f.ships.buy(d,request,offer,"t:D",stamp);eq(f.balance("t:E"),109800L);
            no(()->f.ships.buy(d,req(),offer,"t:D",stamp));eq(f.f.db.query("SELECT * FROM naval_locks").size(),0);
        }});
        test("Прямий трейд вимагає двох чинних мерів поруч",()->{try(var f=new Fixture()){
            String offer=f.sale("DIRECT"),stamp=f.ships.stamp(offer);f.sea.near=false;no(()->f.ships.buy(d,req(),offer,"t:D",stamp));
            f.sea.near=true;no(()->f.ships.buy(new Actor(B,false),req(),offer,"t:D",stamp));f.ships.buy(d,req(),offer,"t:D",stamp);eq(f.balance("t:D"),90000L);eq(f.balance("t:E"),110000L);
        }});
        test("Бій забороняє продаж і повертає резерв аукціону без зміни власника",()->{try(var f=new Fixture()){
            f.sea.inCombat=true;no(()->f.sale("SALE"));no(()->f.sale("DIRECT"));
            f.sea.inCombat=false;String offer=f.sale("AUCTION");f.ships.bid(d,req(),offer,"t:D",10000,f.ships.stamp(offer));
            f.sea.inCombat=true;f.ships.tick();eq(f.balance("t:D"),100000L);eq(string(f.ships.offer(offer),"state"),"CANCELLED");eq(string(f.ships.ship(42),"town"),"t:E");
            eq(f.f.db.query("SELECT * FROM naval_locks").size(),0);
        }});
        test("Недостатні кошти не змінюють власника і не списують резерв",()->{try(var f=new Fixture()){
            String offer=f.sale("SALE");f.f.db.atomic(()->{f.f.s.restore("e:t:D","p:"+B,"KRN",100000);return null;});
            no(()->f.ships.buy(d,req(),offer,"t:D",f.ships.stamp(offer)));eq(string(f.ships.ship(42),"town"),"t:E");eq(f.balance("t:E"),100000L);
        }});
        test("Змінений тариф знецінює підтвердження до списання коштів",()->{try(var f=new Fixture()){
            String offer=f.sale("SALE"),stamp=f.ships.stamp(offer);
            f.f.db.execute("UPDATE cm_tariffs SET buyer=500 WHERE market=?",f.f.market);
            no(()->f.ships.buy(d,req(),offer,"t:D",stamp));eq(f.balance("t:D"),100000L);eq(string(f.ships.ship(42),"town"),"t:E");
        }});
        test("Перебита ставка повертається повністю, доплата своєї ставки резервується один раз",()->{try(var f=new Fixture()){
            f.f.db.execute("INSERT INTO estates VALUES('t:C','BARONY','Третє місто','n:C',?,TRUE)",C);
            f.f.db.atomic(()->{f.f.s.restore("p:"+E,"e:t:C","KRN",100000);return null;});
            String offer=f.sale("AUCTION");f.ships.bid(d,req(),offer,"t:D",10000,f.ships.stamp(offer));
            f.ships.bid(c,req(),offer,"t:C",11000,f.ships.stamp(offer));eq(f.balance("t:D"),100000L);eq(f.balance("t:C"),89000L);
            f.ships.bid(c,req(),offer,"t:C",12000,f.ships.stamp(offer));eq(f.balance("t:C"),88000L);
            f.sea.boat(1000);f.ships.tick();eq(f.balance("t:C"),100000L);eq(string(f.ships.ship(42),"town"),"t:E");
        }});
        test("Місто-власника призначає лише адміністратор; виправлення повертає резерв",()->{try(var f=new Fixture()){
            var boat=f.sea.hull(42);f.sea.hulls.put(43L,new FleetService.Hull(43,boat.world(),boat.footprint(),boat.x(),boat.y(),boat.z(),"","Особистий","",true));
            f.ships.register(f.sea.hull(43),"",B);no(()->f.ships.assign(e,req(),43,"t:E"));no(()->f.ships.assign(b,req(),43,"t:E"));
            no(()->f.ships.assign(b,req(),43,"t:D"));f.ships.assign(admin,req(),43,"t:D");eq(string(f.ships.ship(43),"town"),"t:D");
            String offer=f.sale("AUCTION");f.ships.bid(d,req(),offer,"t:D",10000,f.ships.stamp(offer));
            f.ships.assign(admin,req(),42,"t:D");eq(f.balance("t:D"),100000L);eq(string(f.ships.offer(offer),"state"),"CANCELLED");eq(string(f.ships.ship(42),"town"),"t:D");
        }});
        test("Вихід з радіуса та відкликання дозволу повертають ставки",()->{try(var f=new Fixture()){
            String offer=f.sale("AUCTION");f.ships.bid(d,req(),offer,"t:D",10000,f.ships.stamp(offer));eq(f.balance("t:D"),90000L);String first=offer;no(()->f.ships.cancel(e,req(),first));
            f.sea.boat(300);f.ships.tick();eq(f.balance("t:D"),100000L);eq(string(f.ships.offer(offer),"state"),"CANCELLED");eq(string(f.ships.ship(42),"town"),"t:E");
            f.sea.boat(190);offer=f.sale("AUCTION");f.ships.bid(d,req(),offer,"t:D",10000,f.ships.stamp(offer));f.ships.port(d,req(),f.f.market,false);eq(f.balance("t:D"),100000L);
        }});
        test("Аукціон завершується один раз і не приймає застарілих ставок",()->{try(var f=new Fixture()){
            String offer=f.sale("AUCTION"),stamp=f.ships.stamp(offer);f.ships.bid(d,req(),offer,"t:D",10000,stamp);no(()->f.ships.bid(d,req(),offer,"t:D",11000,stamp));
            f.f.time.value+=3_600_001;f.ships.tick();eq(string(f.ships.ship(42),"town"),"t:D");eq(f.balance("t:E"),109800L);f.ships.tick();eq(f.balance("t:E"),109800L);
        }});
        test("Останнє спостереження не стежить за невидимим судном; приховування зберігається",()->{try(var f=new Fixture()){
            f.sea.allies.add(ArmyPolicy.pair("t:D","t:E"));f.ships.seen(D,f.sea.hull(42));double old=((Number)f.ships.sightings(D).get(0).get("x")).doubleValue();
            f.sea.boat(1000);f.sea.sight=false;f.ships.seen(D,f.sea.hull(42));eq(((Number)f.ships.sightings(D).get(0).get("x")).doubleValue(),old);
            f.ships.hide(D,42,true);f.sea.sight=true;f.ships.seen(D,f.sea.hull(42));eq(f.ships.sightings(D).size(),0);f.ships.hide(D,42,false);eq(((Number)f.ships.sightings(D).get(0).get("x")).doubleValue(),old);
            f.sea.wars.add(ArmyPolicy.pair("t:D","t:E"));eq(f.ships.sightings(D).size(),0);
        }});
        test("Повторна зустріч переносить мітку й оновлює дату; приховане пересування її не змінює",()->{try(var f=new Fixture()){
            f.sea.allies.add(ArmyPolicy.pair("t:D","t:E"));f.ships.seen(D,f.sea.hull(42));long before=number(f.ships.sightings(D).get(0),"observed");
            f.f.time.value+=86_400_000;f.sea.boat(1000);f.sea.sight=false;eq(f.ships.seen(D,f.sea.hull(42)),false);eq(number(f.ships.sightings(D).get(0),"observed"),before);
            f.sea.sight=true;eq(f.ships.seen(D,f.sea.hull(42)),true);var fresh=f.ships.sightings(D).get(0);eq(((Number)fresh.get("x")).doubleValue(),1100.0);eq(number(fresh,"observed"),f.f.time.value);eq(f.ships.sightings(D).size(),1);
            // Coordinates still update if two observations share a clock millisecond.
            f.sea.boat(1200);f.ships.seen(D,f.sea.hull(42));eq(((Number)f.ships.sightings(D).get(0).get("x")).doubleValue(),1300.0);
        }});
        test("Лише перевірене порожнє місце прибирає спостереження; повторна зустріч відновлює його",()->{try(var f=new Fixture()){
            f.sea.allies.add(ArmyPolicy.pair("t:D","t:E"));f.ships.seen(D,f.sea.hull(42));eq(f.ships.checkMissing(D,42),false);eq(f.ships.sightings(D).size(),1);
            f.sea.cleared=true;eq(f.ships.checkMissing(E,42),false);eq(f.ships.checkMissing(D,42),true);eq(f.ships.sightings(D).size(),0);eq(f.ships.checkMissing(D,42),false);
            f.sea.cleared=false;f.ships.seen(D,f.sea.hull(42));eq(f.ships.sightings(D).size(),1);
            f.ships.hide(D,42,true);f.sea.cleared=true;eq(f.ships.checkMissing(D,42),false);f.ships.seen(D,f.sea.hull(42));eq(f.ships.sightings(D).size(),0);f.ships.hide(D,42,false);eq(f.ships.sightings(D).size(),1);
        }});
        test("Вибір за назвою враховує права, дублікати, українські назви й канонічний ID",()->{try(var f=new Fixture()){
            eq(f.ships.resolveShip(e,"чайКА",FleetService.ShipUse.MANAGE),42L);eq(f.ships.resolveShip(e,"42",FleetService.ShipUse.MANAGE),42L);no(()->f.ships.resolveShip(d,"Чайка",FleetService.ShipUse.MANAGE));
            f.ships.register(new FleetService.Hull(43,"world",f.sea.hull(42).footprint(),0,64,0,"t:E","Чайка","",true),"t:E",E);
            no(()->f.ships.resolveShip(e,"Чайка",FleetService.ShipUse.MANAGE));eq(f.ships.resolveShip(e,"43",FleetService.ShipUse.MANAGE),43L);
            f.ships.register(new FleetService.Hull(44,"world",f.sea.hull(42).footprint(),0,64,0,"t:E","Чайка (#42)","",true),"t:E",E);eq(f.ships.resolveShip(e,"Чайка (#42)",FleetService.ShipUse.MANAGE),44L);
            f.ships.register(new FleetService.Hull(-7,"world",f.sea.hull(42).footprint(),0,64,0,"t:E","Ластівка","",true),"t:E",E);eq(f.ships.resolveShip(e,"-7",FleetService.ShipUse.MANAGE),-7L);
            f.ships.edit(e,req(),43,"name","Чорна Перлина");eq(f.ships.resolveShip(e,"\"Чорна Перлина\"",FleetService.ShipUse.MANAGE),43L);
            f.sea.allies.add(ArmyPolicy.pair("t:D","t:E"));f.ships.edit(e,req(),42,"captain",B);eq(f.ships.resolveShip(b,"Чайка",FleetService.ShipUse.CONTROL),42L);no(()->f.ships.resolveShip(b,"Чайка",FleetService.ShipUse.MANAGE));
            eq(f.ships.resolveShip(new Actor(D,true),"Чайка",FleetService.ShipUse.ADMIN),42L);no(()->f.ships.resolveShip(d,"Чайка",FleetService.ShipUse.ADMIN));
        }});
        test("Перезапуск зберігає власника, а застаріла native-копія не повертає старе місто",()->{
            Path path=Files.createTempDirectory("naval-ledger-").resolve("fleet");String url="jdbc:h2:"+path+";DATABASE_TO_LOWER=TRUE";
            try(var f=new Fixture(url,true)){String offer=f.sale("SALE");f.ships.buy(d,req(),offer,"t:D",f.ships.stamp(offer));f.sea.boat(1000);f.ships.seen(D,f.sea.hull(42));}
            try(var f=new Fixture(url,false)){eq(string(f.ships.ship(42),"town"),"t:D");eq(f.balance("t:D"),90200L);eq(f.balance("t:E"),109800L);eq(((Number)f.ships.sightings(D).get(0).get("x")).doubleValue(),1100.0);}
        });
        System.out.println("FLEET_SERVICE_PASS cases="+count);
    }
}
