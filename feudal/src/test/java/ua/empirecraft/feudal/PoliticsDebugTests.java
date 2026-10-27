package ua.empirecraft.feudal;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import org.bukkit.command.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.PoliticalModel.*;
import static ua.empirecraft.feudal.PoliticsTests.*;
import ua.empirecraft.feudal.FeudalService.Actor;

/** Real H2 writes, permission isolation, native Towny routing and restart persistence. */
public final class PoliticsDebugTests {
    static final Actor admin=new Actor("console",true);
    static CommandSender sender(boolean permission,List<String> messages){
        return (CommandSender)Proxy.newProxyInstance(PoliticsDebugTests.class.getClassLoader(),new Class<?>[]{CommandSender.class},(proxy,method,args)->switch(method.getName()){
            case "hasPermission" -> permission || !PoliticsDebug.PERMISSION.equals(args[0]);
            case "sendMessage" -> {if(args[0] instanceof String message)messages.add(message);yield null;}
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
    static Command stateCommand(){return new Command("state"){
        @Override public boolean execute(CommandSender sender,String label,String[] args){return false;}
    };}
    public static void main(String[] args)throws Exception {
        Class.forName("org.h2.Driver");count=0;
        test("Every debug mutation rejects a ruler without debug authority",()->{try(var f=new F()){
            var debug=new PoliticsDebug(f.p);var before=f.p.state("n:A");long audit=f.db.query("SELECT * FROM audit").size();
            no(()->debug.next(a,"n:A",Stage.EMPIRE));no(()->debug.stage(a,"n:A",Stage.BARONY));
            no(()->debug.government(a,"n:A",Government.REPUBLIC));no(()->debug.territory(a,"n:A",Territory.DIRECT));no(()->debug.ready(a,"n:A",Stage.EMPIRE));
            eq(f.p.state("n:A"),before);eq((long)f.db.query("SELECT * FROM audit").size(),audit);
        }});
        test("Next bypasses unmet requirements and the hold period but retains the configured branch",()->{try(var f=new F()){
            f.stage("n:A",Stage.BARONY);var debug=new PoliticsDebug(f.p);ok(!f.p.progress("n:A",Stage.COUNTY).met());
            eq(debug.next(admin,"n:A",null),Stage.COUNTY);eq(f.p.state("n:A").stage(),Stage.COUNTY);
            var h=f.db.one("SELECT * FROM political_history WHERE state='n:A' AND event='DEBUG_NEXT'");eq(string(h,"actor"),"console");ok(string(h,"detail").contains("BARONY → COUNTY"));
            no(()->f.p.submit(a,"n:A",Stage.KINGDOM));
        }});
        test("Ambiguous, invalid and terminal next targets leave state and history unchanged",()->{try(var f=new F()){
            var debug=new PoliticsDebug(f.p);f.stage("n:A",Stage.SETTLEMENT);var before=f.p.state("n:A");var history=f.p.history("n:A");
            no(()->debug.next(admin,"n:A",null));no(()->debug.next(admin,"n:A",Stage.EMPIRE));eq(f.p.state("n:A"),before);eq(f.p.history("n:A"),history);
            debug.stage(admin,"n:A",Stage.EMPIRE);no(()->debug.next(admin,"n:A",null));eq(f.p.state("n:A").stage(),Stage.EMPIRE);
        }});
        test("All development branches use normal promotion effects",()->{try(var f=new F()){
            var debug=new PoliticsDebug(f.p);
            for(Stage target:List.of(Stage.TRIBE,Stage.FREE_CITY,Stage.CENTRAL_STATE,Stage.BARONY)){
                debug.stage(admin,"n:A",Stage.SETTLEMENT);debug.next(admin,"n:A",target);eq(f.p.state("n:A").stage(),target);
                if(target==Stage.TRIBE)eq(f.p.state("n:A").government(),Government.TRIBAL_COUNCIL);
                if(target==Stage.FREE_CITY){eq(f.p.state("n:A").government(),Government.REPUBLIC);ok(f.p.state("n:A").termEnd()>f.time.now);}
                if(target==Stage.CENTRAL_STATE)eq(f.p.state("n:A").territory(),Territory.DIRECT);
            }
        }});
        test("Setstage supports jumps, demotions and branch changes without mutating money or land",()->{try(var f=new F()){
            var debug=new PoliticsDebug(f.p);f.seed(a,1234);
            var accounts=f.db.query("SELECT * FROM accounts ORDER BY owner,code");var gold=f.db.query("SELECT * FROM gold_accounts ORDER BY owner");
            var estates=f.db.query("SELECT * FROM estates ORDER BY id");var links=f.db.query("SELECT * FROM ties ORDER BY child");var contracts=f.db.query("SELECT * FROM contracts ORDER BY id");
            debug.stage(admin,"n:A",Stage.EMPIRE);debug.stage(admin,"n:A",Stage.SETTLEMENT);debug.stage(admin,"n:A",Stage.KHAGANATE);
            eq(f.p.state("n:A").stage(),Stage.KHAGANATE);eq(f.db.query("SELECT * FROM accounts ORDER BY owner,code"),accounts);eq(f.db.query("SELECT * FROM gold_accounts ORDER BY owner"),gold);
            eq(f.db.query("SELECT * FROM estates ORDER BY id"),estates);eq(f.db.query("SELECT * FROM ties ORDER BY child"),links);eq(f.db.query("SELECT * FROM contracts ORDER BY id"),contracts);eq(f.s.check(),List.of());
        }});
        test("Government and territory are independent axes and republic terms are initialized",()->{try(var f=new F()){
            var debug=new PoliticsDebug(f.p);Stage stage=f.p.state("n:A").stage();debug.government(admin,"n:A",Government.REPUBLIC);
            ok(f.p.state("n:A").termEnd()>f.time.now);eq(f.p.state("n:A").stage(),stage);debug.territory(admin,"n:A",Territory.FEDERAL);
            eq(f.p.state("n:A").government(),Government.REPUBLIC);debug.government(admin,"n:A",Government.SULTANATE);eq(f.p.state("n:A").termEnd(),0L);eq(f.p.state("n:A").territory(),Territory.FEDERAL);
        }});
        test("A debug change clears obsolete readiness, submissions, open decisions and elections",()->{try(var f=new F()){
            var debug=new PoliticsDebug(f.p);f.all("n:A",a);f.government(a,"n:A",Government.REPUBLIC);
            String vote=f.p.decision(a,"n:A","CHARTER","Нова конституція для тесту","",0),election=f.p.election(a,"n:A");
            f.db.execute("INSERT INTO political_ready VALUES('n:A','EMPIRE',1,'old')");f.db.execute("INSERT INTO political_advances VALUES('n:A','EMPIRE',?,'old',?)",a.id(),f.time.now+1);
            long revision=f.p.state("n:A").revision();debug.territory(admin,"n:A",Territory.DIRECT);
            eq(f.p.state("n:A").revision(),revision+1);eq(f.db.one("SELECT * FROM political_ready WHERE state='n:A'"),null);eq(f.db.one("SELECT * FROM political_advances WHERE state='n:A'"),null);
            eq(string(f.db.one("SELECT * FROM political_decisions WHERE id=?",vote),"status"),"STALE");eq(string(f.db.one("SELECT * FROM political_elections WHERE id=?",election),"status"),"STALE");
            no(()->f.p.vote(a,vote,true));no(()->f.p.candidate(a,election));
        }});
        test("Prepared native authority transfers cannot be bypassed by any debug write",()->{try(var f=new F()){
            var debug=new PoliticsDebug(f.p);f.all("n:A",a);f.government(a,"n:A",Government.REPUBLIC);String election=f.p.election(a,"n:A");
            f.db.execute("UPDATE political_elections SET status='PREPARED',winner=? WHERE id=?",b.id(),election);var before=f.p.state("n:A");
            no(()->debug.next(admin,"n:A",Stage.EMPIRE));no(()->debug.stage(admin,"n:A",Stage.BARONY));no(()->debug.government(admin,"n:A",Government.MONARCHY));
            no(()->debug.territory(admin,"n:A",Territory.DIRECT));no(()->debug.ready(admin,"n:A",Stage.EMPIRE));eq(f.p.state("n:A"),before);eq(string(f.db.one("SELECT * FROM political_elections WHERE id=?",election),"status"),"PREPARED");
        }});
        test("Archived and merged estates reject debug writes",()->{try(var f=new F()){
            var debug=new PoliticsDebug(f.p);f.db.execute("UPDATE estates SET active=FALSE WHERE id='n:A'");no(()->debug.stage(admin,"n:A",Stage.BARONY));
            f.db.execute("UPDATE political_states SET merged_into='n:C' WHERE estate='n:B'");no(()->debug.stage(admin,"n:B",Stage.BARONY));
        }});
        test("Ready skips exactly the stability period and still needs the ruler's normal promotion",()->{try(var f=new F("jdbc:h2:mem:"+UUID.randomUUID()+";DATABASE_TO_LOWER=TRUE",7)){
            var debug=new PoliticsDebug(f.p);f.all("n:A",a);debug.stage(admin,"n:A",Stage.SETTLEMENT);Stage target=Stage.TRIBE;
            no(()->f.p.submit(a,"n:A",target));debug.ready(admin,"n:A",target);eq(f.p.state("n:A").stage(),Stage.SETTLEMENT);ok(f.p.progress("n:A",target).ready(f.time.now));
            no(()->f.p.submit(admin,"n:A",target));f.p.submit(a,"n:A",target);f.p.confirm(a,"n:A");eq(f.p.state("n:A").stage(),target);
        }});
        test("Ready refuses unmet requirements atomically",()->{try(var f=new F()){
            var debug=new PoliticsDebug(f.p);var before=f.p.state("n:A");var ready=f.db.query("SELECT * FROM political_ready");var history=f.p.history("n:A");
            no(()->debug.ready(admin,"n:A",Stage.EMPIRE));eq(f.p.state("n:A"),before);eq(f.db.query("SELECT * FROM political_ready"),ready);eq(f.p.history("n:A"),history);
        }});
        test("Debug states and their audit trail survive restart",()->{
            Path dir=Files.createTempDirectory("empirecraft-debug-");String url="jdbc:h2:"+dir.resolve("feudal")+";DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0";
            try{try(var f=new F(url,0)){new PoliticsDebug(f.p).stage(admin,"n:A",Stage.KHAGANATE);}
                try(var f=new F(url,0)){eq(f.p.state("n:A").stage(),Stage.KHAGANATE);ok(f.p.history("n:A").stream().anyMatch(row->"DEBUG_STAGE".equals(string(row,"event"))));}
            }finally{try(var paths=Files.walk(dir)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
        });
        test("Admin permission alone cannot execute or discover debug commands",()->{try(var f=new F()){
            var commands=new PoliticalCommands(null,f.s,null,f.p);var messages=new ArrayList<String>();var denied=sender(false,messages);Command command=stateCommand();var before=f.p.state("n:A");
            commands.onCommand(denied,command,"t state",new String[]{"debug","setstage","n:A","empire"});eq(f.p.state("n:A"),before);ok(messages.get(0).contains(PoliticsDebug.PERMISSION));
            ok(!commands.onTabComplete(denied,command,"t state",new String[]{""}).contains("debug"));eq(commands.onTabComplete(denied,command,"t state",new String[]{"debug",""}),List.of());
        }});
        test("Completion suggests valid branch targets and each independent axis",()->{try(var f=new F()){
            var commands=new PoliticalCommands(null,f.s,null,f.p);var permitted=sender(true,new ArrayList<>());Command command=stateCommand();f.stage("n:A",Stage.BARONY);
            ok(commands.onTabComplete(permitted,command,"t state",new String[]{"d"}).contains("debug"));
            eq(commands.onTabComplete(permitted,command,"t state",new String[]{"debug","next","n:A",""}),List.of("county"));
            ok(commands.onTabComplete(permitted,command,"t state",new String[]{"debug","government","n:A","r"}).contains("republic"));
            eq(commands.onTabComplete(permitted,command,"t state",new String[]{"debug","territory","n:A","dir"}),List.of("direct"));
            ok(commands.debug.onTabComplete(permitted,command,"statedebug",new String[]{"setstage","n:A","em"}).contains("empire"));
        }});
        test("Native Ukrainian Towny alias routes nested debug help and permission failures",()->{try(var f=new F()){
            var commands=new PoliticalCommands(null,f.s,null,f.p);var messages=new ArrayList<String>();
            try(var routes=new TownCommands()){
                routes.register("state",commands,"держава");var alias=com.palmergames.bukkit.towny.TownyCommandAddonAPI.getAddonCommand(com.palmergames.bukkit.towny.TownyCommandAddonAPI.CommandType.TOWN,"держава");
                alias.execute(sender(true,messages),"t",new String[]{"держава","debug","help"});ok(messages.stream().anyMatch(message->message.contains("statedebug")));
                messages.clear();alias.execute(sender(false,messages),"t",new String[]{"держава","debug","next","n:A","empire"});ok(messages.get(0).contains(PoliticsDebug.PERMISSION));
            }
        }});
        System.out.println("Debug state scenarios passed: "+count);
    }
}
