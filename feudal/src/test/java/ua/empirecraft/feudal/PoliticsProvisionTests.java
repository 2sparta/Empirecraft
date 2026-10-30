package ua.empirecraft.feudal;

import java.math.*;
import java.nio.file.*;
import java.util.*;
import ua.empirecraft.feudal.FeudalService.Actor;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.PoliticalModel.*;
import static ua.empirecraft.feudal.PoliticsTests.*;
import static ua.empirecraft.feudal.PoliticalRegressionTests.administer;

/** Real H2 integration between PR 13 politics and current durable provisions, archives and native authority. */
public final class PoliticsProvisionTests {
    static BigInteger units(long n){return ProvisionClock.UNIT.multiply(BigInteger.valueOf(n));}
    static ProvisionClock clock(long epoch){return new ProvisionClock(epoch,0,false,epoch);}
    static String food(ProvisionService p,Actor who,String town,int count){
        return p.transfer(who,town,0,0,"original-NBT-prototype",count,5,64,new CommerceService.InventoryChange(who.id(),"before","after"));
    }
    static void archive(F f,String... ids){
        Set<String> gone=Set.of(ids);var entities=f.entities.stream().filter(e->!gone.contains(e.id())).toList();
        f.s.sync(new Snapshot(entities,f.citizenship));f.p.syncDomains();
    }
    static String election(F f)throws Exception {
        f.all("n:A",a);f.government(a,"n:A",Government.REPUBLIC);String key=f.p.election(a,"n:A"),winner=person("A1");
        f.p.candidate(new Actor(winner,false),key);for(String voter:f.p.electorate("n:A"))f.p.ballot(new Actor(voter,false),key,winner);
        f.time.now+=3*FeudalService.DAY;f.p.closeElection(key);return key;
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Upgrade from provisions 1.3 preserves money, gold, hunger and a prepared food transfer",()->{
            var time=new Time();try(var db=new Database("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE")){
                var s=new FeudalService(db,time,7*FeudalService.DAY,2*FeudalService.DAY,32,12,2);
                s.sync(new Snapshot(List.of(new Estate("n:A",Kind.KINGDOM,"Nation","n:A",a.id(),true),new Estate("t:A",Kind.BARONY,"Town","n:A",a.id(),true)),Set.of(a.id()+"@n:A")));
                db.execute("INSERT INTO currencies(code,name,symbol,issuer,supply,backing) VALUES('BASE','Historical fixture','$',NULL,0,'BASE')");s.setBalance("p:"+a.id(),"BASE",10000);s.transfer(a,"p:"+a.id(),"e:n:A","BASE",6000);
                String gold=s.currencies.prepareGold(a,"IN","p:"+a.id(),2,"before","after");s.currencies.finishGold(reviewer,gold,true);
                var c=new CommerceService(s);var p=new ProvisionService(s,c);long start=time.now;p.settle("t:A",1,start,clock(start));time.now+=FeudalService.DAY;p.settle("t:A",1,time.now,clock(start));
                String pending=food(p,a,"t:A",10);var politics=new PoliticsService(s,new PoliticsRules(k->-1));
                eq(politics.state("n:A").stage(),Stage.KINGDOM);eq(politics.state("t:A").stage(),Stage.BARONY);
                eq(s.balance("p:"+a.id(),"BASE"),4000L);eq(s.balance("e:n:A","BASE"),6000L);eq(s.currencies.goldBalance("p:"+a.id()).compareTo(BigDecimal.valueOf(2)),0);
                ok(p.pending("t:A"));eq(number(p.town("t:A"),"hungry_ms"),FeudalService.DAY);c.finish(pending,true);
                time.now+=FeudalService.DAY;p.settle("t:A",1,time.now,clock(start));eq(p.reserve("t:A"),units(42));eq(s.check(),List.of());
            }
        });
        test("Pre-political archived treasuries stay accessible without inventing political authority",()->{
            var time=new Time();try(var db=new Database("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE")){
                var s=new FeudalService(db,time,7*FeudalService.DAY,2*FeudalService.DAY,32,12,2);
                s.sync(new Snapshot(List.of(new Estate("n:A",Kind.KINGDOM,"Old nation","n:A",a.id(),true)),Set.of(a.id()+"@n:A")));
                db.execute("INSERT INTO currencies(code,name,symbol,issuer,supply,backing) VALUES('BASE','Historical fixture','$',NULL,0,'BASE')");
                s.setBalance("p:"+a.id(),"BASE",1000);s.transfer(a,"p:"+a.id(),"e:n:A","BASE",1000);
                s.sync(new Snapshot(List.of(),Set.of()));var politics=new PoliticsService(s,new PoliticsRules(k->-1));
                no(()->s.transfer(b,"e:n:A","p:"+b.id(),"BASE",100));s.transfer(a,"e:n:A","p:"+a.id(),"BASE",1000);
                eq(s.balance("p:"+a.id(),"BASE"),1000L);ok(!politics.hereditary("n:A"));eq(db.one("SELECT * FROM political_states WHERE estate='n:A'"),null);eq(s.check(),List.of());
            }
        });
        test("An existing political republic and provision stock survive the same disk restart",()->{
            Path dir=Files.createTempDirectory("politics-provisions");String url="jdbc:h2:"+dir.resolve("data")+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0";long start;
            try(var f=new F(url,0)){f.all("n:A",a);f.government(a,"n:A",Government.REPUBLIC);start=f.time.now;var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);p.settle("t:A",2,start,clock(start));c.finish(food(p,a,"t:A",20),true);}
            try(var f=new F(url,0)){var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);eq(f.p.state("n:A").government(),Government.REPUBLIC);eq(p.reserve("t:A"),units(100));f.time.now=start+FeudalService.DAY;p.settle("t:A",2,f.time.now,clock(start));eq(p.reserve("t:A"),units(84));eq(string(p.stock("t:A").get(0),"item"),"original-NBT-prototype");}
        });
        test("A political merge keeps separate native town stores and bills each physical town once",()->{try(var f=new F()){
            var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);long start=f.time.now;
            p.settle("t:A",1,start,clock(start));p.settle("t:B",3,start,clock(start));c.finish(food(p,a,"t:A",10),true);c.finish(food(p,b,"t:B",20),true);
            String plan=f.merge("keep");f.p.unions.confirmMerge(a,plan,"n:A");f.p.unions.confirmMerge(b,plan,"n:B");
            eq(f.s.estate("t:B").lord(),b.id());eq(p.reserve("t:A"),units(50));eq(p.reserve("t:B"),units(100));
            f.time.now+=FeudalService.DAY;p.settle("t:A",1,f.time.now,clock(start));p.settle("t:B",3,f.time.now,clock(start));eq(p.reserve("t:A").add(p.reserve("t:B")),units(118));
        }});
        test("Republican budget checks remain enforced while physical food is usable",()->{try(var f=new F()){
            f.seed(a,2000);f.all("n:A",a);f.government(a,"n:A",Government.REPUBLIC);
            no(()->f.s.transfer(a,"e:n:A","p:"+a.id(),"BASE",100));var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);long start=f.time.now;
            p.settle("t:A",1,start,clock(start));c.finish(food(p,a,"t:A",10),true);f.time.now+=FeudalService.DAY;p.settle("t:A",1,f.time.now,clock(start));eq(p.reserve("t:A"),units(42));eq(f.s.balance("e:n:A","BASE"),2000L);
        }});
        test("A vanished province stops taxes and administration upkeep without replaying archive history",()->{try(var f=new F()){
            administer(f,"n:A","n:B",a,b);f.seed(a,1000);archive(f,"n:B","t:B");
            eq(f.db.one("SELECT * FROM political_admin WHERE province='n:B'"),null);long events=f.db.query("SELECT seq FROM political_history WHERE event='ADMIN_ARCHIVED'").size();
            f.p.syncDomains();eq((long)f.db.query("SELECT seq FROM political_history WHERE event='ADMIN_ARCHIVED'").size(),events);
            f.time.now+=FeudalService.DAY;f.p.tick();eq(f.s.balance("e:n:A","BASE"),1000L);eq(f.s.balance("sys:administration","BASE"),0L);ok(!f.p.lands("n:A").contains("t:B"));
        }});
        test("Losing a merged center restores surviving provinces without taking their stock",()->{try(var f=new F()){
            var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);p.settle("t:B",1,f.time.now,clock(f.time.now));c.finish(food(p,b,"t:B",20),true);
            String plan=f.merge("keep");f.p.unions.confirmMerge(a,plan,"n:A");f.p.unions.confirmMerge(b,plan,"n:B");archive(f,"n:A","t:A");
            eq(f.p.state("n:B").mergedInto(),null);ok(f.p.sovereign("n:B"));eq(string(f.p.unions.get(plan),"status"),"ARCHIVED");eq(p.reserve("t:B"),units(100));eq(f.s.estate("t:B").lord(),b.id());
        }});
        test("An extinct union member cannot freeze the remaining council or keep an old vote valid",()->{try(var f=new F()){
            String union=f.federation(UnionType.CONFEDERATION);String stale=f.p.unions.proposeVote(a,union,"n:A","CHARTER","Оновлений статут спільної державної ради","BASE",0);
            archive(f,"n:C","t:C");f.p.tick();eq(f.p.unions.members(union).size(),2);eq(string(f.p.unions.get(union),"status"),"SUSPENDED");eq(string(f.db.one("SELECT status FROM political_union_votes WHERE id=?",stale),"status"),"STALE");
            String invite=f.p.unions.proposeVote(a,union,"n:A","INVITE","n:D","BASE",0);f.p.unions.vote(a,invite,"n:A",true);f.p.unions.vote(b,invite,"n:B",true);f.p.unions.acceptInvite(d,union,"n:D");f.p.tick();eq(string(f.p.unions.get(union),"status"),"ACTIVE");
        }});
        test("Accepted union debt remains payable by the archived treasury's owner only",()->{try(var f=new F()){
            String union=f.p.unions.propose(a,"n:A",List.of("n:B","n:C"),UnionType.CONFEDERATION,"Council","Спільний статут і зобов’язання учасників","defense",500,"BASE");f.p.unions.sign(a,union,"n:A");f.p.unions.sign(b,union,"n:B");f.p.unions.sign(c,union,"n:C");
            f.time.now+=FeudalService.DAY;f.p.unions.tick();f.seed(b,1000);archive(f,"n:B","t:B");
            eq(number(f.db.one("SELECT amount FROM political_union_debts WHERE union_id=? AND state='n:B'",union),"amount"),500L);no(()->f.p.unions.payDebt(c,union,"n:B"));
            f.p.unions.payDebt(b,union,"n:B");eq(f.s.balance("u:"+union,"BASE"),500L);eq(f.s.balance("e:n:B","BASE"),500L);ok(bool(f.db.one("SELECT paid FROM political_union_debts WHERE union_id=? AND state='n:B'",union),"paid"));eq(f.s.check(),List.of());
        }});
        test("Unclaim invalidates castle approval, readiness and a submitted promotion",()->{try(var f=new F()){
            f.all("n:A",a);f.stage("n:A",Stage.SETTLEMENT);f.p.castleInspect(a,"n:A","castle-A",144,true,true,true);f.p.castleReview(reviewer,"n:A",4,"Незалежний огляд замку");f.p.submit(a,"n:A",Stage.BARONY);
            f.p.castleInspect(b,"n:B","castle-B",144,true,true,true);f.p.castleReview(reviewer,"n:B",4,"Інший незалежний огляд");f.p.landLost("t:A");
            eq(f.db.one("SELECT * FROM political_castles WHERE state='n:A'"),null);eq(f.db.one("SELECT * FROM political_ready WHERE state='n:A'"),null);eq(f.db.one("SELECT * FROM political_advances WHERE state='n:A'"),null);ok(f.p.castleApproved("n:B"));no(()->f.p.confirm(a,"n:A"));eq(f.p.state("n:A").stage(),Stage.SETTLEMENT);
        }});
        test("Extinction archives an unfinished election instead of transferring dead authority",()->{try(var f=new F()){
            String key=election(f);archive(f,"n:A","t:A");eq(string(f.db.one("SELECT status FROM political_elections WHERE id=?",key),"status"),"ARCHIVED");no(()->f.p.finishElection(key));
        }});
        test("A forced native replacement invalidates an obsolete prepared vote and permits fresh elections",()->{try(var f=new F()){
            String old=election(f),next=person("A2");f.db.execute("UPDATE estates SET lord=? WHERE id='n:A'",next);f.p.syncDomains();
            eq(string(f.db.one("SELECT status FROM political_elections WHERE id=?",old),"status"),"STALE");ok(!f.p.authorityChangeAllowed("n:A",person("A1")));String fresh=f.p.election(new Actor(next,false),"n:A");ok(!fresh.equals(old));
        }});
        test("Native famine exception is restricted to the exact town/nation and cannot outlive its scope",()->{
            UUID town=UUID.randomUUID(),nation=UUID.randomUUID();ok(FamineAuthority.allows("t:"+town,town,nation));ok(FamineAuthority.allows("n:"+nation,town,nation));
            ok(!FamineAuthority.allows("n:"+UUID.randomUUID(),town,nation));ok(!FamineAuthority.allows("t:"+UUID.randomUUID(),town,nation));ok(!FamineAuthority.allows("n:"+nation,null,nation));ok(!FamineAuthority.allows("t:"+town,null,null));
        });
        System.out.println("Перевірок спільної політики та провізії: "+count);
    }
}

