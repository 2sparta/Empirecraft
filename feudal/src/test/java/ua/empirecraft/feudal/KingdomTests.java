package ua.empirecraft.feudal;

import java.nio.file.*;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.PoliticalModel.*;

/** Real H2 regressions for derived membership, capital identity and BASE retirement. */
public final class KingdomTests {
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("A fresh ledger has no BASE and item barter works without any currency",()->{
            try(var db=new Database("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE")){
                db.retireBase();var s=new FeudalService(db,new Time(),7*FeudalService.DAY,FeudalService.DAY,32,12,2);
                eq(db.query("SELECT code FROM currencies"),List.of());no(()->s.currency("BASE"));
                var currencies=new TradeCurrencies(s);eq(currencies.initial(UUID.fromString(D)),"");eq(currencies.personal(UUID.fromString(D),"").balance(),0L);
                var commerce=new CommerceService(s);String op=commerce.trade(d,E,List.of(new CommerceService.InventoryChange(D,"before D","after D"),new CommerceService.InventoryChange(E,"before E","after E")),List.of());commerce.finish(op,true);
                eq(db.query("SELECT * FROM accounts"),List.of());no(()->s.prepareVault(d,"IN",100));
            }
        });
        test("Retirement survives restart, archives Vault and keeps city coins and physical gold exactly",()->{
            Path dir=Files.createTempDirectory("retire-base-");String url="jdbc:h2:"+dir.resolve("data")+";DATABASE_TO_LOWER=TRUE";
            try(var db=new Database(url)){
                db.execute("INSERT INTO currencies VALUES('BASE','Vault','$',NULL,0,'BASE'),('OLD','City coin','O','n:A',700,'LEGACY_BASE')");
                db.execute("INSERT INTO accounts VALUES('p:holder','BASE',900),('r:OLD','BASE',700),('e:n:A','OLD',700)");
                db.execute("INSERT INTO gold_accounts VALUES('r:OLD',12.25)");db.retireBase();
                eq(db.one("SELECT code FROM currencies WHERE code='BASE'"),null);eq(db.query("SELECT * FROM accounts WHERE code='BASE'"),List.of());
                eq(number(db.one("SELECT * FROM retired_base_accounts WHERE owner='p:holder'"),"balance"),900L);
                eq(number(db.one("SELECT * FROM accounts WHERE code='OLD'"),"balance"),700L);
                eq(number(db.one("SELECT * FROM currencies WHERE code='OLD'"),"supply"),700L);eq(string(db.one("SELECT * FROM currencies WHERE code='OLD'"),"backing"),"GOLD");
                eq(((java.math.BigDecimal)db.one("SELECT * FROM gold_accounts WHERE owner='r:OLD'").get("balance")).toPlainString(),"12.250000000000");
            }
            try(var db=new Database(url)){db.retireBase();eq(db.query("SELECT * FROM retired_base_accounts").size(),2);eq(db.one("SELECT code FROM currencies WHERE code='BASE'"),null);}
            try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        });
        var a=PoliticsTests.a;var b=PoliticsTests.b;var admin=PoliticsTests.reviewer;
        test("Capital city can create one crown currency at its own eligible level",()->{try(var f=new PoliticsTests.F()){
            f.s.derivedKingdoms=true;f.s.kingdomCapitals.put("n:A","t:A");f.stage("n:A",Stage.SETTLEMENT);f.stage("t:A",Stage.COUNTY);
            var market=new CurrencyMarket(f.s,f.p);eq(f.p.sovereign("t:A"),true);eq(market.eligible("n:A"),true);eq(market.printInterval("n:A"),15000L);
            f.s.createCurrency(a,"t:A","KRN","Крона","K");eq(string(f.s.currency("KRN"),"issuer"),"n:A");
            no(()->f.s.createCurrency(a,"n:A","TAL","Талер","T"));eq(market.treasuryCurrency("t:A"),"KRN");
            market.printingOffice=(person,estate)->person.equals(a.id());eq(market.canPrint(a,"KRN"),true);eq(market.canPrint(b,"KRN"),false);
            market.start(a,"KRN",1);f.time.now+=15000;market.tick();eq(number(f.s.currency("KRN"),"supply"),100L);
        }});
        test("A capital under a foreign crown cannot evade currency sovereignty",()->{try(var f=new PoliticsTests.F()){
            f.s.derivedKingdoms=true;f.s.kingdomCapitals.put("n:A","t:A");f.stage("t:A",Stage.COUNTY);f.s.forceParent(admin,"n:A","n:B");
            var market=new CurrencyMarket(f.s,f.p);eq(f.p.sovereign("t:A"),false);eq(market.eligible("n:A"),false);no(()->f.s.createCurrency(a,"t:A","KRN","Крона","K"));
        }});
        test("All nested vassals resolve to one kingdom; independence restores the complete subtree",()->{try(var f=new PoliticsTests.F()){
            f.s.derivedKingdoms=true;f.s.forceParent(admin,"n:B","n:A");f.s.forceParent(admin,"n:C","n:B");
            eq(KingdomTree.root(f.s,"t:B"),"n:A");eq(KingdomTree.root(f.s,"t:C"),"n:A");
            f.s.release(a,"n:B");eq(KingdomTree.root(f.s,"t:B"),"n:B");eq(KingdomTree.root(f.s,"t:C"),"n:B");
        }});
        test("Capital aliases move their crown and vassals together, and reject self-submission",()->{try(var f=new PoliticsTests.F()){
            f.s.derivedKingdoms=true;f.s.kingdomCapitals.put("n:A","t:A");f.s.kingdomCapitals.put("n:B","t:B");
            String offer=f.s.offer(a,"t:B","t:A",0,"","висока","оборона",0);f.s.accept(b,offer);
            eq(f.s.parent("n:B"),"n:A");eq(f.s.parent("t:B"),"n:B");eq(KingdomTree.root(f.s,"t:B"),"n:A");
            no(()->f.s.offer(a,"t:A","n:A",0,"","висока","оборона",0));f.s.release(a,"t:B");eq(f.s.parent("n:B"),null);
        }});
        test("Native projection never reattaches a released town during the next snapshot",()->{try(var f=new PoliticsTests.F()){
            f.s.derivedKingdoms=true;f.s.release(a,"t:A");eq(f.s.parent("t:A"),null);f.s.sync(new Model.Snapshot(f.entities,f.citizenship));eq(f.s.parent("t:A"),null);
        }});
        test("Administrative mergers are included and a cycle aborts before native mutations",()->{try(var f=new PoliticsTests.F()){
            f.db.execute("UPDATE political_states SET merged_into='n:A' WHERE estate='n:B'");eq(KingdomTree.root(f.s,"t:B"),"n:A");
            f.db.execute("UPDATE political_states SET merged_into='n:B' WHERE estate='n:A'");no(()->KingdomTree.root(f.s,"t:B"));
        }});
        System.out.println("Derived kingdom and city currency regressions: "+count);
    }
}
