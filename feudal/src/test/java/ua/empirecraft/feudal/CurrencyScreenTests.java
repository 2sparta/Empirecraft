package ua.empirecraft.feudal;

import java.util.*;
import java.lang.reflect.Proxy;
import org.bukkit.entity.Player;
import static ua.empirecraft.feudal.FeudalTests.*;

/** The server confirmation contract is tested without trusting the client screen. */
public final class CurrencyScreenTests {
    static Player player(String id){return (Player)Proxy.newProxyInstance(CurrencyScreenTests.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){case "getUniqueId"->UUID.fromString(id);case "hasPermission"->a[0].equals("empirecraft.feudal.use");case "isOnline"->true;default->null;});}
    public static void main(String[] args)throws Exception{
        Class.forName("org.h2.Driver");count=0;
        test("A purchase confirmation is player-bound, parameter-bound and consumed once",()->{try(var f=new Fixture()){
            var screens=new CurrencyScreens(null,f.s,null,null);var p=player(A);var other=player(B);String token=screens.permit(p,"buy","KRN","MRK","10",2500);
            no(()->screens.consume(other,token,"buy","KRN","MRK","10"));eq(screens.consume(p,token,"buy","KRN","MRK","10").minimum(),2500L);no(()->screens.consume(p,token,"buy","KRN","MRK","10"));
        }});
        test("Changing the target, amount or operation invalidates a captured quote",()->{try(var f=new Fixture()){
            var screens=new CurrencyScreens(null,f.s,null,null);var p=player(A);String a=screens.permit(p,"buy","KRN","MRK","10",200);no(()->screens.consume(p,a,"buy","KRN","ROM","10"));
            String b=screens.permit(p,"print","@GOLD","KRN","10",0);no(()->screens.consume(p,b,"print","@GOLD","KRN","100000"));String c=screens.permit(p,"print","@GOLD","KRN","10",0);no(()->screens.consume(p,c,"cancel","@GOLD","KRN","10"));
        }});
        test("A new quote supersedes its predecessor and expired confirmations cannot mutate money",()->{try(var f=new Fixture()){
            var screens=new CurrencyScreens(null,f.s,null,null);var p=player(A);String old=screens.permit(p,"buy","KRN","MRK","10",200);String current=screens.permit(p,"buy","KRN","MRK","11",220);no(()->screens.consume(p,old,"buy","KRN","MRK","10"));f.time.value+=30001;no(()->screens.consume(p,current,"buy","KRN","MRK","11"));
        }});
        test("A captured minimum or policy configuration cannot be substituted",()->{try(var f=new Fixture()){
            var screens=new CurrencyScreens(null,f.s,null,null);var p=player(A);String token=screens.approve(p,"buy","KRN","MRK","10","9.95","","policy-v1");no(()->screens.consumeApproved(p,token,"buy","KRN","MRK","10","0",""));
            String policy=screens.approve(p,"policy-save","@GOLD","KRN","","","1;1;0;0;10;30;20;1000","");no(()->screens.consumeApproved(p,policy,"policy-save","@GOLD","KRN","","","0;0;0;0;100;100;0;1000"));
        }});
        test("A valid protected quote keeps its precise minimum and policy snapshot",()->{try(var f=new Fixture()){
            var screens=new CurrencyScreens(null,f.s,null,null);var p=player(A);String token=screens.approve(p,"redeem","KRN","@GOLD","10","0.123456789012","","policy-v2");var permit=screens.consumeApproved(p,token,"redeem","KRN","@GOLD","10","0.123456789012","");eq(permit.minOut(),"0.123456789012");eq(permit.stamp(),"policy-v2");no(()->screens.consumeApproved(p,token,"redeem","KRN","@GOLD","10","0.123456789012",""));
        }});
        test("Currency comparisons never fall back to physical gold or compare a code with itself",()->{
            var pair=CurrencyScreens.pair(List.of("KRN","SOL"),"@GOLD","KRN");eq(pair.from(),"SOL");eq(pair.to(),"KRN");eq(pair.complete(),true);
            pair=CurrencyScreens.pair(List.of("KRN","SOL"),"KRN","KRN");eq(pair.from(),"KRN");eq(pair.to(),"SOL");
            pair=CurrencyScreens.pair(List.of("GOLD","SOL"),"@GOLD","");eq(pair.from(),"GOLD");eq(pair.to(),"SOL");
        });
        test("One existing currency keeps an empty comparison instead of inventing a self-pair",()->{
            var pair=CurrencyScreens.pair(List.of("KRN"),"KRN","");eq(pair.from(),"KRN");eq(pair.to(),"");eq(pair.complete(),false);
            pair=CurrencyScreens.pair(List.of("KRN"),"","KRN");eq(pair.from(),"");eq(pair.to(),"KRN");eq(pair.complete(),false);
            pair=CurrencyScreens.pair(List.of(),"@GOLD","@GOLD");eq(pair.from(),"");eq(pair.to(),"");eq(pair.complete(),false);
        });
        test("BASE alone does not open the market; a real created currency enables it",()->{try(var f=new Fixture()){
            var market=CurrencyMarketTests.market(f);var screens=new CurrencyScreens(null,f.s,null,market);eq(screens.codes(),List.of());
            no(()->screens.open(player(A),"market","@GOLD",""));eq(screens.opens.isEmpty(),true);
            f.s.createCurrency(a,"n:A","KRN","Крона","K");screens.open(player(A),"market","","");eq(screens.codes(),List.of("KRN"));eq(screens.opens.size(),1);
        }});
        test("Print tab grants require issuer authority, capital office, sovereignty and an actual own currency",()->{try(var f=new Fixture()){
            var market=CurrencyMarketTests.market(f);var office=new HashSet<String>(List.of(A));market.printingOffice=(person,issuer)->office.contains(person);
            var screens=new CurrencyScreens(null,f.s,null,market);eq(screens.printable(player(A)),"");f.s.createCurrency(a,"n:A","KRN","Крона","K");
            eq(screens.printable(player(A)),"KRN");eq(screens.printable(player(D)),"");eq(screens.printable(player(E)),"");
            market.banker(a,"n:A",D,true);eq(market.manages(d,"n:A"),true);eq(screens.printable(player(D)),"");no(()->screens.open(player(D),"print","","KRN"));
            office.add(D);eq(screens.printable(player(D)),"KRN");office.remove(D);eq(screens.printable(player(D)),"");
            f.db.execute("INSERT INTO ties VALUES('n:A','n:B',NULL)");eq(screens.printable(player(A)),"");no(()->screens.open(player(A),"print","","KRN"));
        }});
        System.out.println("Currency screen confirmation scenarios: "+count);
    }
}
