package ua.empirecraft.feudal;

import java.math.BigDecimal;
import java.nio.file.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CurrencyService.*;
import static ua.empirecraft.feudal.CurrencyTests.*;
import static ua.empirecraft.feudal.Database.*;

public final class PhysicalGoldTests {
    public static void main(String[] args)throws Exception{
        Class.forName("org.h2.Driver");count=0;
        test("Legacy gold migrates once without affecting reserves or pending withdrawals",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",100,10000);f.gold(d,7);String op=f.s.currencies.prepareGold(d,"OUT","p:"+D,2,"before","after");
            f.s.currencies.migratePersonalGold();eq(f.s.currencies.goldBalance("p:"+D),ZERO);eq(f.s.currencies.payable(D),gold("5"));f.s.currencies.migratePersonalGold();eq(f.s.currencies.payable(D),gold("5"));
            CurrencyExchangeTests.exchange(f);f.s.currencies.finishGold(admin,op,false);eq(f.s.currencies.payable(D),gold("7"));eq(f.s.currencies.reserve("KRN"),gold("100"));clean(f);
        }});
        test("Physical purchases settle a saved inventory intent exactly once and never create a personal bank",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",1000,100000);var x=CurrencyExchangeTests.exchange(f);var q=x.quote("@GOLD","KRN","2");String op=f.s.currencies.prepareGold(d,"IN","b:KRN",2,"before","after");
            x.executePhysical(d,op,ZERO,q.stamp());eq(string(f.db.one("SELECT * FROM gold_ops WHERE id=?",op),"status"),"APPLIED");eq(f.s.currencies.goldBalance("p:"+D),ZERO);eq(f.s.currencies.payable(D),q.change());eq(f.s.balance("p:"+D,"KRN"),q.targetGross()-q.targetBurn());no(()->x.executePhysical(d,op,ZERO,q.stamp()));clean(f);
        }});
        test("Rejected physical purchase leaves intent recoverable as ingots, not coins, after restart",()->{
            Path dir=Files.createTempDirectory("physical-gold-");String url="jdbc:h2:"+dir.resolve("db")+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0";String op;
            try(var f=new Fixture(url)){currency(f,a,"n:A","KRN",1000,100000);var x=CurrencyExchangeTests.exchange(f);op=f.s.currencies.prepareGold(d,"IN","b:KRN",2,"before","after");no(()->x.executePhysical(d,op,gold("10000"),null));eq(f.s.balance("p:"+D,"KRN"),0L);}
            try(var f=new Fixture(url)){CurrencyExchangeTests.exchange(f);f.s.currencies.finishGold(admin,op,true);eq(f.s.currencies.payable(D),gold("2"));eq(f.s.currencies.goldBalance("p:"+D),ZERO);no(()->f.s.currencies.finishGold(admin,op,true));clean(f);}remove(dir);
        });
        test("Physical payout reserves debt once and a failed inventory delivery restores it",()->{try(var f=new Fixture()){
            f.gold(d,3);f.s.currencies.migratePersonalGold();String op=f.s.currencies.prepareGold(d,"OUT","d:"+D,2,"before","after");eq(f.s.currencies.payable(D),gold("1"));no(()->f.s.currencies.prepareGold(d,"OUT","d:"+D,1,"before","after"));f.s.currencies.finishGold(admin,op,false);eq(f.s.currencies.payable(D),gold("3"));no(()->f.s.currencies.finishGold(admin,op,false));clean(f);
        }});
        System.out.println("Physical gold scenarios: "+count);
    }
}
