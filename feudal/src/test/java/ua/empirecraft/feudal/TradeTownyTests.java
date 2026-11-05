package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.AddonCommand;
import org.bukkit.command.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.CurrencyTests.*;
import static ua.empirecraft.feudal.CurrencyService.*;
import static ua.empirecraft.feudal.Database.*;
import static com.palmergames.bukkit.towny.TownyCommandAddonAPI.CommandType.TOWN;

/** Real H2 monetary rules and the pinned native Towny addon dispatcher. */
public final class TradeTownyTests {
    static UUID uuid(String id){return UUID.fromString(id);}
    static CommandSender sender(boolean permitted){return (CommandSender)Proxy.newProxyInstance(TradeTownyTests.class.getClassLoader(),new Class<?>[]{CommandSender.class},(p,m,args)->switch(m.getName()){
        case "hasPermission" -> permitted;case "sendMessage" -> null;case "getName" -> "Regression";default -> throw new UnsupportedOperationException(m.getName());
    });}
    static final class Handler implements CommandExecutor,TabCompleter {
        String name,label;List<String> args;int calls;
        public boolean onCommand(CommandSender s,Command c,String l,String[] a){name=c.getName();label=l;args=List.of(a);calls++;return true;}
        public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){name=c.getName();args=List.of(a);return List.of("status","progress");}
    }
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Currency 1 lists only the player's own positive balances, excluding their treasury and other wallets",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",30,1000);currency(f,b,"n:B","MRK",20,1000);
            var currencies=new TradeCurrencies(f.s);eq(currencies.owned(uuid(D)),List.of());
            f.s.transfer(a,"e:n:A","p:"+D,"KRN",125);var owned=currencies.owned(uuid(D));eq(owned.size(),1);eq(owned.get(0).code(),"KRN");eq(owned.get(0).balance(),125L);
            eq(currencies.initial(uuid(D)),"KRN");eq(currencies.personal(uuid(D),"MRK").balance(),0L);eq(currencies.personal(uuid(A),"KRN").balance(),0L);clean(f);
        }});
        test("The displayed quote buys target coins with exactly one source coin without moving money or minting",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",15000,500000);currency(f,b,"n:B","MRK",9000,600000);f.s.transfer(a,"e:n:A","p:"+D,"KRN",500);
            var currencies=new TradeCurrencies(f.s);var before=f.db.query("SELECT * FROM accounts ORDER BY owner,code");var gold=f.db.query("SELECT * FROM gold_accounts ORDER BY owner");var supplies=f.db.query("SELECT * FROM currencies ORDER BY code");
            eq(currencies.quote("KRN","MRK"),"With 1.00 KRN you can buy 2.00 MRK");eq(currencies.quote("MRK","KRN"),"With 1.00 MRK you can buy 0.50 KRN");
            eq(f.db.query("SELECT * FROM accounts ORDER BY owner,code"),before);eq(f.db.query("SELECT * FROM gold_accounts ORDER BY owner"),gold);eq(f.db.query("SELECT * FROM currencies ORDER BY code"),supplies);clean(f);
        }});
        test("Quotes refresh after backing changes and follow the real cent rounding rules",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",10,300);currency(f,b,"n:B","MRK",30,300);var currencies=new TradeCurrencies(f.s);
            eq(currencies.quote("KRN","MRK"),"With 1.00 KRN you can buy 0.33 MRK");f.gold(a,10);f.back(a,"KRN","10");eq(currencies.quote("KRN","MRK"),"With 1.00 KRN you can buy 0.66 MRK");clean(f);
        }});
        test("Unbacked, empty and inactive target currencies show unavailable instead of inventing a rate",()->{try(var f=new Fixture()){
            currency(f,a,"n:A","KRN",10,1000);f.s.createCurrency(b,"n:B","MRK","Марка","M");var currencies=new TradeCurrencies(f.s);
            eq(currencies.quote("BASE","KRN").startsWith("Rate unavailable:"),true);eq(currencies.quote("KRN","MRK").startsWith("Rate unavailable:"),true);
            f.gold(b,10);f.back(b,"MRK","10");f.s.mint(b,"MRK",1000);f.db.execute("UPDATE estates SET active=FALSE WHERE id='n:B'");eq(currencies.quote("KRN","MRK").startsWith("Rate unavailable:"),true);
        }});
        test("Empty wallets and target lists are safe and currency cycling wraps",()->{try(var f=new Fixture()){
            var currencies=new TradeCurrencies(f.s);eq(currencies.initial(uuid(D)),"");eq(currencies.targets("BASE"),List.of());eq(currencies.quote("BASE",""),"Select two town currencies for a rate.");
            eq(TradeCurrencies.next(List.of(),"BASE"),"");eq(TradeCurrencies.next(List.of("KRN","MRK"),"KRN"),"MRK");eq(TradeCurrencies.next(List.of("KRN","MRK"),"MRK"),"KRN");
        }});
        test("Native Towny strips only /t state and preserves nested arguments and completion",()->{
            var handler=new Handler();try(var routes=new TownCommands()){
                routes.register("state",handler);var command=TownyCommandAddonAPI.getAddonCommand(TOWN,"state");
                command.execute(sender(true),"town",new String[]{"state","law","set","n:A","tax","10"});eq(handler.name,"state");eq(handler.label,"t state");eq(handler.args,List.of("law","set","n:A","tax","10"));
                eq(command.getTabCompletion(sender(true),new String[]{"state","pr"}),List.of("status","progress"));eq(handler.args,List.of("pr"));
                eq(TownyCommandAddonAPI.getTabCompletes(TOWN,List.of()).contains("state"),true);
            }eq(TownyCommandAddonAPI.hasCommand(TOWN,"state"),false);
        });
        test("Ukrainian aliases use the canonical handler name and preserve permission checks",()->{
            var handler=new Handler();try(var routes=new TownCommands()){
                routes.register("vassal",handler,"васал","feudal");var alias=TownyCommandAddonAPI.getAddonCommand(TOWN,"васал");
                alias.execute(sender(true),"t",new String[]{"васал","info","n:A"});eq(handler.name,"vassal");eq(handler.args,List.of("info","n:A"));eq(handler.calls,1);
                alias.execute(sender(false),"t",new String[]{"васал","admin","backup"});eq(handler.calls,1);eq(alias.getTabCompletion(sender(false),new String[]{"васал",""}),List.of());
            }eq(TownyCommandAddonAPI.hasCommand(TOWN,"васал"),false);
        });
        test("A command collision cannot overwrite another plugin or leave our partially registered routes behind",()->{
            var foreign=new AddonCommand(TOWN,"state",new Handler());TownyCommandAddonAPI.addSubCommand(foreign);
            try{try(var routes=new TownCommands()){
                routes.register("vassal",new Handler());try{routes.register("state",new Handler());throw new AssertionError("Expected command collision");}catch(IllegalStateException expected){}
                eq(TownyCommandAddonAPI.getAddonCommand(TOWN,"state"),foreign);
            }eq(TownyCommandAddonAPI.hasCommand(TOWN,"vassal"),false);eq(TownyCommandAddonAPI.getAddonCommand(TOWN,"state"),foreign);}
            finally{TownyCommandAddonAPI.removeSubCommand(foreign);}
        });
        test("Native /t treasury and /t казна preserve financial subcommands and do not replace /t",()->{
            var handler=new Handler();try(var routes=new TownCommands()){
                routes.register("treasury",handler,"казна");var alias=TownyCommandAddonAPI.getAddonCommand(TOWN,"казна");
                alias.execute(sender(true),"t",new String[]{"казна","banker","add","Resident"});eq(handler.name,"treasury");eq(handler.args,List.of("banker","add","Resident"));
                alias.execute(sender(false),"t",new String[]{"казна","withdraw","100"});eq(handler.calls,1);
            }eq(TownyCommandAddonAPI.hasCommand(TOWN,"treasury"),false);
        });
        System.out.println("Trade currency and native Towny command regressions: "+count);
    }
}


