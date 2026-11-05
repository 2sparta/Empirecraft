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
        Set<String> claims=Set.of("t:D"),allies=new HashSet<>(),wars=new HashSet<>();boolean sight=true,near=true;
        public FleetService.Hull hull(long id){return hulls.get(id);}
        public String hometown(String person){return homes.get(person);}
        public boolean person(String id){return homes.containsKey(id);}
        public boolean allied(String a,String b){return Objects.equals(a,b)||allies.contains(ArmyPolicy.pair(a,b));}
        public boolean war(String a,String b){return wars.contains(ArmyPolicy.pair(a,b));}
        public boolean republic(String town){return town.equals("t:D");}
        public boolean direct(String seller,String buyer){return near;}
        public boolean visible(String person,FleetService.Hull hull){return sight;}
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
        test("Відстань від носа, точна межа 200 та повернутий корпус",()->{
            var rectangle=FleetGeometry.hull(List.of(new FleetGeometry.Point(190,-2),new FleetGeometry.Point(390,-2),new FleetGeometry.Point(390,2),new FleetGeometry.Point(190,2)));
            eq(FleetGeometry.distance(rectangle,0,0),190.0);eq(FleetGeometry.docked(rectangle,-10,0,16,200,(x,z)->true),true);eq(FleetGeometry.docked(rectangle,-10.01,0,16,200,(x,z)->true),false);
            var diamond=FleetGeometry.hull(List.of(new FleetGeometry.Point(100,250),new FleetGeometry.Point(250,100),new FleetGeometry.Point(400,250),new FleetGeometry.Point(250,400)));
            eq(FleetGeometry.distance(diamond,0,0)>200,true);eq(FleetGeometry.docked(rectangle,0,0,16,200,(x,z)->false),false);
        });
        test("Мер, капітан, команда й адмірал мають окремі права",()->{try(var f=new Fixture()){
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
        test("Продаж атомарно передає місто, ціну і збори; повтор не платить двічі",()->{try(var f=new Fixture()){
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
        test("Особисте судно передає тільки засновник; адміністративне виправлення повертає резерв",()->{try(var f=new Fixture()){
            var boat=f.sea.hull(42);f.sea.hulls.put(43L,new FleetService.Hull(43,boat.world(),boat.footprint(),boat.x(),boat.y(),boat.z(),"","Особистий","",true));
            f.ships.register(f.sea.hull(43),"",B);no(()->f.ships.assign(e,req(),43,"t:E"));no(()->f.ships.assign(b,req(),43,"t:E"));
            f.ships.assign(b,req(),43,"t:D");eq(string(f.ships.ship(43),"town"),"t:D");
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
        test("Перезапуск зберігає власника, а застаріла native-копія не повертає старе місто",()->{
            Path path=Files.createTempDirectory("naval-ledger-").resolve("fleet");String url="jdbc:h2:"+path+";DATABASE_TO_LOWER=TRUE";
            try(var f=new Fixture(url,true)){String offer=f.sale("SALE");f.ships.buy(d,req(),offer,"t:D",f.ships.stamp(offer));}
            try(var f=new Fixture(url,false)){eq(string(f.ships.ship(42),"town"),"t:D");eq(f.balance("t:D"),90200L);eq(f.balance("t:E"),109800L);}
        });
        System.out.println("FLEET_SERVICE_PASS cases="+count);
    }
}
