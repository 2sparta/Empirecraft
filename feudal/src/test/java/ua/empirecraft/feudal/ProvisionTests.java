package ua.empirecraft.feudal;

import java.math.*;
import java.nio.file.*;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.ProvisionClock.*;

/** Real H2, exact seasonal integration, restart boundaries and the existing durable inventory saga. */
public final class ProvisionTests {
    static final long START=1_800_000_000_000L;
    static ProvisionClock spring(){return new ProvisionClock(START,0,false,START);}
    static String town="t:11111111-1111-1111-1111-111111111111";
    static String food(ProvisionService p,FeudalService.Actor actor,int slot,long version,int n,int nutrition){return p.transfer(actor,town,slot,version,"modded-food-full-NBT",n,nutrition,64,new CommerceService.InventoryChange(actor.id(),"inventory-before","inventory-after"));}
    static BigInteger units(long n){return UNIT.multiply(BigInteger.valueOf(n));}
    public static void main(String[] args)throws Exception {
        count=0;Class.forName("org.h2.Driver");
        test("Кожна пора року: 8, 4, 6.4, 12 голоду на чанк за реальну добу",()->{
            var c=spring();eq(c.cost(START,START+DAY,1),units(8));eq(c.cost(START+3*DAY,START+4*DAY,1),units(4));eq(c.cost(START+6*DAY,START+7*DAY,1),UNIT.multiply(BigInteger.valueOf(64)).divide(BigInteger.TEN));eq(c.cost(START+9*DAY,START+10*DAY,1),units(12));
        });
        test("Межа сезону й роки інтегруються точно, без переповнення та TPS",()->{
            var c=spring();eq(c.cost(START+5*DAY/2,START+7*DAY/2,2),units(12));eq(c.cost(START,START+YEAR,1),UNIT.multiply(BigInteger.valueOf(912)).divide(BigInteger.TEN));
            long to=START+20*365*DAY;eq(c.cost(START,to,1000000),c.cost(START,START+2*DAY,1000000).add(c.cost(START+2*DAY,to,1000000)));eq(c.cost(START+100,START,1),BigInteger.ZERO);
        });
        test("Облік фактичних 16×16 чанків: Towny 8/16/32, від'ємні координати та світи",()->{
            eq(chunks(List.of(new Plot("world",-1,-1)),32),4L);eq(chunks(List.of(new Plot("world",0,0),new Plot("world",1,0)),8),1L);eq(chunks(List.of(new Plot("world",-1,0),new Plot("world",0,0)),8),2L);eq(chunks(List.of(new Plot("a",0,0),new Plot("b",0,0)),16),2L);
        });
        test("Відсутній, несумісний або застарілий календар не дає списувати їжу",()->{
            try{parse(null,START);throw new AssertionError();}catch(IllegalStateException expected){}try{parse("2|0|0|false|0",START);throw new AssertionError();}catch(IllegalStateException expected){}try{parse("1|"+START+"|0|false|"+START,START+DAY);throw new AssertionError();}catch(IllegalStateException expected){}
            eq(parse("1|"+START+"|0|false|"+START,START).season(START),0);var paused=new ProvisionClock(START,3*DAY,true,START);eq(paused.cost(START,START+4*DAY,1),units(16));
        });
        test("Перша інсталяція не нараховує старий борг; голод починається після старту",()->{try(var f=new Fixture()){
            var p=new ProvisionService(f.s,new CommerceService(f.s));p.settle(town,10,START,spring());eq(number(p.town(town),"hungry_since"),0L);p.settle(town,10,START+DAY,spring());eq(number(p.town(town),"hungry_since"),START);eq(number(p.town(town),"hungry_ms"),DAY);eq(ProvisionService.energy(p.town(town),"unmet"),units(80));
            p.settle(town,10,START+DAY,spring());eq(number(p.town(town),"hungry_ms"),DAY);
        }});
        test("Повна поживність модової їжі, дробова решта і момент вичерпання запасу",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);p.settle(town,1,START,spring());c.finish(food(p,a,0,0,2,5),true);
            p.settle(town,1,START+DAY,spring());eq(p.reserve(town),units(2));eq(p.stock(town).size(),0);eq(number(p.town(town),"hungry_since"),0L);
            p.settle(town,1,START+2*DAY,spring());eq(number(p.town(town),"hungry_since"),START+DAY+DAY/4);eq(number(p.town(town),"hungry_ms"),DAY*3/4);eq(ProvisionService.energy(p.town(town),"unmet"),units(6));
        }});
        test("Годування скидає поточний повний голод, історія для майбутніх селян зберігається",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);p.settle(town,1,START,spring());p.settle(town,1,START+DAY,spring());c.finish(food(p,a,0,0,64,5),true);
            eq(number(p.town(town),"hungry_since"),0L);eq(number(p.town(town),"hungry_ms"),DAY);eq(number(p.town(town),"worst_ms"),DAY);p.settle(town,1,START+2*DAY,spring());eq(number(p.town(town),"hungry_since"),0L);
        }});
        test("Незавершена передача заморожує склад, списання й розширення, без втрати простою",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);p.settle(town,1,START,spring());String op=food(p,a,0,0,20,5);eq(p.reserve(town),BigInteger.ZERO);eq(p.settle(town,1,START+DAY,spring()),false);no(()->p.canSupport(town,1,START+DAY,spring()));no(()->food(p,b,1,0,1,5));c.finish(op,true);p.settle(town,1,START+DAY,spring());eq(p.reserve(town),units(92));no(()->c.finish(op,true));
        }});
        test("Відхилення наміру не створює їжу; старі версії/невалідні слоти не проходять",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);p.settle(town,1,START,spring());String op=food(p,a,0,0,20,5);c.finish(op,false);eq(p.reserve(town),BigInteger.ZERO);no(()->food(p,a,45,0,1,5));no(()->food(p,a,0,0,65,5));c.finish(food(p,a,0,0,1,5),true);no(()->food(p,a,0,0,1,5));
        }});
        test("Зміна землі спочатку оплачує стару територію; час назад не дає подвійного списання",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);p.settle(town,2,START,spring());c.finish(food(p,a,0,0,64,5),true);p.settle(town,1,START+DAY,spring());eq(p.reserve(town),units(304));p.settle(town,1,START+DAY/2,spring());eq(p.reserve(town),units(304));p.settle(town,1,START+2*DAY,spring());eq(p.reserve(town),units(296));
        }});
        test("Нові чанки потребують запасу на наступні 24 години з переходом сезону",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);long now=START+5*DAY/2;p.settle(town,1,now,spring());c.finish(food(p,a,0,0,3,4),true);eq(p.canSupport(town,2,now,spring()),true);eq(p.canSupport(town,3,now,spring()),false);
        }});
        test("Адміністративна зміна сезону не переписує минулі витрати",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);p.settle(town,1,START,spring());c.finish(food(p,a,0,0,64,5),true);
            var winter=new ProvisionClock(START+DAY,9*DAY,false,START+DAY);p.settle(town,1,START+2*DAY,winter);eq(p.reserve(town),units(300));
        }});
        test("Видача не дублюється, відміна повертає початкову комірку",()->{try(var f=new Fixture()){
            var c=new CommerceService(f.s);var p=new ProvisionService(f.s,c);p.settle(town,1,START,spring());c.finish(food(p,a,0,0,10,5),true);String op=food(p,a,0,1,-4,5);eq(p.reserve(town),units(50));c.finish(op,true);eq(p.reserve(town),units(30));no(()->c.finish(op,true));String aborted=food(p,a,0,2,-6,5);c.finish(aborted,false);eq(p.reserve(town),units(30));
        }});
        test("Журнал території не вибирає нові ділянки повторно після перезапуску",()->{try(var f=new Fixture()){
            var p=new ProvisionService(f.s,new CommerceService(f.s));var rows=p.plan(town,START,0,List.of("world/1/2","world/1/3"),START+2*DAY);p.action(string(rows.get(0),"id"),"DONE","removed");var replay=p.plan(town,START,0,List.of("world/9/9"),START+3*DAY);eq(replay.size(),2);eq(replay.stream().filter(r->string(r,"status").equals("DONE")).count(),1L);eq(p.plan(town,START,-1,List.of("COLLAPSE"),START+4*DAY).size(),1);
        }});
        test("H2 на диску: 4 доби простою, голод і незавершений внесок переживають JVM",()->{
            Path dir=Files.createTempDirectory("provision-restart");String url="jdbc:h2:"+dir.resolve("data");String op;
            try(var db=new Database(url)){var s=new FeudalService(db,java.time.Clock.systemUTC(),7*DAY,DAY,32,12,2);var c=new CommerceService(s);var p=new ProvisionService(s,c);p.settle(town,1,START,spring());op=food(p,a,0,0,2,4);}
            try(var db=new Database(url)){var s=new FeudalService(db,java.time.Clock.systemUTC(),7*DAY,DAY,32,12,2);var c=new CommerceService(s);var p=new ProvisionService(s,c);eq(p.settle(town,1,START+5*DAY,spring()),false);c.finish(op,true);p.settle(town,1,START+5*DAY,spring());eq(number(p.town(town),"hungry_since"),START+DAY);eq(number(p.town(town),"hungry_ms"),4*DAY);eq(p.reserve(town),BigInteger.ZERO);}
        });
        System.out.println("Перевірок провізії: "+count);
    }
}
