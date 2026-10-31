package ua.empirecraft.feudal;
import java.util.*;
import java.math.*;
import java.nio.file.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.Model.*;

/** Real H2 and immutable policy tests; clocks, persistence, authorization and no inventory shortcuts. */
public final class ArmyTests {
    static int checks;
    static void check(boolean b){if(!b)throw new AssertionError("Army check "+checks);checks++;}
    interface Attempt{void run()throws Exception;}
    static void fails(Attempt a)throws Exception{try{a.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError("Expected rejection at "+checks);}
    static final String U=UUID.nameUUIDFromBytes("soldier".getBytes()).toString(),V=UUID.nameUUIDFromBytes("soldier-two".getBytes()).toString();
    static final class F extends Fixture{
        final CommerceService commerce;final ProvisionService food;final ArmyService army;
        F()throws Exception{super();commerce=new CommerceService(s);food=new ProvisionService(s,commerce);army=new ArmyService(s,food,null);init("t:D");init("t:E");}
        void init(String town){food.settle(town,0,time.value,new ProvisionClock(time.value,0,false,time.value));}
        void stock(String town,int quantity,int nutrition)throws Exception{db.execute("MERGE INTO provision_stock KEY(town,slot) VALUES(?,0,'fixture-food',?,?,64,0)",town,quantity,nutrition);}
        void observe(){army.observe(U,D,"t:D",20);}
    }
    public static void main(String[] args)throws Exception{
        try(var f=new F()){
            f.observe();f.stock("t:D",4,5);check(f.army.feed(U,1,10)==10);check(number(f.db.one("SELECT quantity FROM provision_stock WHERE town='t:D'"),"quantity")==2);
            check(f.army.feed(U,1,10)==10);check(number(f.db.one("SELECT quantity FROM provision_stock WHERE town='t:D'"),"quantity")==2);fails(()->f.army.feed(U,1,9));fails(()->f.army.feed(U,3,10));
            check(f.army.feed(U,2,10)==10);check(f.army.feed(U,1,10)==10);check(number(f.army.unit(U),"feed_total")==20);check(f.army.feed(U,3,10)==0);check(number(f.army.unit(U),"feed_seq")==2);check(f.army.food.reserve("t:D").signum()==0);
            f.stock("t:D",1,15);check(f.army.feed(U,3,10)==10);check(f.army.food.reserve("t:D").equals(ProvisionClock.UNIT.multiply(BigInteger.valueOf(5))));check(f.army.feed(U,4,10)==5);check(f.army.food.reserve("t:D").signum()==0);
            f.stock("t:E",64,5);check(f.army.feed(U,5,10)==0);check(f.army.food.reserve("t:E").equals(ProvisionClock.UNIT.multiply(BigInteger.valueOf(320))));
            f.army.presence(U,false);fails(()->f.army.feed(U,5,10));f.observe();check(bool(f.army.unit(U),"loaded"));f.army.presence(U,true);f.observe();check(!bool(f.army.unit(U),"alive"));check(f.army.count("home","t:D",false)==0);
        }
        try(var f=new F()){
            f.observe();f.stock("t:D",10,5);var change=new CommerceService.InventoryChange(D,"before","after");
            String op=f.food.transfer(d,"t:D",0,0,"fixture-food",1,5,64,change);check(f.army.feed(U,1,10)==0);check(number(f.db.one("SELECT quantity FROM provision_stock WHERE town='t:D'"),"quantity")==10);f.commerce.finish(op,false);check(f.army.feed(U,1,10)==10);
        }
        try(var f=new F()){
            f.observe();fails(()->f.army.assign(a,U,"t:D","t:D"));fails(()->f.army.assign(d,U,"t:D","t:E"));f.army.assign(d,U,"t:D","t:D");check(f.army.commands(D,f.army.unit(U)));check(!f.army.commands(A,f.army.unit(U)));
            f.army.officer(d,"t:D",E,true);check(f.army.commands(E,f.army.unit(U)));f.army.officer(d,"t:D",E,false);check(!f.army.commands(E,f.army.unit(U)));
            var changed=f.entities.stream().map(e->e.id().equals("t:D")?new Estate(e.id(),e.kind(),e.name(),e.nation(),E,true):e).toList();f.s.sync(new Snapshot(changed,f.citizens));f.army.tick();check(!f.army.commands(D,f.army.unit(U)));check(f.army.commands(E,f.army.unit(U)));check(string(f.army.unit(U),"owner").equals(E));
            f.army.release(e,U);check(string(f.army.unit(U),"owner").equals(D));check(string(f.army.unit(U),"pending_owner").equals(D));
            f.army.observe(U,E,"t:D",30);check(string(f.army.unit(U),"owner").equals(D));check(string(f.army.unit(U),"pending_owner").equals(D));f.army.observe(U,D,"t:D",30);check(string(f.army.unit(U),"pending_owner")==null);
        }
        try(var f=new F()){
            f.observe();fails(()->f.army.propose(d,"t:D","WAGE","92233720368547758.07 BASE 10000"));f.army.observe(V,D,"t:D",30);f.army.propose(d,"t:D","WAGE","1.00 BASE 1");f.army.assign(d,U,"t:D","t:D");fails(()->f.army.assign(d,V,"t:D","t:D"));
            f.s.setBalance("e:t:D","BASE",500);f.time.value+=FeudalService.DAY;f.army.tick();check(f.s.balance("e:t:D","BASE")==400);check(f.s.balance("army:t:D","BASE")==100);f.army.tick();check(f.s.balance("e:t:D","BASE")==400);
            f.s.setBalance("e:t:D","BASE",0);f.time.value+=FeudalService.DAY;f.army.tick();check(number(f.army.settings("t:D"),"unpaid_since")>0);long oldBill=number(f.db.one("SELECT amount FROM army_payroll WHERE paid=FALSE"),"amount");
            f.army.release(d,U);check(number(f.db.one("SELECT amount FROM army_payroll WHERE paid=FALSE"),"amount")==oldBill);f.s.setBalance("e:t:D","BASE",100);f.army.tick();check(f.s.balance("army:t:D","BASE")==200);check(number(f.army.settings("t:D"),"unpaid_since")==0);
        }
        try(var f=new F()){
            f.observe();f.army.assign(d,U,"t:D","t:D");f.army.propose(d,"t:D","WAGE","1.00 BASE 100");f.time.value+=100*FeudalService.DAY;
            f.army.release(d,U);check(number(f.db.one("SELECT COUNT(*) AS n FROM army_payroll WHERE estate='t:D' AND amount=100"),"n")==100);
            f.army.tick();check(number(f.db.one("SELECT SUM(amount) AS n FROM army_payroll WHERE estate='t:D' AND paid=FALSE"),"n")==10000);
        }
        try(var f=new F()){
            f.army.propose(a,"n:A","WAR","n:B");var w=f.db.one("SELECT * FROM army_wars");check("ACTIVE".equals(string(w,"status")));fails(()->f.army.propose(a,"n:A","WAR","n:B"));fails(()->f.army.propose(d,"t:D","WAR","n:B"));
            String key=string(w,"id");f.army.propose(a,"n:A","PEACE",key);check("ACTIVE".equals(string(f.army.war(key),"status")));f.army.propose(b,"n:B","PEACE",key);check("PEACE".equals(string(f.db.one("SELECT status FROM army_wars WHERE id=?",key),"status")));
        }
        try(var f=new PoliticsTests.F()){
            var commerce=new CommerceService(f.s);var food=new ProvisionService(f.s,commerce);var army=new ArmyService(f.s,food,f.p);f.db.execute("UPDATE political_states SET government='REPUBLIC' WHERE estate='n:A'");
            String motion=army.propose(PoliticsTests.a,"n:A","WAGE","1.00 BASE 100");check(!motion.equals("APPLIED"));fails(()->army.vote(PoliticsTests.b,motion,true));
            int i=0;var voters=f.p.electorate("n:A");for(String voter:voters)if(i++<=voters.size()/2)army.vote(new FeudalService.Actor(voter,false),motion,true);check(number(army.settings("n:A"),"wage")==100);
        }
        Path temp=Files.createTempDirectory("army-persist-");String url="jdbc:h2:"+temp.resolve("army")+";DATABASE_TO_LOWER=TRUE";
        try(var f=new Fixture(url)){
            var food=new ProvisionService(f.s,new CommerceService(f.s));food.settle("t:D",0,f.time.value,new ProvisionClock(f.time.value,0,false,f.time.value));f.db.execute("INSERT INTO provision_stock VALUES('t:D',0,'food',10,5,64,0)");var army=new ArmyService(f.s,food,null);army.observe(U,D,"t:D",20);check(army.feed(U,1,10)==10);check(army.feed(U,2,10)==10);
        }
        try(var f=new Fixture(url)){
            var food=new ProvisionService(f.s,new CommerceService(f.s));var army=new ArmyService(f.s,food,null);check(!bool(army.unit(U),"loaded"));army.observe(U,D,"t:D",20);check(army.feed(U,1,10)==10);check(number(f.db.one("SELECT quantity FROM provision_stock"),"quantity")==6);check(number(army.unit(U),"feed_total")==20);
        }finally{try(var files=Files.walk(temp)){for(var p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        policy();System.out.println("PASS ArmyTests: "+checks+" supply, receipts, authority, wages, councils, war and combat checks");
    }
    static void policy(){
        var lands=Map.of("t:A",new ArmyPolicy.Land("t:A","n:A","n:A",A),"t:B",new ArmyPolicy.Land("t:B","n:B","n:B",B),"t:C",new ArmyPolicy.Land("t:C","n:C","n:C",C));
        var citizens=Map.of(A,"t:A",B,"t:B",C,"t:C");var units=Map.of(U,new ArmyPolicy.Soldier(A,null,"t:A",true),V,new ArmyPolicy.Soldier(B,null,"t:B",true));long now=10000;
        var ally=new ArmyPolicy.View(now,lands,citizens,units,Set.of(ArmyPolicy.pair("n:A","n:B"),ArmyPolicy.pair("n:B","n:C")),Set.of(),Set.of(),Set.of());
        for(boolean pvp:List.of(false,true))for(boolean retaliation:List.of(false,true)){
            check(!ally.decide(U,A,true,V,B,true,false,false,pvp,retaliation,now).allow());check(!ally.decide(U,A,true,B,null,false,false,false,pvp,retaliation,now).allow());
        }
        check(!ally.friendly("t:A","t:C"));check(!ally.decide(U,A,true,C,null,false,false,false,true,false,now).allow());check(ally.decide(U,A,true,C,null,false,false,false,true,true,now).allow());
        check(!ally.decide(U,A,true,C,null,false,false,false,false,true,now).allow());check(!ally.decide(U,A,true,C,null,false,false,false,true,true,now+5001).allow());
        var war=new ArmyPolicy.View(now,lands,citizens,units,Set.of(),Set.of(),Set.of(ArmyPolicy.pair("n:A","n:B")),Set.of());check(war.decide(U,A,true,V,B,true,false,false,true,false,now).allow());check(!war.decide(U,A,true,V,B,true,false,false,false,false,now).allow());
        var pact=new ArmyPolicy.View(now,lands,citizens,units,Set.of(),Set.of(ArmyPolicy.pair("n:A","n:B")),war.wars(),Set.of());check(!pact.decide(U,A,true,V,B,true,false,false,true,true,now).allow());
        check(!war.decide(U,A,true,A,null,false,false,false,true,false,now).allow());check(!war.decide(U,A,true,"cow",null,false,true,false,true,true,now).allow());check(war.decide(U,A,true,"zombie",null,false,false,true,false,false,now).allow());
        check(war.commands(A,U));check(!war.commands(B,U));var garrison=Map.of(U,new ArmyPolicy.Soldier(A,"t:B","t:B",true));var state=new ArmyPolicy.View(now,lands,citizens,garrison,Set.of(),Set.of(),Set.of(),Set.of("t:B|"+C));check(state.commands(B,U));check(state.commands(C,U));check(!state.commands(A,U));
        check(war.decide("outsider",null,false,V,B,true,false,false,true,false,now).allow());check(war.decide(U,A,true,"outsider",null,false,false,false,true,true,now).allow());
    }
}
