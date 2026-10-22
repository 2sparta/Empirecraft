package ua.empirecraft.feudal;

import java.math.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CurrencyService.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

/** Real H2 transactions, monetary invariants, persistence, and v1 migration; no mocked SQL. */
public final class CurrencyTests {
    static void currency(Fixture f,FeudalService.Actor who,String nation,String code,int ingots,long cents) throws Exception {
        f.s.createCurrency(who,nation,code,"Валюта "+code,code);f.gold(who,ingots);f.back(who,code,Integer.toString(ingots));f.s.mint(who,code,cents);
    }
    static void clean(Fixture f) throws Exception {eq(f.s.check(),List.of());}
    static void remove(Path root) throws Exception {try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("R/M: 15000/5000=3, додаткова емісія 10000 дає 1, погашення зберігає курс",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",15000,500000);eq(f.s.currencies.value("KRN"),gold("3"));
            f.s.mint(a,"KRN",1000000);eq(f.s.currencies.value("KRN"),gold("1"));eq(f.s.balance("e:n:A","BASE"),0L);
            f.s.transfer(a,"e:n:A","p:"+D,"KRN",10000);f.s.redeem(d,"p:"+D,"KRN",10000);
            eq(f.s.currencies.reserve("KRN"),gold("14900"));eq(number(f.s.currency("KRN"),"supply"),1490000L);eq(f.s.currencies.value("KRN"),gold("1"));clean(f);
        }});
        test("Внесок резерву підвищує ціну, burn зменшує лише M",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",15000,500000);f.gold(a,15000);f.back(a,"KRN","15000");eq(f.s.currencies.value("KRN"),gold("6"));
            f.s.currencies.burn(a,"e:n:A","KRN",250000);eq(f.s.currencies.value("KRN"),gold("12"));eq(f.s.currencies.reserve("KRN"),gold("30000"));clean(f);
        }});
        test("Обмін 100 KRN на 200 MRK переносить 300 ЗО, обидва курси сталі",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",15000,500000);currency(f,b,"n:B","MRK",9000,600000);
            f.s.transfer(a,"e:n:A","p:"+D,"KRN",10000);var q=f.s.currencies.quote(10000,"KRN","MRK");eq(q.coins(),20000L);
            f.s.currencies.convert(d,10000,"KRN","MRK",20000);eq(f.s.balance("p:"+D,"MRK"),20000L);
            eq(f.s.currencies.reserve("KRN"),gold("14700"));eq(f.s.currencies.reserve("MRK"),gold("9300"));
            eq(f.s.currencies.value("KRN"),gold("3"));eq(f.s.currencies.value("MRK"),gold("1.5"));clean(f);
        }});
        test("Дробові ЗО: 0.4 за монету, ціла видача лишає дробовий залишок",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","DUC",2,500);f.s.transfer(a,"e:n:A","p:"+D,"DUC",300);f.s.redeem(d,"p:"+D,"DUC",300);
            eq(f.s.currencies.goldBalance("p:"+D),gold("1.2"));String op=f.s.currencies.prepareGold(d,"OUT","p:"+D,1,"before","after");f.s.currencies.finishGold(d,op,true);
            eq(f.s.currencies.goldBalance("p:"+D),gold("0.2"));eq(f.s.currencies.reserve("DUC"),gold("0.8"));clean(f);
        }});
        test("Недостатній баланс, мінімум обміну та помилка після списання відкочують усе",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",30,1000);currency(f,b,"n:B","MRK",20,1000);f.s.transfer(a,"e:n:A","p:"+D,"KRN",100);
            no(()->f.s.currencies.convert(d,101,"KRN","MRK",0));no(()->f.s.currencies.convert(d,100,"KRN","MRK",151));
            f.db.execute("ALTER TABLE gold_accounts ADD CONSTRAINT test_block_target CHECK(owner<>'r:MRK' OR balance<=20)");
            try{f.s.currencies.convert(d,100,"KRN","MRK",0);throw new AssertionError("Очікувалася помилка БД");}catch(IllegalStateException expected){}
            eq(f.s.balance("p:"+D,"KRN"),100L);eq(f.s.balance("p:"+D,"MRK"),0L);eq(f.s.currencies.reserve("KRN"),gold("30"));eq(number(f.s.currency("KRN"),"supply"),1000L);clean(f);
        }});
        test("Резерв недоступний скарбниці та адміністративним звичайним переказам",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",30,1000);no(()->f.s.currencies.transferGold(a,"r:KRN","p:"+A,gold("1")));
            no(()->f.s.currencies.transferGold(admin,"r:KRN","e:n:A",gold("1")));
            no(()->f.s.currencies.prepareGold(a,"OUT","r:KRN",1,"before","after"));
            no(()->f.s.currencies.depositReserve(d,"p:"+D,"KRN",gold("1")));no(()->f.s.currencies.burn(d,"e:n:A","KRN",1));clean(f);
        }});
        test("Порожній обіг, нульовий резерв, повне погашення та повторний запуск валюти",()->{try(var f=new Fixture()){
            f.s.createCurrency(a,"n:A","KRN","Крона","K");no(()->f.s.currencies.value("KRN"));f.s.mint(a,"KRN",100);eq(f.s.currencies.value("KRN"),ZERO);
            no(()->f.s.currencies.redeem(a,"e:n:A","KRN",100));f.gold(a,3);f.back(a,"KRN","3");f.s.redeem(a,"e:n:A","KRN",100);
            eq(f.s.currencies.reserve("KRN"),ZERO);eq(number(f.s.currency("KRN"),"supply"),0L);no(()->f.s.currencies.value("KRN"));
            f.s.currencies.depositReserve(a,"e:n:A","KRN",gold("3"));f.s.mint(a,"KRN",100);eq(f.s.currencies.value("KRN"),gold("3"));clean(f);
        }});
        test("Спалення всього обігу зберігає резерв до наступної емісії",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",3,100);f.s.currencies.burn(a,"e:n:A","KRN",100);eq(f.s.currencies.reserve("KRN"),gold("3"));no(()->f.s.currencies.value("KRN"));
            f.s.mint(a,"KRN",300);eq(f.s.currencies.value("KRN"),gold("1"));clean(f);
        }});
        test("Решта округлення обміну належить гравцю, дрібні цикли не створюють золото",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",1,300);currency(f,b,"n:B","MRK",7,300);f.s.transfer(a,"e:n:A","p:"+D,"KRN",100);
            var q=f.s.currencies.convert(d,100,"KRN","MRK",0);eq(q.coins(),14L);require(q.change().signum()>0,"Має бути решта");
            eq(f.s.currencies.goldBalance("p:"+D),q.change());
            for(int i=0;i<30;i++){
                long k=f.s.balance("p:"+D,"MRK");if(k>0)f.s.currencies.convert(d,k,"MRK","KRN",0);
                long n=f.s.balance("p:"+D,"KRN");if(n>0){try{f.s.currencies.convert(d,n,"KRN","MRK",0);}catch(IllegalArgumentException tooSmall){break;}}
                clean(f);
            }
            BigDecimal holding=f.s.currencies.goldBalance("p:"+D).add(f.s.currencies.reserve("KRN").multiply(BigDecimal.valueOf(f.s.balance("p:"+D,"KRN"))).divide(BigDecimal.valueOf(number(f.s.currency("KRN"),"supply")),12,RoundingMode.DOWN)).add(f.s.currencies.reserve("MRK").multiply(BigDecimal.valueOf(f.s.balance("p:"+D,"MRK"))).divide(BigDecimal.valueOf(number(f.s.currency("MRK"),"supply")),12,RoundingMode.DOWN));
            require(holding.compareTo(gold("0.333333333333"))<=0,"Цикли не повинні створювати купівельну спроможність");
        }});
        test("Останнє погашення забирає дробовий пил; резерв не губиться",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",1,300);for(int i=0;i<300;i++)f.s.redeem(a,"e:n:A","KRN",1);
            eq(f.s.currencies.reserve("KRN"),ZERO);eq(f.s.currencies.goldBalance("e:n:A"),gold("1"));clean(f);
        }});
        test("Золоті входи/виходи одноразові; невдала видача повертає зарезервоване",()->{try(var f=new Fixture()){
            f.gold(d,3);String op=f.s.currencies.prepareGold(d,"OUT","p:"+D,2,"before","after");eq(f.s.currencies.goldBalance("p:"+D),gold("1"));
            no(()->f.s.currencies.prepareGold(d,"IN","p:"+D,1,"before","after"));no(()->f.s.currencies.finishGold(e,op,true));
            f.s.currencies.finishGold(admin,op,false);eq(f.s.currencies.goldBalance("p:"+D),gold("3"));no(()->f.s.currencies.finishGold(admin,op,true));
            String in=f.s.currencies.prepareGold(d,"IN","p:"+D,1,"before","after");f.s.currencies.finishGold(d,in,false);eq(f.s.currencies.goldBalance("p:"+D),gold("3"));clean(f);
        }});
        test("Золотий журнал переживає перезапуск без повторної видачі",()->{
            Path dir=Files.createTempDirectory("currency-restart-");String url="jdbc:h2:"+dir.resolve("db")+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0";String op;
            try(var f=new Fixture(url)){f.gold(d,3);op=f.s.currencies.prepareGold(d,"OUT","p:"+D,1,"before","after");}
            try(var f=new Fixture(url)){eq(f.s.currencies.goldBalance("p:"+D),gold("2"));require(f.s.check().size()==1,"Операція має залишатися невизначеною");f.s.currencies.finishGold(admin,op,true);clean(f);}
            try(var f=new Fixture(url)){eq(f.s.currencies.goldBalance("p:"+D),gold("2"));no(()->f.s.currencies.finishGold(admin,op,true));clean(f);}remove(dir);
        });
        test("Міграція v1 зберігає BASE без вигаданого золота; після погашення upgrade",()->{
            Path dir=Files.createTempDirectory("currency-v1-");String url="jdbc:h2:"+dir.resolve("db")+";DATABASE_TO_LOWER=TRUE";
            try(var connection=DriverManager.getConnection(url,"sa","");var st=connection.createStatement()){
                st.execute("CREATE TABLE schema_version(version INT PRIMARY KEY)");st.execute("INSERT INTO schema_version VALUES(1)");
                st.execute("CREATE TABLE currencies(code VARCHAR(8) PRIMARY KEY,name VARCHAR(64),symbol VARCHAR(8),issuer VARCHAR(80) UNIQUE,supply BIGINT NOT NULL CHECK(supply>=0))");
                st.execute("INSERT INTO currencies VALUES('OLD','Стара','O','n:A',100)");
                st.execute("CREATE TABLE accounts(owner VARCHAR(100),code VARCHAR(8),balance BIGINT NOT NULL CHECK(balance>=0),PRIMARY KEY(owner,code))");
                st.execute("INSERT INTO accounts VALUES('e:n:A','OLD',100),('r:OLD','BASE',100)");
            }
            Path backup=dir.resolve("migration.zip");try(var db=new Database(url,backup.toString())){require(Files.size(backup)>0,"Потрібна копія до міграції");}
            try(var f=new Fixture(url)){
                eq(string(f.s.currency("OLD"),"backing"),"LEGACY_BASE");eq(f.s.currencies.goldBalance("r:OLD"),ZERO);no(()->f.s.mint(a,"OLD",100));no(()->f.s.currencies.upgrade(a,"OLD"));
                f.s.redeem(a,"e:n:A","OLD",100);eq(f.s.balance("e:n:A","BASE"),100L);f.s.currencies.upgrade(a,"OLD");eq(string(f.s.currency("OLD"),"backing"),"GOLD");clean(f);
            }remove(dir);
        });
        test("Захист від NaN, експонентів, надмірної точності та переповнення",()->{try(var f=new Fixture()){
            no(()->gold("NaN"));no(()->gold("1e1000000"));no(()->gold("-1"));no(()->gold("0.0000000000001"));no(()->gold("9000000000000.1"));eq(gold("0,000000000001"),new BigDecimal("0.000000000001"));
            currency(f,a,"n:A","KRN",1,MAX_MONEY);no(()->f.s.mint(a,"KRN",1));eq(number(f.s.currency("KRN"),"supply"),MAX_MONEY);clean(f);
        }});
        test("Особиста історія доступна власнику, державна — правителю; переказ не змінює M",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",3,1000);f.s.transfer(a,"e:n:A","p:"+D,"KRN",100);f.s.transfer(d,"p:"+D,"p:"+E,"KRN",50);
            eq(number(f.s.currency("KRN"),"supply"),1000L);require(!f.s.currencies.history(e,null).isEmpty(),"Одержувач має бачити переказ");
            no(()->f.s.currencies.history(d,"KRN"));require(!f.s.currencies.history(a,"KRN").isEmpty(),"Король має бачити журнал");clean(f);
        }});
        test("Обмін і резерви зберігаються після перезапуску та онлайн-копії",()->{
            Path dir=Files.createTempDirectory("currency-backup-");String url="jdbc:h2:"+dir.resolve("db")+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0";
            try(var f=new Fixture(url)){currency(f,a,"n:A","KRN",30,1000);currency(f,b,"n:B","MRK",20,1000);f.s.transfer(a,"e:n:A","p:"+D,"KRN",100);f.s.currencies.convert(d,100,"KRN","MRK",0);f.db.backup(dir.resolve("backup.zip").toString());}
            try(var f=new Fixture(url)){eq(f.s.balance("p:"+D,"MRK"),150L);eq(f.s.currencies.reserve("KRN"),gold("27"));eq(f.s.currencies.reserve("MRK"),gold("23"));clean(f);}
            Path restored=Files.createDirectories(dir.resolve("restored"));try(var zip=new java.util.zip.ZipInputStream(Files.newInputStream(dir.resolve("backup.zip")))){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){Path dest=restored.resolve(entry.getName()).normalize();require(dest.startsWith(restored),"Невірний шлях");if(entry.isDirectory())Files.createDirectories(dest);else Files.copy(zip,dest);}}
            try(var f=new Fixture("jdbc:h2:"+restored.resolve("db")+";DATABASE_TO_LOWER=TRUE")){eq(f.s.balance("p:"+D,"MRK"),150L);eq(f.s.currencies.reserve("KRN"),gold("27"));clean(f);}remove(dir);
        });
        test("Данина валютою сюзерена після обміну не змінює M та резерв",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",30,1000);currency(f,b,"n:B","MRK",20,1000);
            f.s.transfer(a,"e:n:A","p:"+A,"KRN",100);f.s.currencies.convert(a,100,"KRN","MRK",0);
            f.s.transfer(a,"p:"+A,"e:n:A","MRK",150);f.link(a,"n:A","n:B",150,"MRK",b);
            long supply=number(f.s.currency("MRK"),"supply");f.time.value+=7*FeudalService.DAY;f.s.tick();
            eq(number(f.s.currency("MRK"),"supply"),supply);eq(f.s.currencies.reserve("MRK"),gold("23"));
            eq(f.s.balance("e:n:A","MRK"),0L);eq(f.s.balance("e:n:B","MRK"),1150L);clean(f);
        }});
        System.out.println("Валютних тестів пройдено: "+count);
    }
}
