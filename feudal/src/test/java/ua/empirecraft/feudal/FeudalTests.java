package ua.empirecraft.feudal;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Integration tests against the real embedded database, without a Minecraft mock server. */
public final class FeudalTests {
    interface Test { void run() throws Exception; }
    static int count;
    static final String A="00000000-0000-0000-0000-000000000001", B="00000000-0000-0000-0000-000000000002", C="00000000-0000-0000-0000-000000000003", D="00000000-0000-0000-0000-000000000004", E="00000000-0000-0000-0000-000000000005";
    static Actor a=new Actor(A,false),b=new Actor(B,false),c=new Actor(C,false),d=new Actor(D,false),e=new Actor(E,false),admin=new Actor("консоль",true);
    static class Time extends Clock {
        long value=1_800_000_000_000L;
        public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;}
        public Instant instant(){return Instant.ofEpochMilli(value);} public long millis(){return value;}
    }
    static class Fixture implements AutoCloseable {
        final Database db; final Time time=new Time(); final FeudalService s;
        final List<Estate> entities=List.of(new Estate("n:A",Kind.KINGDOM,"Русь","n:A",A,true),new Estate("n:B",Kind.KINGDOM,"Візантія","n:B",B,true),new Estate("n:C",Kind.KINGDOM,"Рим","n:C",C,true),new Estate("t:D",Kind.BARONY,"Київ","n:A",D,true),new Estate("t:E",Kind.BARONY,"Черкаси","n:A",E,true));
        final Set<String> citizens=Set.of(A+"@n:A",B+"@n:B",C+"@n:C",D+"@n:A",E+"@n:A");
        Fixture() throws Exception {this("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE");}
        Fixture(String url) throws Exception {db=new Database(url);s=new FeudalService(db,time,7*FeudalService.DAY,FeudalService.DAY,32,12,2);s.sync(new Snapshot(entities,citizens));}
        String duchy() {return s.create(a,"n:A",Kind.DUCHY,"Київське");}
        void seed(Actor who,long amount) {String id=s.prepareVault(who,"IN",amount);s.finishVault(who,id,true);}
        void gold(Actor who,int ingots) {while(ingots>0){int n=Math.min(2304,ingots);String id=s.currencies.prepareGold(who,"IN","p:"+who.id(),n,"before","after");s.currencies.finishGold(who,id,true);ingots-=n;}}
        void back(Actor who,String code,String amount) {s.currencies.depositReserve(who,"p:"+who.id(),code,CurrencyService.gold(amount));}
        void link(Actor sender,String child,String parent,long amount,String code,Actor... signers) throws Exception {String p=s.offer(sender,child,parent,amount,code,"висока","оборона",0);for(Actor signer:signers)if(db.one("SELECT id FROM proposals WHERE id=?",p)!=null)s.accept(signer,p);}
        public void close() throws Exception {db.close();}
    }
    static void eq(Object actual,Object expected){if(!Objects.equals(actual,expected))throw new AssertionError("Очікувалося "+expected+", отримано "+actual);}
    static void no(Test test) throws Exception {try{test.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Операція мала бути відхилена");}
    static void test(String name,Test test) throws Exception {test.run();count++;System.out.println("✓ "+name);}
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        test("Дробові суми точні; NaN, мінус і третій десятковий знак заборонені",()->{eq(money("1,25"),125L);eq(money("0.01"),1L);no(()->money("-1"));no(()->money("NaN"));no(()->money("1.001"));});
        test("Ідемпотентний імпорт і незмінні UUID",()->{try(var f=new Fixture()){f.s.sync(new Snapshot(f.entities,f.citizens));eq(f.s.estates().size(),5);eq(f.s.parent("t:D"),"n:A");var changed=new ArrayList<>(f.entities);changed.set(0,new Estate("n:A",Kind.KINGDOM,"Нова Русь","n:A",A,true));f.s.sync(new Snapshot(changed,f.citizens));eq(f.s.resolve("держава:Нова_Русь").id(),"n:A");}});
        test("Чужі призначення і неправильні рівні відхиляються",()->{try(var f=new Fixture()){no(()->f.s.create(d,"n:A",Kind.DUCHY,"Чуже"));String duke=f.duchy();no(()->f.s.offer(a,duke,"n:B",0,"BASE","висока","оборона",0));no(()->f.s.offer(d,"t:D","t:E",0,"BASE","висока","оборона",0));}});
        test("Договір чинний лише після згоди всіх правителів",()->{try(var f=new Fixture()){String duke=f.duchy();String p=f.s.offer(a,"t:D",duke,500,"BASE","висока","оборона",0);eq(f.s.parent("t:D"),"n:A");no(()->f.s.accept(e,p));no(()->f.s.accept(admin,p));f.s.accept(d,p);eq(f.s.parent("t:D"),duke);eq(number(f.s.contract("t:D"),"amount"),500L);}});
        test("Зовнішній васалітет зберігає Towny-держави, забороняє цикли",()->{try(var f=new Fixture()){f.link(a,"n:B","n:A",0,"BASE",b);f.link(b,"n:C","n:B",0,"BASE",c);no(()->f.s.offer(c,"n:A","n:C",0,"BASE","висока","оборона",0));eq(f.s.estate("n:B").nation(),"n:B");}});
        test("Перехід до нового сюзерена потребує згоди попереднього",()->{try(var f=new Fixture()){f.link(a,"n:B","n:A",0,"BASE",b);String p=f.s.offer(c,"n:B","n:C",100,"BASE","висока","оборона",0);f.s.accept(b,p);eq(f.s.parent("n:B"),"n:A");f.s.accept(a,p);eq(f.s.parent("n:B"),"n:C");}});
        test("Відкликання пропозиції та строк дії",()->{try(var f=new Fixture()){String p=f.s.offer(a,"n:B","n:A",0,"BASE","висока","оборона",0);f.s.reject(b,p);no(()->f.s.accept(b,p));String id=f.s.offer(a,"n:B","n:A",0,"BASE","висока","оборона",0);f.time.value+=FeudalService.DAY+1;no(()->f.s.accept(b,id));eq(f.s.parent("n:B"),null);}});
        test("Зміна правителя робить старий підпис недійсним",()->{try(var f=new Fixture()){String p=f.s.offer(a,"n:B","n:A",0,"BASE","висока","оборона",0);var changed=new ArrayList<>(f.entities);changed.set(1,new Estate("n:B",Kind.KINGDOM,"Візантія","n:B",C,true));f.s.sync(new Snapshot(changed,f.citizens));no(()->f.s.accept(b,p));no(()->f.s.accept(c,p));}});
        test("Регіон передається зі згодою нового та попереднього правителів",()->{try(var f=new Fixture()){String duke=f.duchy();String p=f.s.nominate(a,duke,E);eq(f.s.estate(duke).lord(),A);f.s.accept(e,p);eq(f.s.estate(duke).lord(),E);no(()->f.s.nominate(e,duke,B));}});
        test("Після виходу регіонального правителя корона зберігає землю й договори",()->{try(var f=new Fixture()){String duke=f.duchy();f.s.accept(e,f.s.nominate(a,duke,E));f.link(e,"t:D",duke,10,"BASE",d,a);var citizens=new HashSet<>(f.citizens);citizens.remove(E+"@n:A");f.s.sync(new Snapshot(f.entities,citizens));eq(f.s.estate(duke).active(),true);eq(f.s.estate(duke).lord(),A);eq(f.s.parent("t:D"),duke);eq(number(f.s.contract("t:D"),"amount"),10L);}});
        test("Видалення Towny-держави архівує її та звільняє зовнішнього васала",()->{try(var f=new Fixture()){f.link(a,"n:B","n:A",100,"BASE",b);var changed=f.entities.stream().filter(x->!x.id().equals("n:A") && !x.id().startsWith("t:")).toList();f.s.sync(new Snapshot(changed,Set.of(B+"@n:B",C+"@n:C")));eq(f.s.estate("n:A").active(),false);eq(f.s.parent("n:B"),null);f.time.value+=10*FeudalService.DAY;f.s.tick();eq(f.db.query("SELECT * FROM invoices").size(),0);}});
        test("Створення без емісії; незабезпечений випуск дозволений лише королю",()->{try(var f=new Fixture()){f.s.createCurrency(a,"n:A","SOL","Солід","S");eq(number(f.s.currency("SOL"),"supply"),0L);f.s.mint(a,"SOL",1);eq(f.s.currencies.value("SOL"),CurrencyService.ZERO);no(()->f.s.createCurrency(a,"n:A","TAL","Талер","T"));no(()->f.s.mint(d,"SOL",100));}});
        test("Золотий резерв, переказ і погашення зберігають курс",()->{try(var f=new Fixture()){
            f.s.createCurrency(a,"n:A","SOL","Солід","S");f.gold(a,124);f.back(a,"SOL","123.45");f.s.mint(a,"SOL",12345);
            f.s.transfer(a,"e:n:A","p:"+D,"SOL",2345);f.s.redeem(d,"p:"+D,"SOL",2345);
            eq(f.s.currencies.goldBalance("p:"+D),CurrencyService.gold("23.45"));eq(f.s.currencies.reserve("SOL"),CurrencyService.gold("100"));
            eq(number(f.s.currency("SOL"),"supply"),10000L);eq(f.s.check(),List.of());
        }});
        test("Переказ без достатніх коштів не змінює жодного рахунку",()->{try(var f=new Fixture()){f.seed(d,100);no(()->f.s.transfer(d,"p:"+D,"p:"+E,"BASE",101));eq(f.s.balance("p:"+D,"BASE"),100L);eq(f.s.balance("p:"+E,"BASE"),0L);no(()->f.s.transfer(d,"e:n:A","p:"+D,"BASE",1));}});
        test("Vault не зараховується двічі, невизначена операція блокує повтор",()->{try(var f=new Fixture()){String id=f.s.prepareVault(d,"IN",100);no(()->f.s.prepareVault(d,"IN",100));f.s.finishVault(admin,id,true);no(()->f.s.finishVault(admin,id,true));eq(f.s.balance("p:"+D,"BASE"),100L);}});
        test("Невдала виплата Vault повертає резервовані кошти",()->{try(var f=new Fixture()){f.seed(d,100);String id=f.s.prepareVault(d,"OUT",80);eq(f.s.balance("p:"+D,"BASE"),20L);f.s.finishVault(admin,id,false);eq(f.s.balance("p:"+D,"BASE"),100L);eq(f.s.balance("v:"+id,"BASE"),0L);}});
        test("Щотижнева данина виставляється і сплачується один раз",()->{try(var f=new Fixture()){f.seed(d,1000);f.s.transfer(d,"p:"+D,"e:t:D","BASE",1000);f.link(a,"t:D",f.duchy(),250,"BASE",d);f.time.value+=7*FeudalService.DAY;f.s.tick();f.s.tick();eq(f.db.query("SELECT * FROM invoices").size(),1);eq(f.s.balance("e:t:D","BASE"),750L);eq(number(f.db.one("SELECT * FROM invoices"),"paid"),250L);}});
        test("Нестача коштів створює борг, часткова сплата підтримує копійки",()->{try(var f=new Fixture()){String duke=f.duchy();f.link(a,"t:D",duke,12345,"BASE",d);f.time.value+=7*FeudalService.DAY;f.s.tick();eq(f.s.estate("t:D").active(),true);f.seed(d,345);f.s.transfer(d,"p:"+D,"e:t:D","BASE",345);eq(f.s.payDebt(d,"t:D","BASE",345),345L);eq(number(f.db.one("SELECT * FROM invoices"),"paid"),345L);eq(f.s.balance("e:"+duke,"BASE"),345L);}});
        test("Борг обмежений двома періодами навіть після довгої перерви",()->{try(var f=new Fixture()){f.link(a,"t:D",f.duchy(),100,"BASE",d);f.time.value+=100*FeudalService.DAY;f.s.tick();f.s.tick();eq(f.db.query("SELECT * FROM invoices").size(),2);}});
        test("Зміна умов договору потребує нових підписів",()->{try(var f=new Fixture()){String duke=f.duchy();f.link(a,"t:D",duke,100,"BASE",d);String p=f.s.offer(a,"t:D",duke,500,"BASE","обмежена","повний",0);eq(number(f.s.contract("t:D"),"amount"),100L);f.s.accept(d,p);eq(number(f.s.contract("t:D"),"amount"),500L);}});
        test("Мирна незалежність погоджується обома сторонами",()->{try(var f=new Fixture()){f.link(a,"n:B","n:A",100,"BASE",b);String p=f.s.independence(b,"n:B");eq(f.s.parent("n:B"),"n:A");f.s.accept(a,p);eq(f.s.parent("n:B"),null);}});
        test("Звільнення зберігає старий борг та Towny-членство",()->{try(var f=new Fixture()){String duke=f.duchy();f.link(a,"t:D",duke,100,"BASE",d);f.time.value+=7*FeudalService.DAY;f.s.release(a,"t:D");eq(f.s.parent("t:D"),"n:A");eq(f.db.query("SELECT * FROM invoices WHERE paid<amount").size(),1);}});
        test("Строковий договір завершується після виставлення останнього рахунку",()->{try(var f=new Fixture()){String p=f.s.offer(a,"n:B","n:A",100,"BASE","висока","оборона",7);f.s.accept(b,p);f.time.value+=7*FeudalService.DAY;f.s.tick();eq(f.s.parent("n:B"),null);eq(f.db.query("SELECT * FROM invoices").size(),1);}});
        test("Грамота зберігається після виходу зі старого міста",()->{try(var f=new Fixture()){f.s.charter(a,"n:A",D);var citizens=new HashSet<>(f.citizens);citizens.remove(D+"@n:A");f.s.sync(new Snapshot(f.entities.stream().filter(x->!x.id().equals("t:D")).toList(),citizens));eq(string(f.s.charter(D),"nation"),"n:A");f.time.value+=4*FeudalService.DAY;no(()->f.s.charter(D));}});
        test("Повторне завершення грамоти заборонено",()->{try(var f=new Fixture()){String duke=f.duchy();f.s.charter(a,"n:A",D);f.s.completeFounding(d,"t:D");no(()->f.s.completeFounding(d,"t:D"));}});
        test("Збереження на диску, перезапуск та узгодження перерваного Vault",()->{Path dir=Files.createTempDirectory("empirecraft-test-");String url="jdbc:h2:"+dir.resolve("feudal")+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0";String op;try(var f=new Fixture(url)){f.seed(d,100);op=f.s.prepareVault(d,"OUT",40);}try(var f=new Fixture(url)){eq(f.s.balance("p:"+D,"BASE"),60L);eq(f.s.check().size(),1);f.s.finishVault(admin,op,false);eq(f.s.balance("p:"+D,"BASE"),100L);eq(f.s.check(),List.of());}try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}});
        test("Новий король може скасувати застарілу пропозицію попередника",()->{try(var f=new Fixture()){String p=f.s.offer(a,"n:B","n:A",0,"BASE","висока","оборона",0);var changed=new ArrayList<>(f.entities);changed.set(1,new Estate("n:B",Kind.KINGDOM,"Візантія","n:B",C,true));f.s.sync(new Snapshot(changed,f.citizens));f.s.reject(c,p);eq(f.db.one("SELECT * FROM proposals WHERE id=?",p),null);}});
        test("Ручна сплата боргу не змішує різні валюти",()->{try(var f=new Fixture()){String duke=f.duchy();f.link(a,"t:D",duke,100,"BASE",d);f.time.value+=7*FeudalService.DAY;f.s.release(a,"t:D");f.seed(a,1000);f.s.transfer(a,"p:"+A,"e:n:A","BASE",1000);f.s.createCurrency(a,"n:A","SOL","Солід","S");f.s.mint(a,"SOL",1000);f.s.transfer(a,"e:n:A","e:t:D","SOL",1000);f.link(a,"t:D",duke,200,"SOL",d);f.time.value+=7*FeudalService.DAY;f.s.tick();eq(number(f.db.one("SELECT * FROM invoices WHERE code='BASE'"),"paid"),0L);eq(number(f.db.one("SELECT * FROM invoices WHERE code='SOL'"),"paid"),200L);no(()->f.s.payDebt(d,"t:D","SOL",100));}});
        test("Суми обмежені до розбору: експоненти та надмірна точність відхиляються",()->{
            no(()->money("1e999999999"));no(()->money("1e2"));no(()->money("1.000"));no(()->money("0".repeat(10000)));no(()->money(null));
            eq(money("9000000000000.00"),900_000_000_000_000L);no(()->money("9000000000000.01"));
        });
        test("Архівна скарбниця повертає кошти лише останньому правителю",()->{try(var f=new Fixture()){
            f.seed(d,12345);f.s.transfer(d,"p:"+D,"e:t:D","BASE",12345);
            f.s.sync(new Snapshot(f.entities.stream().filter(x->!x.id().equals("t:D")).toList(),f.citizens));
            eq(f.s.controls(d,f.s.estate("t:D")),false);eq(f.s.controlsTreasury(d,f.s.estate("t:D")),true);
            no(()->f.s.transfer(e,"e:t:D","p:"+E,"BASE",1));
            f.s.transfer(d,"e:t:D","p:"+D,"BASE",12345);eq(f.s.balance("p:"+D,"BASE"),12345L);
            no(()->f.s.create(d,"t:D",Kind.COUNTY,"Чуже"));
        }});
        test("Валюту ліквідованої держави можна погасити з архівної скарбниці",()->{try(var f=new Fixture()){
            f.s.createCurrency(a,"n:A","SOL","Солід","S");f.gold(a,10);f.back(a,"SOL","10");f.s.mint(a,"SOL",1000);
            f.s.sync(new Snapshot(f.entities.stream().filter(x->!Objects.equals(x.nation(),"n:A")).toList(),Set.of(B+"@n:B",C+"@n:C")));
            no(()->f.s.mint(a,"SOL",1));f.s.redeem(a,"e:n:A","SOL",1000);
            f.s.currencies.transferGold(a,"e:n:A","p:"+A,CurrencyService.gold("10"));
            eq(f.s.currencies.goldBalance("p:"+A),CurrencyService.gold("10"));eq(f.s.check(),List.of());
        }});
        test("Борг архівного володіння можна сплатити без відновлення політичних прав",()->{try(var f=new Fixture()){
            String duke=f.duchy();f.link(a,"t:D",duke,125,"BASE",d);f.time.value+=7*FeudalService.DAY;f.s.tick();
            f.s.sync(new Snapshot(f.entities.stream().filter(x->!x.id().equals("t:D")).toList(),f.citizens));
            f.seed(d,125);no(()->f.s.transfer(e,"p:"+E,"e:t:D","BASE",1));
            f.s.transfer(d,"p:"+D,"e:t:D","BASE",125);eq(f.s.payDebt(d,"t:D","BASE",125),125L);
            eq(f.s.balance("e:"+duke,"BASE"),125L);eq(f.s.controls(d,f.s.estate("t:D")),false);
        }});
        test("Повне дерево король → герцог → граф → барон з автономією",()->{try(var f=new Fixture()){
            String duke=f.duchy();f.s.accept(e,f.s.nominate(a,duke,E));
            no(()->f.s.create(e,duke,Kind.COUNTY,"Черкаське"));
            f.link(a,duke,"n:A",0,"BASE",e);String county=f.s.create(e,duke,Kind.COUNTY,"Черкаське");
            f.link(e,"t:D",county,100,"BASE",d,a);
            eq(f.s.parent("t:D"),county);eq(f.s.parent(county),duke);eq(f.s.parent(duke),"n:A");eq(f.s.check(),List.of());
        }});
        test("Ліміти дітей і глибини відхиляють зміну атомарно",()->{try(var f=new Fixture()){
            var restricted=new FeudalService(f.db,f.time,7*FeudalService.DAY,FeudalService.DAY,2,2,2);restricted.sync(new Snapshot(f.entities,f.citizens));
            int size=f.s.estates().size();no(()->restricted.create(a,"n:A",Kind.DUCHY,"Зайве"));eq(f.s.estates().size(),size);
            no(()->restricted.offer(a,"n:A","n:B",0,"BASE","висока","оборона",0));eq(f.s.parent("n:A"),null);
        }});
        test("Перезапуск не повторює данину, а договори та підписи зберігаються",()->{
            Path dir=Files.createTempDirectory("empirecraft-tribute-");String url="jdbc:h2:"+dir.resolve("feudal")+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0";
            String proposal,duke;long at;
            try(var f=new Fixture(url)){duke=f.duchy();f.seed(d,1000);f.s.transfer(d,"p:"+D,"e:t:D","BASE",1000);f.link(a,"t:D",duke,250,"BASE",d);
                f.time.value+=7*FeudalService.DAY;f.s.tick();at=f.time.value;proposal=f.s.offer(a,"n:B","n:A",0,"BASE","висока","оборона",0);}
            try(var f=new Fixture(url)){f.time.value=at;f.s.tick();eq(f.s.balance("e:t:D","BASE"),750L);eq(f.s.balance("e:"+duke,"BASE"),250L);
                eq(f.db.query("SELECT * FROM invoices").size(),1);f.s.accept(b,proposal);eq(f.s.parent("n:B"),"n:A");}
            try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        });
        test("Онлайн-копія H2 відновлює скарбниці та валютний резерв",()->{
            Path dir=Files.createTempDirectory("empirecraft-backup-");Path backup=dir.resolve("backup.zip");
            try(var f=new Fixture("jdbc:h2:"+dir.resolve("live")+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0")){
                f.seed(a,1000);f.s.transfer(a,"p:"+A,"e:n:A","BASE",1000);f.s.createCurrency(a,"n:A","SOL","Солід","S");f.s.mint(a,"SOL",1000);f.db.backup(backup.toString());
            }
            Path restored=Files.createDirectories(dir.resolve("restored"));
            try(var zip=new java.util.zip.ZipInputStream(Files.newInputStream(backup))){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){
                Path target=restored.resolve(entry.getName()).normalize();if(!target.startsWith(restored))throw new AssertionError("Неправильний шлях у backup");
                if(entry.isDirectory())Files.createDirectories(target);else{Files.createDirectories(target.getParent());Files.copy(zip,target);}
            }}
            try(var f=new Fixture("jdbc:h2:"+restored.resolve("live")+";DATABASE_TO_LOWER=TRUE")){eq(f.s.balance("e:n:A","SOL"),1000L);eq(f.s.check(),List.of());}
            try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        });
        System.out.println("Пройдено тестів: "+count);
    }
}

