package ua.empirecraft.feudal;

import java.nio.file.*;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CityMarketTests.req;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.CityMarketService.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Real SQL transactions, a durable file restart and native-world boundary fixtures. */
public final class RecruitMarketTests {
    static final String REG=UUID.randomUUID().toString(),GROUP=UUID.randomUUID().toString(),N1=UUID.randomUUID().toString(),N2=UUID.randomUUID().toString();
    static final class World implements RecruitMarketService.World {
        final Map<String,String> homes=new HashMap<>(Map.of(D,"t:D",E,"t:E",C,"t:E"));boolean republic,near=true,safe=true,room=true,available=true,alliedCommander,local=true;String town="t:E",fingerprint="roster";int count=8;boolean exclusive=true;
        public RecruitMarketService.Roster roster(String asset){if(!available)throw new IllegalStateException("Native unavailable");return new RecruitMarketService.Roster(asset,REG,GROUP,town,town.equals("t:E")?N1:N2,C,"Полк",count,fingerprint,true,exclusive,local);}
        public String home(String person){return homes.get(person);}public String nation(String town){return town.equals("t:E")?N1:N2;}
        public boolean commander(String town,String person){return Objects.equals(town,home(person))||alliedCommander&&town.equals("t:D")&&Objects.equals(home(person),"t:E");}
        public boolean mayor(String person,String town){return person.equals(town.equals("t:E")?E:D);}
        public boolean republic(String town){return republic;}public boolean direct(String seller,String buyer){return near;}
        public boolean safeCaptain(String person){return safe;}public boolean capacity(String nation,int incoming){return room;}
        public String citizenship(String person){return "{\"town\":\"t:E\",\"townRanks\":[\"general\"],\"nationRanks\":[],\"title\":\"Captain\",\"surname\":\"\",\"joined\":1234}";}
    }
    static final class F implements AutoCloseable {
        final CityMarketTests.F f;final World w=new World();final RecruitMarketService r;
        F()throws Exception {this("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE",true);}
        F(String url,boolean setup)throws Exception {f=new CityMarketTests.F(url,setup);r=new RecruitMarketService(f.m,w);if(setup)f.db.atomic(()->{f.s.restore("p:"+B,"e:t:D","KRN",100000);f.s.restore("p:"+E,"e:t:E","KRN",100000);return null;});}
        String offer(String kind)throws Exception {return r.list(e,req(),(kind.equals("SALE")?"R:":"C:")+REG,kind,"",f.p(Department.GOODS),"t:D","KRN",10000,24);}
        String battle()throws Exception {return r.createBattle(admin,req(),"Міст","t:D","t:E",10);}
        String hire(String offer,String battle)throws Exception {return r.buy(d,req(),offer,"t:D","",battle,f.p(Department.GOODS),r.stamp(offer));}
        String signed(String battle)throws Exception {String id=hire(offer("HIRE"),battle);r.consent(c,req(),id,true);return id;}
        long balance(String city)throws Exception{return f.s.balance("e:"+city,"KRN");}
        void sameCash(long a,long b)throws Exception {eq(balance("t:D"),a);eq(balance("t:E"),b);}
        public void close()throws Exception {f.close();}
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Permanent city sale, new commander, one debit and durable projection are one SQL commit",()->{try(var f=new F()){
            String offer=f.offer("SALE"),request=req();long before=f.balance("t:D"),seller=f.balance("t:E");
            String stamp=f.r.stamp(offer);f.r.buy(d,request,offer,"t:D",D,"",f.f.p(Department.GOODS),stamp);f.r.buy(d,request,offer,"t:D",D,"",f.f.p(Department.GOODS),stamp);
            f.sameCash(before-10000,seller+10000);eq(string(f.r.offer(offer),"state"),"SOLD");var jobs=(List<?>)f.r.state().get("jobs");eq(jobs.size(),1);
            var job=(Map<String,Object>)jobs.get(0);eq(string(job,"town"),"t:D");eq(string(job,"commander"),D);eq(string(job,"nation"),N2);eq(f.f.db.query("SELECT * FROM rm_locks").size(),0);
            no(()->f.r.buy(d,req(),offer,"t:D",D,"",f.f.p(Department.GOODS),f.r.stamp(offer)));
        }});
        test("Wrong mayor, foreign commander, capacity, remote trade and stale roster never charge",()->{try(var f=new F()){
            String offer=f.offer("SALE");long buyer=f.balance("t:D"),seller=f.balance("t:E");
            no(()->f.r.buy(e,req(),offer,"t:D",D,"",f.f.p(Department.GOODS),f.r.stamp(offer)));
            no(()->f.r.buy(d,req(),offer,"t:D",C,"",f.f.p(Department.GOODS),f.r.stamp(offer)));
            f.w.room=false;no(()->f.r.buy(d,req(),offer,"t:D",D,"",f.f.p(Department.GOODS),f.r.stamp(offer)));f.w.room=true;
            f.w.near=false;no(()->f.r.buy(d,req(),offer,"t:D",D,"",f.f.p(Department.GOODS),f.r.stamp(offer)));f.w.near=true;
            String old=f.r.stamp(offer);f.w.fingerprint="casualty";no(()->f.r.buy(d,req(),offer,"t:D",D,"",f.f.p(Department.GOODS),old));f.sameCash(buyer,seller);eq(f.f.db.query("SELECT * FROM rm_jobs").size(),0);
        }});
        test("Allied buyer commander keeps citizenship; revoked alliance cannot spend treasury funds",()->{try(var f=new F()){
            String offer=f.offer("SALE"),stamp=f.r.stamp(offer);long buyer=f.balance("t:D"),seller=f.balance("t:E");
            f.w.alliedCommander=true;f.r.inspect(d,offer,"t:D",C,"",f.f.p(Department.GOODS),stamp);f.w.alliedCommander=false;
            no(()->f.r.buy(d,req(),offer,"t:D",C,"",f.f.p(Department.GOODS),stamp));f.sameCash(buyer,seller);eq(f.f.db.query("SELECT * FROM rm_jobs").size(),0);
            f.w.alliedCommander=true;f.r.buy(d,req(),offer,"t:D",C,"",f.f.p(Department.GOODS),stamp);
            var job=f.f.db.one("SELECT * FROM rm_jobs");eq(string(job,"town"),"t:D");eq(string(job,"commander"),C);eq(string(job,"nation"),N2);eq(f.w.home(C),"t:E");eq(f.f.db.query("SELECT * FROM rm_contracts").size(),0);
        }});
        test("Delegated foreign troops can be sold but cannot enter a local-citizenship battle hire",()->{try(var f=new F()){
            f.w.local=false;no(()->f.offer("HIRE"));eq(f.f.db.query("SELECT * FROM rm_offers").size(),0);eq(f.f.db.query("SELECT * FROM rm_captains").size(),0);
            String sale=f.offer("SALE");eq(string(f.r.offer(sale),"state"),"OPEN");
        }});
        test("Ordinary bazaars sell only their city; trade republics accept other cities' own regiments",()->{try(var f=new F()){
            no(()->f.r.list(e,req(),"R:"+REG,"SALE",f.f.market,f.f.p(Department.GOODS),"","KRN",10000,24));
            f.w.republic=true;String offer=f.r.list(e,req(),"R:"+REG,"SALE",f.f.market,f.f.p(Department.GOODS),"","KRN",10000,24);
            no(()->f.r.list(d,req(),"R:"+REG,"SALE",f.f.market,f.f.p(Department.GOODS),"","KRN",10000,24));
            no(()->f.r.buy(d,req(),offer,"t:D",D,"",new Place("world",1000,64,1000),f.r.stamp(offer)));
            f.w.republic=false;no(()->f.r.buy(d,req(),offer,"t:D",D,"",f.f.p(Department.GOODS),f.r.stamp(offer)));
            f.r.tick();eq(string(f.r.offer(offer),"state"),"EXPIRED");
        }});
        test("Same-city bazaar and republican spending checks are enforced in the service",()->{try(var f=new F()){
            f.w.town="t:D";String offer=f.r.list(d,req(),"R:"+REG,"SALE",f.f.market,f.f.p(Department.GOODS),"","KRN",10000,24);
            eq(string(f.r.offer(offer),"seller"),"t:D");f.r.cancel(d,req(),offer);f.w.town="t:E";offer=f.offer("SALE");String id=offer;
            long before=f.balance("t:D");f.f.s.treasuryPermission=(actor,city)->false;no(()->f.r.buy(d,req(),id,"t:D",D,"",f.f.p(Department.GOODS),f.r.stamp(id)));eq(f.balance("t:D"),before);
        }});
        test("Hire reserves money, requires this captain's consent and never changes the commander",()->{try(var f=new F()){
            String battle=f.battle(),offer=f.offer("HIRE");long buyer=f.balance("t:D"),seller=f.balance("t:E");String id=f.hire(offer,battle);
            f.sameCash(buyer-10000,seller);eq(string(f.r.contract(id),"state"),"RESERVED");eq(f.f.db.query("SELECT * FROM rm_jobs").size(),0);
            no(()->f.r.consent(d,req(),id,true));f.r.consent(c,req(),id,true);eq(string(f.r.contract(id),"state"),"WAITING");
            f.r.startBattle(admin,req(),battle);eq(f.r.citizenLocked(C),true);eq(f.r.cityLocked("t:D"),true);eq(f.r.cityLocked("t:E"),true);
            var job=f.f.db.one("SELECT * FROM rm_jobs WHERE kind='HIRE'");eq(string(job,"commander"),C);eq(string(job,"town"),"t:D");eq(string(job,"origin"),"t:E");eq(string(f.r.contract(id),"citizenship").contains("general"),true);
            no(()->f.r.cancel(d,req(),id));no(()->f.r.cancel(c,req(),id));no(()->f.r.verdict(d,req(),id,true,"not admin"));
        }});
        test("Admin absence refunds every coin including market fees, once, after battle",()->{try(var f=new F()){
            f.w.republic=true;f.f.db.execute("UPDATE cm_tariffs SET buyer=500,seller=700 WHERE market=?",f.f.market);
            String battle=f.battle(),offer=f.r.list(e,req(),"C:"+REG,"HIRE",f.f.market,f.f.p(Department.GOODS),"","KRN",10000,24);long buyer=f.balance("t:D"),seller=f.balance("t:E");String id=f.hire(offer,battle);f.r.consent(c,req(),id,true);f.r.startBattle(admin,req(),battle);
            f.r.verdict(admin,req(),id,false,"Командир не прибув у бій");eq(f.balance("t:D"),buyer-10500);eq(f.r.citizenLocked(C),true);
            f.r.endBattle(admin,req(),battle,false,"Бій завершено");f.sameCash(buyer,seller);eq(string(f.r.contract(id),"state"),"REFUNDED");
            eq(f.r.citizenLocked(C),true);f.r.restored(id);eq(f.r.citizenLocked(C),false);f.r.tick();f.sameCash(buyer,seller);no(()->f.r.verdict(admin,req(),id,true,"Зміна після виплати"));
        }});
        test("Attendance pays seller only after battle; automatic end releases citizenship before adjudication",()->{try(var f=new F()){
            String battle=f.battle(),id=f.signed(battle);long seller=f.balance("t:E");f.r.startBattle(admin,req(),battle);f.f.time.value+=600_001;f.r.tick();
            eq(string(f.r.contract(id),"state"),"REVIEW");eq(f.balance("t:E"),seller);eq(string(f.f.db.one("SELECT * FROM rm_jobs ORDER BY seq DESC LIMIT 1"),"kind"),"RETURN");
            f.r.restored(id);eq(f.r.citizenLocked(C),false);f.r.verdict(admin,req(),id,true,"Загін виконав накази в бою");eq(f.balance("t:E"),seller+10000);eq(string(f.r.contract(id),"state"),"PAID");
        }});
        test("Consent expiry, refusal and unstarted battle all refund without captain captivity",()->{try(var f=new F()){
            String battle=f.battle();long buyer=f.balance("t:D"),seller=f.balance("t:E");String id=f.hire(f.offer("HIRE"),battle);f.f.time.value+=300_001;f.r.tick();f.sameCash(buyer,seller);eq(string(f.r.contract(id),"state"),"REFUNDED");
            id=f.hire(f.offer("HIRE"),battle);f.r.consent(c,req(),id,false);f.sameCash(buyer,seller);
            id=f.signed(battle);f.f.time.value+=FeudalService.DAY;f.r.tick();f.sameCash(buyer,seller);eq(f.r.citizenLocked(C),false);
        }});
        test("Royal/mayoral or shared commanders cannot be hired; roster/company overlap is reserved",()->{try(var f=new F()){
            f.w.safe=false;no(()->f.offer("HIRE"));f.w.safe=true;f.w.exclusive=false;no(()->f.offer("HIRE"));f.w.exclusive=true;
            String offer=f.offer("HIRE");no(()->f.offer("SALE"));f.r.cancel(e,req(),offer);String battle=f.battle(),id=f.signed(battle);f.r.startBattle(admin,req(),battle);
            no(()->f.offer("HIRE"));f.r.endBattle(admin,req(),battle,true,"Скасовано адміністратором");eq(string(f.r.contract(id),"state"),"REFUNDED");
        }});
        test("Projection failure cannot stop expiry or unlock troops before their acknowledged return",()->{try(var f=new F()){
            String battle=f.battle(),id=f.signed(battle);f.r.startBattle(admin,req(),battle);f.w.available=false;f.f.time.value+=600_001;f.r.tick();eq(string(f.r.battle(battle),"status"),"ENDED");eq(string(f.r.contract(id),"state"),"REVIEW");
            eq(f.f.db.query("SELECT * FROM rm_locks").size(),1);f.w.available=true;
            no(()->f.offer("HIRE"));no(()->f.offer("SALE"));
            f.r.restored(id);eq(f.f.db.query("SELECT * FROM rm_locks").size(),0);eq(f.r.citizenLocked(C),false);f.offer("SALE");
        }});
        test("SQL failure rolls money, offer state and native projection intent back together",()->{try(var f=new F()){
            String offer=f.offer("SALE");long buyer=f.balance("t:D"),seller=f.balance("t:E");f.f.db.execute("DROP TABLE rm_jobs");
            try{f.r.buy(d,req(),offer,"t:D",D,"",f.f.p(Department.GOODS),f.r.stamp(offer));throw new AssertionError("SQL failure accepted");}catch(IllegalStateException expected){}
            f.sameCash(buyer,seller);eq(string(f.r.offer(offer),"state"),"OPEN");eq(f.f.db.query("SELECT * FROM rm_locks").size(),1);
        }});
        test("Real file restart preserves escrow, membership snapshot, verdict and idempotent settlement",()->{
            Path dir=Files.createTempDirectory("recruit-market-");String url="jdbc:h2:"+dir.resolve("ledger")+";DATABASE_TO_LOWER=TRUE",battle,id;long buyer,seller;
            try(var f=new F(url,true)){battle=f.battle();buyer=f.balance("t:D");seller=f.balance("t:E");id=f.signed(battle);f.r.startBattle(admin,req(),battle);f.r.verdict(admin,req(),id,false,"Відсутній у битві");}
            try(var f=new F(url,false)){eq(f.r.citizenLocked(C),true);eq(string(f.r.contract(id),"verdict"),"ABSENT");f.r.endBattle(admin,req(),battle,false,"Завершення після перезапуску");f.r.restored(id);f.sameCash(buyer,seller);eq(((List<?>)f.r.state().get("jobs")).size(),1);}
            try(var f=new F(url,false)){f.r.tick();f.sameCash(buyer,seller);eq(f.r.citizenLocked(C),false);eq(string(f.r.contract(id),"state"),"REFUNDED");}
            try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        });
        System.out.println("Recruit market tests: "+count);
    }
}
