package ua.empirecraft.feudal;

import java.util.*;
import java.nio.file.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CurrencyTests.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.CurrencyService.*;
import static ua.empirecraft.feudal.PoliticalModel.*;

/** Real H2, elapsed wall time, live political authority and conservation across restarts. */
public final class CurrencyMarketTests {
    static CurrencyMarket market(Fixture f)throws Exception{var m=new CurrencyMarket(f.s,new PoliticsService(f.s,new PoliticsRules(k->-1)));m.printingOffice=(person,estate)->true;return m;}
    static void county(Fixture f)throws Exception{f.db.execute("UPDATE political_states SET stage='COUNTY' WHERE estate='n:A'");}
    public static void main(String[] args)throws Exception{
        Class.forName("org.h2.Driver");count=0;
        test("All branches have explicit eligibility and print cadence",()->{
            eq(CurrencyMarket.interval(Stage.SETTLEMENT),0L);eq(CurrencyMarket.interval(Stage.BARONY),0L);eq(CurrencyMarket.interval(Stage.TRIBE),0L);eq(CurrencyMarket.interval(Stage.FREE_CITY),0L);
            for(Stage stage:List.of(Stage.COUNTY,Stage.PRINCIPALITY,Stage.HORDE,Stage.CENTRAL_STATE))eq(CurrencyMarket.interval(stage),15000L);
            for(Stage stage:List.of(Stage.DUCHY,Stage.KHANATE,Stage.TRADE_REPUBLIC))eq(CurrencyMarket.interval(stage),10000L);
            eq(CurrencyMarket.interval(Stage.KINGDOM),7000L);eq(CurrencyMarket.interval(Stage.CENTRAL_KINGDOM),7000L);
            for(Stage stage:List.of(Stage.EMPIRE,Stage.CENTRAL_EMPIRE,Stage.KHAGANATE))eq(CurrencyMarket.interval(stage),5000L);
        });
        test("Creation requires real sovereignty and county-equivalent political rank",()->{try(var f=new Fixture()){
            var m=market(f);f.db.execute("UPDATE political_states SET stage='BARONY' WHERE estate='n:A'");no(()->f.s.createCurrency(a,"n:A","KRN","Крона","K"));county(f);f.s.createCurrency(a,"n:A","KRN","Крона","K");
            f.db.execute("INSERT INTO ties VALUES('n:B','n:A',NULL)");no(()->f.s.createCurrency(b,"n:B","MRK","Марка","M"));no(()->f.s.createCurrency(admin,"n:C","ROM","Рим","R"));
        }});
        test("Only ruler-appointed current citizens can manage currency; OP is not implicit banker",()->{try(var f=new Fixture()){
            var m=market(f);f.s.createCurrency(a,"n:A","KRN","Крона","K");no(()->m.start(d,"KRN",1));no(()->m.banker(d,"n:A",E,true));no(()->m.banker(a,"n:A",B,true));
            m.banker(a,"n:A",D,true);f.s.controlTreasury(d,f.s.estate("n:A"));m.start(d,"KRN",2);m.banker(a,"n:A",D,false);f.time.value+=7000;m.tick();eq(string(m.job("KRN"),"status"),"PAUSED");eq(number(f.s.currency("KRN"),"supply"),0L);no(()->m.start(admin,"KRN",1));
        }});
        test("No immediate mint bypass; exactly one whole coin and corresponding price per due interval",()->{try(var f=new Fixture()){
            var m=market(f);county(f);f.s.createCurrency(a,"n:A","KRN","Крона","K");f.gold(a,100);f.back(a,"KRN","100");no(()->f.s.mint(a,"KRN",10000));m.start(a,"KRN",3);
            f.time.value+=14999;m.tick();eq(f.s.balance("e:n:A","KRN"),0L);f.time.value++;m.tick();eq(f.s.balance("e:n:A","KRN"),100L);eq(f.s.currencies.value("KRN"),gold("100"));m.tick();eq(f.s.balance("e:n:A","KRN"),100L);
            f.time.value+=15000;m.tick();eq(f.s.balance("e:n:A","KRN"),200L);eq(f.s.currencies.value("KRN"),gold("50"));eq(number(m.job("KRN"),"remaining"),1L);clean(f);
        }});
        test("Preview includes exact future dilution, rank cadence and ETA without changing state",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",1500,50000);var m=market(f);county(f);var preview=m.preview("KRN",1000);eq(preview.get("after"),"1");eq(preview.get("seconds"),15000L);eq(preview.get("interval"),15L);eq(number(f.s.currency("KRN"),"supply"),50000L);
        }});
        test("Cancellation preserves already printed coins and never refunds invented backing",()->{try(var f=new Fixture()){
            var m=market(f);f.s.createCurrency(a,"n:A","KRN","Крона","K");m.start(a,"KRN",5);f.time.value+=7000;m.tick();m.cancel(a,"KRN");f.time.value+=70000;m.tick();eq(number(f.s.currency("KRN"),"supply"),100L);eq(f.s.currencies.reserve("KRN"),ZERO);clean(f);
        }});
        test("Loss of independence pauses printing and selects sovereign currency for all descendant treasuries",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",100,10000);currency(f,b,"n:B","MRK",100,10000);var m=market(f);m.start(b,"MRK",10);
            f.db.execute("INSERT INTO ties VALUES('n:B','n:A',NULL)");f.time.value+=7000;m.tick();eq(string(m.job("MRK"),"status"),"PAUSED");eq(m.treasuryCurrency("n:B"),"KRN");eq(m.treasuryCurrency("t:D"),"KRN");no(()->m.resume(b,"MRK"));no(()->f.s.currencies.quote(100,"KRN","MRK"));
            f.s.transfer(a,"e:n:A","p:"+D,"KRN",100);f.s.transfer(d,"p:"+D,"e:t:D","KRN",100);f.seed(d,100);no(()->f.s.transfer(d,"p:"+D,"e:t:D","BASE",100));clean(f);
        }});
        test("Banker departure and ruler succession invalidate old authority and pause queues",()->{try(var f=new Fixture()){
            var m=market(f);f.s.createCurrency(a,"n:A","KRN","Крона","K");m.banker(a,"n:A",D,true);m.start(d,"KRN",3);var members=new HashSet<>(f.citizens);members.remove(D+"@n:A");f.s.sync(new Model.Snapshot(f.entities,members));f.time.value+=7000;m.tick();eq(string(m.job("KRN"),"status"),"PAUSED");no(()->m.resume(d,"KRN"));
            f.db.execute("UPDATE estates SET lord=? WHERE id='n:A'",E);eq(m.manages(d,"n:A"),false);m.resume(e,"KRN");eq(string(m.job("KRN"),"operator"),E);
        }});
        test("History contains actual changes and never fabricates pre-installation samples",()->{try(var f=new Fixture()){
            var m=market(f);f.s.createCurrency(a,"n:A","KRN","Крона","K");f.gold(a,10);f.back(a,"KRN","10");m.start(a,"KRN",2);f.time.value+=7000;m.tick();eq(m.history("KRN","@GOLD","1h").size(),1);eq(m.history("KRN","@GOLD","1h").get(0).get("price"),"10");
            f.time.value+=7000;m.tick();eq(m.history("KRN","@GOLD","1h").get(0).get("price"),"5");no(()->m.history("KRN","@GOLD","999d"));clean(f);
        }});
        test("Gold purchase conserves gold, consumes treasury liquidity and changes R/M",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",100,10000);var m=market(f);f.gold(d,10);var before=f.s.currencies.value("KRN");var q=m.exchange.quote("@GOLD","KRN","3");f.s.currencies.buyGold(d,"3","KRN",q.targetGross()-q.targetBurn());eq(f.s.balance("p:"+D,"KRN"),q.targetGross()-q.targetBurn());eq(f.s.currencies.value("KRN").compareTo(before)>0,true);eq(f.s.balance("e:n:A","KRN"),10000L-q.targetGross());clean(f);
            no(()->f.s.currencies.buyGold(d,"1000","KRN",0));clean(f);
        }});
        test("A disk restart retains remaining work without a downtime catch-up burst or duplicate coin",()->{
            Path root=Files.createTempDirectory("currency-market-");String url="jdbc:h2:"+root.resolve("db")+";DATABASE_TO_LOWER=TRUE";
            try{
                try(var f=new Fixture(url)){var m=market(f);f.s.createCurrency(a,"n:A","KRN","Крона","K");m.start(a,"KRN",10);f.time.value+=7000;m.tick();eq(number(m.job("KRN"),"remaining"),9L);}
                try(var f=new Fixture(url)){var m=market(f);f.time.value+=86400000;m.tick();eq(number(m.job("KRN"),"remaining"),8L);eq(number(f.s.currency("KRN"),"supply"),200L);m.tick();eq(number(f.s.currency("KRN"),"supply"),200L);clean(f);}
            }finally{remove(root);}
        });
        test("Future tribute follows the sovereign gold currency; existing invoices are never rewritten",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",100,10000);currency(f,b,"n:B","MRK",200,10000);f.link(a,"n:B","n:A",100,"MRK",b);
            var m=market(f);String contract=string(f.s.contract("n:B"),"id");f.time.value+=7*FeudalService.DAY;f.s.tick();
            eq(string(f.s.contract("n:B"),"code"),"KRN");eq(number(f.s.contract("n:B"),"amount"),200L);eq(string(f.db.one("SELECT * FROM invoices WHERE contract=?",contract),"code"),"KRN");
            no(()->f.s.offer(a,"n:C","n:A",100,"MRK","висока","оборона",0));clean(f);
        }});
        test("BASE tribute is paused for explicit agreement instead of inventing a gold exchange rate",()->{try(var f=new Fixture()){
            f.link(a,"n:B","n:A",100,"BASE",b);currency(f,a,"n:A","KRN",100,10000);var m=market(f);String contract=string(f.s.contract("n:B"),"id");f.time.value+=7*FeudalService.DAY;f.s.tick();
            eq(string(f.s.contract("n:B"),"code"),"BASE");eq(f.db.query("SELECT * FROM invoices WHERE contract=?",contract).size(),0);eq(f.db.query("SELECT * FROM currency_tribute_pauses WHERE contract=?",contract).size(),1);clean(f);
        }});
        test("A failed queue write rolls back supply, treasury and history together",()->{try(var f=new Fixture()){
            var m=market(f);f.s.createCurrency(a,"n:A","KRN","Крона","K");m.start(a,"KRN",2);long samples=f.db.query("SELECT seq FROM currency_prices").size();
            f.db.execute("CREATE TRIGGER reject_job BEFORE UPDATE ON currency_print_jobs FOR EACH ROW CALL 'ua.empirecraft.feudal.CurrencyMarketTests$Reject'");f.time.value+=7000;
            try{m.tick();throw new AssertionError("Expected database rejection");}catch(IllegalStateException expected){}
            eq(number(f.s.currency("KRN"),"supply"),0L);eq(f.s.balance("e:n:A","KRN"),0L);eq(number(m.job("KRN"),"remaining"),2L);eq((long)f.db.query("SELECT seq FROM currency_prices").size(),samples);
            f.db.execute("DROP TRIGGER reject_job");m.tick();eq(number(f.s.currency("KRN"),"supply"),100L);eq(number(m.job("KRN"),"remaining"),1L);clean(f);
        }});
        test("Capital departure immediately revokes printing and pauses the persisted queue without another coin",()->{try(var f=new Fixture()){
            var m=market(f);f.s.createCurrency(a,"n:A","KRN","Крона","K");m.banker(a,"n:A",D,true);
            var office=new HashSet<String>(List.of(A,D));m.printingOffice=(person,issuer)->office.contains(person);eq(m.canPrint(d,"KRN"),true);m.start(d,"KRN",3);
            office.remove(D);eq(m.manages(d,"n:A"),true);eq(m.canPrint(d,"KRN"),false);f.time.value+=7000;m.tick();
            eq(string(m.job("KRN"),"status"),"PAUSED");eq(number(f.s.currency("KRN"),"supply"),0L);no(()->m.resume(d,"KRN"));no(()->m.start(d,"KRN",1));
            office.add(D);m.resume(d,"KRN");f.time.value+=7000;m.tick();eq(number(f.s.currency("KRN"),"supply"),100L);clean(f);
        }});
        System.out.println("Currency market scenarios: "+count);
    }
    public static final class Reject implements org.h2.api.Trigger {public void fire(java.sql.Connection c,Object[] oldRow,Object[] newRow)throws java.sql.SQLException{throw new java.sql.SQLException("queue write rejected");}}
}
