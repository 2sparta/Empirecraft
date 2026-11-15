package ua.townyrecruits.test;
import ua.townyrecruits.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

public final class RecruitmentStateTest {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("townyrecruits-ledger-");UUID town=UUID.randomUUID(),player=UUID.randomUUID(),nation=UUID.randomUUID();
        var config=new RecruitmentConfig(dir.resolve("config.properties"));check(config.total==15&&config.perPlayer==15,"15/15 defaults");
        check(CommandWords.parse("dev add Львів 0.5").equals(List.of("dev","add","Львів","0.5")),"Ukrainian town name does not stall command parsing");
        check(CommandWords.parse("limit \"Київська Русь\" 25 15").equals(List.of("limit","Київська Русь","25","15")),"quoted nation names preserve spaces");
        check(CommandWords.parse("dev info Київ-центр").equals(List.of("dev","info","Київ-центр")),"Unicode and punctuation in Towny names");
        boolean quoteRejected=false;try{CommandWords.parse("limit \"unclosed");}catch(IllegalArgumentException ex){quoteRejected=true;}check(quoteRejected,"unclosed quotes fail without looping");
        for(String bad:List.of("NaN","Infinity","-0.5","1e9","0.0000001","1,5")){boolean rejected=false;try{RecruitmentConfig.decimal(bad);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"reject invalid decimal "+bad);}
        var state=new RecruitmentState(dir.resolve("state.json"));state.development(town,"set",BigDecimal.TEN);
        for(int i=0;i<10;i++)state.development(town,"remove",new BigDecimal("0.5"));check(state.balance(town).compareTo(new BigDecimal("5"))==0,"exact repeated decimal deductions");
        try{state.development(town,"remove",new BigDecimal("5.5"));throw new AssertionError("overdraft accepted");}catch(IllegalArgumentException expected){}check(state.balance(town).compareTo(new BigDecimal("5"))==0,"overdraft rollback");
        state.change(()->state.nations.put(nation,new RecruitmentState.Nation("manual-existing",77,12)));
        List<Future<Boolean>> jobs=new ArrayList<>();var threads=Executors.newFixedThreadPool(8);
        for(int i=0;i<100;i++)jobs.add(threads.submit(()->{var p=new RecruitmentState.Purchase();p.unit=UUID.randomUUID();p.town=town;p.player=player;p.nation=nation;p.phase="PREPARED";p.development=new BigDecimal("0.5");try{state.reserve(p);return true;}catch(IllegalStateException expected){return false;}}));
        int winners=0;for(var f:jobs)if(f.get())winners++;threads.shutdown();check(winners==1,"100 concurrent attempts make one reservation");
        var reopened=new RecruitmentState(dir.resolve("state.json"));check(reopened.balance(town).compareTo(new BigDecimal("4.5"))==0,"balance survives reopen");check(reopened.nations.get(nation).faction.equals("manual-existing")&&reopened.nations.get(nation).perPlayer==12,"legacy ID and caps survive reopen");
        UUID op=reopened.purchases.keySet().iterator().next();reopened.refund(op);reopened.refund(op);check(reopened.balance(town).compareTo(new BigDecimal("5"))==0,"refund is idempotent");
        Path invalid=dir.resolve("directory-in-place-of-file");Files.createDirectory(invalid);var failed=new RecruitmentState(dir.resolve("will-fail/state.json"));failed.development(town,"set",BigDecimal.TEN);Files.move(dir.resolve("will-fail/state.json"),dir.resolve("saved.json"));Files.createDirectory(dir.resolve("will-fail/state.json"));
        boolean ioFailed=false;try{failed.development(town,"remove",BigDecimal.ONE);}catch(RuntimeException ex){ioFailed=true;}check(ioFailed&&failed.balance(town).compareTo(BigDecimal.TEN)==0,"disk error rolls memory back without consuming development");
        check(Files.isRegularFile(dir.resolve("state.json.bak")),"previous durable state retained as backup");
        UUID dismissed=UUID.randomUUID();
        reopened.change(()->reopened.dismissed.add(dismissed));
        var afterDismissal=new RecruitmentState(dir.resolve("state.json"));
        check(afterDismissal.dismissed.contains(dismissed),"dismissal tombstone survives restart");
        check(afterDismissal.balance(town).compareTo(new BigDecimal("5"))==0,"dismissal does not refund development");
        var legacyObject=com.google.gson.JsonParser.parseString(Files.readString(dir.resolve("state.json"))).getAsJsonObject();legacyObject.remove("dismissed");String legacy=legacyObject.toString();
        Path legacyFile=dir.resolve("legacy.json");Files.writeString(legacyFile,legacy);
        var migrated=new RecruitmentState(legacyFile);
        check(migrated.dismissed.isEmpty(),"2.0.0 document without dismissal field remains readable");
        check(migrated.nations.get(nation).total==77,"migration preserves existing limits");
        UUID failedDismissal=UUID.randomUUID();
        try{failed.change(()->failed.dismissed.add(failedDismissal));throw new AssertionError("disk error ignored");}catch(RuntimeException expected){}
        check(!failed.dismissed.contains(failedDismissal),"failed dismissal persistence rolls tombstone back");
        System.out.println("RECRUITMENT_LEDGER_PASS checks="+checks);
    }
}
