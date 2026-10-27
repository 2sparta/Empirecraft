package ua.empirecraft.feudal;

import java.util.*;
import java.lang.reflect.Proxy;
import org.bukkit.entity.Player;
import static ua.empirecraft.feudal.FeudalTests.*;

/** The server confirmation contract is tested without trusting the client screen. */
public final class CurrencyScreenTests {
    static Player player(String id){return (Player)Proxy.newProxyInstance(CurrencyScreenTests.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->m.getName().equals("getUniqueId")?UUID.fromString(id):null);}
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
        System.out.println("Currency screen confirmation scenarios: "+count);
    }
}
