package ua.empirecraft.feudal;

import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.PoliticalModel.*;
import static ua.empirecraft.feudal.PoliticsTests.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Regressions at the boundaries between feudal, administrative and elected authority. */
public final class PoliticalRegressionTests {
    static void administer(F f,String center,String province,Actor owner,Actor governor) {
        String plan=f.p.administer(owner,center,province,"Погоджена цивільна адміністрація",0,"BASE");
        f.p.signAdministration(owner,plan);f.p.signAdministration(governor,plan);
    }
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        test("A governor's feudal vassals never become direct provinces",()->{try(var f=new F()){
            f.stage("n:C",Stage.BARONY);f.link(b,"n:C","n:B",c);
            administer(f,"n:A","n:B",a,b);
            eq(f.p.directTowns("n:A"),Set.of("t:A","t:B"));
            ok(f.p.lands("n:A").contains("t:C"));eq(f.p.electorate("n:A").size(),60);
            eq(f.p.centralization("n:A"),50);
        }});
        test("Combined administrative and feudal cycles are rejected",()->{try(var f=new F()){
            administer(f,"n:A","n:B",a,b);
            no(()->f.s.offer(b,"n:A","n:B",0,"BASE","висока","оборона",0));
            eq(f.s.parent("n:A"),null);
        }});
        test("Real town baronies can form a county without a progression deadlock",()->{try(var f=new F()){
            f.all("t:A",a);f.link(a,"t:B","t:A",b);f.link(a,"t:C","t:A",c);
            f.advance(a,"t:A",Stage.COUNTY);
            eq(f.p.state("t:A").stage(),Stage.COUNTY);eq(f.s.estate("t:B").lord(),b.id());
        }});
        test("Two paper regions sharing one town count as only one barony",()->{try(var f=new F()){
            f.all("n:A",a);var regions=new ArrayList<String>();
            for(int i=0;i<2;i++) {
                String region=f.s.create(a,"n:A",Model.Kind.COUNTY,"Регіон "+i);f.p.syncDomains();
                f.db.execute("UPDATE estates SET lord=? WHERE id=?",b.id(),region);
                f.stage(region,Stage.BARONY);f.p.syncDomains();regions.add(region);f.link(a,region,"n:A",b);
            }
            f.stage("n:A",Stage.BARONY);
            no(()->f.p.submit(a,"n:A",Stage.COUNTY));
            eq(f.p.personal(regions.get(0)),f.p.personal(regions.get(1)));
        }});
        test("A noble title requires a real approved holding",()->{try(var f=new F()){
            no(()->f.s.grant(a,"n:A",person("A1"),Model.Kind.BARONY));
            f.s.grant(a,"n:A",a.id(),Model.Kind.BARONY);
            f.stage("t:A",Stage.SETTLEMENT);no(()->f.s.grant(a,"n:A",a.id(),Model.Kind.BARONY));
        }});
        test("An old regional appointment cannot bypass a later republic",()->{try(var f=new F()){
            String region=f.s.create(a,"n:A",Model.Kind.COUNTY,"Виборний регіон");f.p.syncDomains();
            String appointment=f.s.nominate(a,region,person("A1"));
            f.grant(region,"council");f.laws(a,region);f.government(a,region,Government.REPUBLIC);
            no(()->f.s.accept(new Actor(person("A1"),false),appointment));eq(f.s.estate(region).lord(),a.id());
        }});
        test("An old prepared succession cannot override republican authority",()->{try(var f=new F()){
            f.all("n:A",a);var realm=new RealmService(f.s);realm.hereditary=f.p::hereditary;
            realm.nominate(a,"n:A",person("A1"));realm.acceptHeir(new Actor(person("A1"),false),"n:A");
            String succession=realm.prepareSuccession(a,"n:A",false,30);
            f.government(a,"n:A",Government.REPUBLIC);no(()->realm.finishSuccession(succession));
            eq(f.s.estate("n:A").lord(),a.id());
        }});
        test("Republican tribute requires a vote over the exact vassal proposal",()->{try(var f=new F()){
            f.all("n:A",a);f.government(a,"n:A",Government.REPUBLIC);
            String proposal=f.s.offer(a,"n:A","n:B",100,"BASE","висока","оборона",0);
            no(()->f.s.accept(b,proposal));eq(f.s.contract("n:A"),null);
            String mandate=f.p.decision(a,"n:A","VASSAL",proposal,"",0);f.voteAll(mandate,"n:A");
            f.s.accept(b,proposal);eq(number(f.s.contract("n:A"),"amount"),100L);
        }});
        test("A republican delegate needs the council's exact yes or no mandate",()->{try(var f=new F()){
            String union=f.federation(UnionType.CONFEDERATION);f.all("n:A",a);f.government(a,"n:A",Government.REPUBLIC);
            String vote=f.p.unions.proposeVote(b,union,"n:B","CHARTER","Нова конституція спільного політичного союзу","BASE",0);
            no(()->f.p.unions.vote(a,vote,"n:A",true));
            String mandate=f.p.decision(a,"n:A","COUNCIL",vote,"NO",0);f.voteAll(mandate,"n:A");
            no(()->f.p.unions.vote(a,vote,"n:A",true));f.p.unions.vote(a,vote,"n:A",false);
        }});
        test("A republican head cannot leave a union without a domestic vote",()->{try(var f=new F()){
            String union=f.federation(UnionType.CONFEDERATION);f.all("n:A",a);f.government(a,"n:A",Government.REPUBLIC);
            no(()->f.p.unions.leave(a,union,"n:A"));
            String mandate=f.p.decision(a,"n:A","EXIT",union,"",0);f.voteAll(mandate,"n:A");f.p.unions.leave(a,union,"n:A");
        }});
        test("A suspended union still completes a second exit notice",()->{try(var f=new F()){
            String union=f.federation(UnionType.CONFEDERATION);f.p.unions.leave(a,union,"n:A");
            f.time.now+=3*FeudalService.DAY+1;f.p.unions.tick();eq(string(f.p.unions.get(union),"status"),"SUSPENDED");
            f.p.unions.leave(b,union,"n:B");f.time.now+=3*FeudalService.DAY+1;f.p.unions.tick();
            eq(f.p.unions.members(union).size(),1);eq(string(f.p.unions.members(union).get(0),"state"),"n:C");
        }});
        test("A suspended council can accept a replacement member",()->{try(var f=new F()){
            String union=f.federation(UnionType.CONFEDERATION);f.p.unions.leave(a,union,"n:A");
            f.time.now+=3*FeudalService.DAY+1;f.p.unions.tick();
            String vote=f.p.unions.proposeVote(b,union,"n:B","INVITE","n:D","BASE",0);
            f.p.unions.vote(b,vote,"n:B",true);f.p.unions.vote(c,vote,"n:C",true);
            f.p.unions.acceptInvite(d,union,"n:D");f.p.unions.tick();eq(string(f.p.unions.get(union),"status"),"ACTIVE");
        }});
        test("A bilateral personal union rejects a third member",()->{try(var f=new F()){
            f.db.execute("UPDATE estates SET lord=? WHERE id='n:B'",a.id());
            String union=f.p.unions.propose(a,"n:A",List.of("n:B"),UnionType.PERSONAL,"Дві корони","Спільний монарх та окремі державні установи","dynastic",0,"BASE");
            f.p.unions.sign(a,union,"n:A");f.p.unions.sign(a,union,"n:B");
            no(()->f.p.unions.proposeVote(a,union,"n:A","INVITE","n:C","BASE",0));
        }});
        test("A merger draft blocks competing international commitments",()->{try(var f=new F()){
            f.merge("keep");
            no(()->f.p.unions.propose(a,"n:A",List.of("n:C","n:D"),UnionType.FEDERATION,"Інший союз","Інша федеральна конституція та спільна рада","defense",0,"BASE"));
        }});
        test("Losing population warns, preserves rank and observes a grace period",()->{try(var f=new F()){
            f.p.population(Map.of());f.p.tick();eq(f.p.state("n:A").stage(),Stage.KINGDOM);
            ok(f.db.one("SELECT * FROM political_health WHERE state='n:A'")!=null);
            f.time.now+=8*FeudalService.DAY;f.p.tick();ok(bool(f.db.one("SELECT * FROM political_health WHERE state='n:A'"),"escalated"));
            f.p.population(f.people);f.p.tick();eq(f.db.one("SELECT * FROM political_health WHERE state='n:A'"),null);
        }});
        test("A failed observed condition resets the seven-day promotion clock",()->{try(var f=new F("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE",7)){
            f.all("n:A",a);f.stage("n:A",Stage.COUNTY);
            for(var pair:List.of(Map.entry("n:B",b),Map.entry("n:C",c),Map.entry("n:D",d))){f.stage(pair.getKey(),Stage.BARONY);f.link(a,pair.getKey(),"n:A",pair.getValue());}
            f.p.progress("n:A",Stage.KINGDOM);f.time.now+=6*FeudalService.DAY;
            f.p.population(Map.of());f.p.tick();f.p.population(f.people);
            f.time.now+=FeudalService.DAY;no(()->f.p.submit(a,"n:A",Stage.KINGDOM));
            f.time.now+=7*FeudalService.DAY;f.advance(a,"n:A",Stage.KINGDOM);
        }});
        test("Union budgets cannot debit a gold reserve account",()->{try(var f=new F()){
            String union=f.federation(UnionType.CONFEDERATION);
            no(()->f.p.unions.proposeVote(a,union,"n:A","BUDGET","r:SOL","BASE",100));
            f.all("n:A",a);no(()->f.p.decision(a,"n:A","BUDGET","r:SOL","BASE",100));
        }});
        test("Founding a confederation waits for three members and a written charter",()->{try(var f=new F()){
            String union=f.p.unions.create(a,"n:A",UnionType.CONFEDERATION,"Нова конфедерація");
            no(()->f.p.unions.sign(a,union,"n:A"));
            f.p.unions.inviteDraft(a,union,"n:A","n:B");f.p.unions.inviteDraft(a,union,"n:A","n:C");
            no(()->f.p.unions.sign(a,union,"n:A"));
            f.p.unions.amendDraft(a,union,"n:A","Погоджений конфедеративний договір та компетенції ради");
            f.p.unions.sign(a,union,"n:A");f.p.unions.sign(b,union,"n:B");f.p.unions.sign(c,union,"n:C");
            eq(string(f.p.unions.get(union),"status"),"ACTIVE");
        }});
        test("Adding a founding member resets signatures and invalidates old republican mandates",()->{try(var f=new F()){
            f.all("n:A",a);f.government(a,"n:A",Government.REPUBLIC);
            String union=f.p.unions.propose(a,"n:A",List.of("n:B","n:C"),UnionType.FEDERATION,"Федерація міст","Федеральна конституція та компетенції спільного уряду","defense,trade",0,"BASE");
            String mandate=f.p.decision(a,"n:A","UNION",union,"",0);f.voteAll(mandate,"n:A");f.p.unions.sign(a,union,"n:A");
            f.p.unions.inviteDraft(a,union,"n:A","n:D");
            ok(f.p.unions.members(union).stream().noneMatch(m->bool(m,"signed")));
            no(()->f.p.unions.sign(a,union,"n:A"));
        }});
        test("Only a draft's founding ruler can amend its terms",()->{try(var f=new F()){
            String union=f.p.unions.create(a,"n:A",UnionType.FEDERATION,"Федерація міст");
            f.p.unions.inviteDraft(a,union,"n:A","n:B");
            no(()->f.p.unions.amendDraft(b,union,"n:B","Змінена без згоди засновника конституція федерації"));
        }});
        test("A republic cannot promote itself without its voters' mandate",()->{try(var f=new F()){
            f.all("n:A",a);f.stage("n:A",Stage.SETTLEMENT);f.advance(a,"n:A",Stage.FREE_CITY);
            f.stage("n:A",Stage.COUNTY);
            for(var pair:List.of(Map.entry("n:B",b),Map.entry("n:C",c),Map.entry("n:D",d))){f.stage(pair.getKey(),Stage.BARONY);}
            // Previously accepted obligations survive a change to a republic.
            f.db.execute("UPDATE political_states SET government='MONARCHY' WHERE estate='n:A'");
            f.link(a,"n:B","n:A",b);f.link(a,"n:C","n:A",c);f.link(a,"n:D","n:A",d);
            f.db.execute("UPDATE political_states SET government='REPUBLIC' WHERE estate='n:A'");
            no(()->f.p.submit(a,"n:A",Stage.KINGDOM));
            f.advance(a,"n:A",Stage.KINGDOM);eq(f.p.state("n:A").government(),Government.REPUBLIC);
        }});
        test("A new mayor updates the governor record without changing treasury identity",()->{try(var f=new F()){
            administer(f,"n:A","n:B",a,b);f.seed(b,100);
            f.db.execute("UPDATE estates SET lord=? WHERE id='n:B'",person("B1"));f.p.syncDomains();
            eq(string(f.db.one("SELECT * FROM political_admin WHERE province='n:B'"),"governor"),person("B1"));
            eq(f.s.balance("e:n:B","BASE"),100L);
        }});
        geometry();
        System.out.println("Додаткових політичних сценаріїв пройдено: "+count);
    }
    static void geometry() throws Exception {
        var air=new CastleGeometry.Cell("air",false,true,false,false);
        var stone=new CastleGeometry.Cell("stone_bricks",true,false,true,false);
        var gate=new CastleGeometry.Cell("oak_fence_gate[open=false]",false,false,false,true);
        CastleGeometry.Blocks courtyard=(x,y,z)->{
            if(y==-1)return stone;
            if((x==-1 || x==12 || z==-1 || z==12) && y>=0 && y<4) {
                if(x==5 && z==-1 && y<2)return y==0?gate:air;
                return stone;
            }
            return air;
        };
        test("Castle scan measures a 12 by 12 interior with four-block walls",()->{
            var r=CastleGeometry.scan(courtyard,0,11,0,0,11,4);
            eq(r.area(),144);ok(r.walls() && r.perimeter() && r.gates());
        });
        test("An open gap invalidates the enclosed castle perimeter",()->{
            var r=CastleGeometry.scan((x,y,z)->x==4 && z==-1 && y>=0?air:courtyard.at(x,y,z),0,11,0,0,11,4);
            ok(!r.walls() && !r.perimeter());
        });
        test("A three-block wall fails the four-block requirement",()->{
            var r=CastleGeometry.scan((x,y,z)->y==3?air:courtyard.at(x,y,z),0,11,0,0,11,4);ok(!r.walls());
        });
        test("Interior obstacles and missing floor reduce usable courtyard area",()->{
            var r=CastleGeometry.scan((x,y,z)->x==0 && z==0 && y==0?stone:x==1 && z==0 && y==-1?air:courtyard.at(x,y,z),0,11,0,0,11,4);
            eq(r.area(),142);
        });
        test("A stone box with no organized entrance fails the gate criterion",()->{
            var r=CastleGeometry.scan((x,y,z)->x==5 && z==-1 && y>=0 && y<2?stone:courtyard.at(x,y,z),0,11,0,0,11,4);
            ok(r.walls() && r.perimeter() && !r.gates());
        });
        test("Changed gate state invalidates the castle fingerprint",()->{
            var before=CastleGeometry.scan(courtyard,0,11,0,0,11,4);
            var after=CastleGeometry.scan((x,y,z)->courtyard.at(x,y,z)==gate?new CastleGeometry.Cell("oak_fence_gate[open=true]",false,true,false,true):courtyard.at(x,y,z),0,11,0,0,11,4);
            ok(!before.fingerprint().equals(after.fingerprint()));
        });
        test("Castle scan rejects excessive regions before reading blocks",()->{
            no(()->CastleGeometry.scan((x,y,z)->{throw new AssertionError("Read before bounds check");},0,128,0,0,11,4));
        });
    }
}
