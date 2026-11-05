package ua.empirecraft.feudal;

import java.nio.file.*;
import java.util.*;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.FeudalTests.*;

/** Contract lifecycle, real SQL, deterministic time; no invented Bukkit server. */
public final class ContractTests {
    static int checks;
    static void check(boolean value){if(!value)throw new AssertionError("Contract assertion "+checks);checks++;}
    @FunctionalInterface interface Attempt{void run()throws Exception;}
    static void fails(Attempt action)throws Exception{try{action.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError("Expected rejection");}
    static class F extends Fixture {
        final ContractService cs;final long id;
        F()throws Exception{super();s.createCurrency(a,"n:A","SLV","Срібло","S");cs=new ContractService(s,null);s.contractBuilder=cs;id=cs.create(b,"n:B","n:A");}
        int rev()throws Exception{return cs.rev(cs.get(id));}
        void set(String key,String value)throws Exception{cs.edit(a,id,rev(),key,value);}
        void sign()throws Exception{int r=rev();cs.agree(a,id,r);cs.agree(b,id,r);check(!cs.sign(a,id,r));check(cs.sign(b,id,r));}
        void advance(long days){time.value+=days*FeudalService.DAY;cs.tick();}
    }
    public static void main(String[] args)throws Exception{
        try(var languageScope=LocalText.use("uk")){
        try(var f=new F()){
            int r=f.rev();f.cs.agree(a,f.id,r);f.cs.agree(b,f.id,r);check(!f.cs.sign(a,f.id,r));
            int next=f.cs.edit(b,f.id,r,"extra","Утримувати прикордонну фортецю");check(next>r);fails(()->f.cs.sign(b,f.id,r));fails(()->f.cs.sign(b,f.id,next));
            f.cs.agree(b,f.id,next);fails(()->f.cs.sign(b,f.id,next));f.cs.agree(a,f.id,next);check(!f.cs.sign(b,f.id,next));check(f.cs.sign(a,f.id,next));
            check(number(f.cs.get(f.id),"active_rev")==next);fails(()->f.cs.sign(a,f.id,next));fails(()->f.cs.agree(c,f.id,next));
            fails(()->f.s.offer(a,"n:B","n:A",0,"","висока","оборона",0));fails(()->f.s.forceParent(admin,"n:B","n:C"));fails(()->f.s.release(a,"n:B"));
            int edit=f.cs.amend(a,f.id,next);f.cs.reject(b,f.id,edit);check(f.rev()==next);check(f.cs.amend(b,f.id,next)>edit);
        }
        try(var f=new F()){
            f.set("amount","100");f.set("code","SLV");f.set("resources","WHEAT:128,IRON_INGOT:64");f.set("subsidy","5");f.set("subcode","SLV");f.set("provisions","BREAD:64");f.set("aid","2");
            f.s.setBalance("e:n:A","SLV",100000);f.s.setBalance("e:n:B","SLV",100000);f.sign();check(f.s.balance("e:n:B","SLV")==100200);check(f.s.balance("e:n:A","SLV")==99800);
            f.advance(7);check(f.s.balance("e:n:B","SLV")==90700);check(f.s.balance("e:n:A","SLV")==109300);check(f.cs.debts(f.id).size()==3);int obligations=f.db.query("SELECT * FROM vc_obligation").size();f.cs.tick();check(f.db.query("SELECT * FROM vc_obligation").size()==obligations);
            // A new ruler inherits obligations, not another player's signatures.
            var changed=f.entities.stream().map(e->e.id().equals("n:B")?new Estate(e.id(),e.kind(),e.name(),e.nation(),C,true):e).toList();f.s.sync(new Snapshot(changed,f.citizens));check("ACTIVE".equals(string(f.cs.get(f.id),"status")));check(f.s.parent("n:B").equals("n:A"));
            int newRev=f.cs.amend(c,f.id,f.rev());fails(()->f.cs.agree(b,f.id,newRev));f.cs.agree(c,f.id,newRev);check(number(f.cs.get(f.id),"active_rev")<newRev);
        }
        try(var f=new F()){
            f.set("code","SLV");f.set("amount","100");f.set("resources","WHEAT:128,IRON_INGOT:64");f.set("fine","10");f.set("grace","1");f.sign();f.advance(9);
            check(f.db.query("SELECT * FROM vc_obligation WHERE penalty=TRUE").size()==1);f.cs.tick();check(f.db.query("SELECT * FROM vc_obligation WHERE penalty=TRUE").size()==1);
            f.s.setBalance("e:n:B","SLV",11000);f.cs.tick();check(f.s.balance("e:n:A","SLV")==11000);check(f.cs.debts(f.id).size()==2);
            long oid=number(f.cs.debts(f.id).get(0),"id");String material=string(f.cs.debts(f.id).get(0),"asset").substring(5);var commerce=new CommerceService(f.s);
            String op=f.cs.supply(b,oid,12,commerce,new CommerceService.InventoryChange(B,"before","after"));
            fails(()->f.cs.supply(b,oid,12,commerce,new CommerceService.InventoryChange(B,"before","after")));
            commerce.finish(op,true);check(number(f.db.one("SELECT quantity FROM vc_goods WHERE estate='n:A' AND material=?",material),"quantity")==12);fails(()->commerce.finish(op,true));
            String out=f.cs.collect(a,"n:A",material,5,commerce,new CommerceService.InventoryChange(A,"before","after"));commerce.finish(out,false);check(number(f.db.one("SELECT quantity FROM vc_goods WHERE estate='n:A' AND material=?",material),"quantity")==12);
        }
        try(var f=new F()){
            f.set("days","14");f.set("amount","10");f.set("code","SLV");f.sign();f.advance(14);check("EXPIRED".equals(string(f.cs.get(f.id),"status")));check(f.cs.debts(f.id).size()==2);check(f.s.parent("n:B")==null);f.cs.tick();check(f.cs.debts(f.id).size()==2);
        }
        try(var f=new F()){
            f.set("code","SLV");f.set("unilateral","true");f.set("notice","2");f.set("buyout","25");f.sign();fails(()->f.cs.exit(b,f.id,true));check(number(f.cs.get(f.id),"exit_at")==0);
            f.s.setBalance("e:n:B","SLV",2500);f.cs.exit(b,f.id,true);check(f.s.balance("e:n:A","SLV")==2500);f.advance(1);check(f.s.parent("n:B")!=null);f.advance(1);check("ENDED".equals(string(f.cs.get(f.id),"status")));
        }
        try(var f=new F()){
            f.set("vassals","DENY");f.set("alliance","CONSENT");f.sign();fails(()->f.cs.autonomy("n:B","vassals"));fails(()->f.cs.autonomy("n:B","alliance"));f.cs.permit(a,f.id,"alliance");f.cs.autonomy("n:B","alliance");fails(()->f.cs.autonomy("n:B","alliance"));
            f.cs.exit(a,f.id,false);check("ACTIVE".equals(string(f.cs.get(f.id),"status")));f.cs.exit(b,f.id,false);check("ENDED".equals(string(f.cs.get(f.id),"status")));
        }
        try(var f=new F()){
            f.set("aid","10");f.set("subcode","SLV");int r=f.rev();f.cs.agree(a,f.id,r);f.cs.agree(b,f.id,r);f.cs.sign(a,f.id,r);fails(()->f.cs.sign(b,f.id,r));check(number(f.cs.get(f.id),"active_rev")==0);check(f.s.parent("n:B")==null);
            f.s.setBalance("e:n:A","SLV",1000);check(f.cs.sign(b,f.id,r));
        }
        try(var f=new F()){
            f.set("period","1");f.set("first","0");f.set("code","SLV");f.set("amount","1");f.sign();f.advance(150);for(int i=0;i<4;i++)f.cs.tick();check(f.cs.debts(f.id).size()==151); // bounded catch-up never waives debt
        }
        try(var f=new F()){
            f.set("code","SLV");f.set("amount","1");f.set("grace","0");f.set("misses","0");f.set("breach","TERMINATE");f.sign();f.advance(8);check("BREACHED".equals(string(f.cs.get(f.id),"status")));check(f.cs.debts(f.id).size()==1);
        }
        try(var f=new PoliticsTests.F()){
            var cs=new ContractService(f.s,f.p);f.s.contractBuilder=cs;f.db.execute("UPDATE political_states SET government='REPUBLIC' WHERE estate='n:B'");
            long id=cs.create(PoliticsTests.b,"n:B","n:A");cs.agree(PoliticsTests.a,id,1);cs.agree(PoliticsTests.b,id,1);fails(()->cs.sign(PoliticsTests.a,id,1));
            var voters=f.p.electorate("n:B");int n=0;for(String voter:voters){if(n++<=voters.size()/2)cs.vote(new FeudalService.Actor(voter,false),id,1,"n:B",true);}
            check(cs.mandate(id,1,"n:B"));cs.sign(PoliticsTests.b,id,1);check(cs.sign(PoliticsTests.a,id,1));
            int next=cs.amend(PoliticsTests.a,id,1);check(!cs.mandate(id,next,"n:B"));
        }
        try(var f=new F()){
            f.set("amount","10");f.set("code","SLV");f.sign();int old=f.rev();f.cs.amend(a,f.id,old);f.set("amount","20");f.advance(7);check(f.cs.debts(f.id).size()==1);check(number(f.cs.debts(f.id).get(0),"amount")==1000);
            f.sign();check(number(f.cs.get(f.id),"active_rev")==f.rev());f.advance(7);check(f.cs.debts(f.id).size()==2);check(number(f.cs.debts(f.id).get(1),"amount")==2000);
        }
        try(var f=new F()){
            f.set("aid","2");f.set("subcode","SLV");f.s.setBalance("e:n:A","SLV",1000);f.sign();check(f.s.balance("e:n:B","SLV")==200);f.cs.amend(a,f.id,f.rev());f.sign();check(f.s.balance("e:n:B","SLV")==200);
        }
        try(var f=new F()){
            f.set("first_date","2000-01-01");int r=f.rev();fails(()->f.cs.agree(a,f.id,r));
            var date=java.time.Instant.ofEpochMilli(f.s.now()).atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(10);f.set("first_date",date.toString());f.sign();check(number(f.cs.get(f.id),"next_due")==date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli());
        }
        try(var f=new F()){
            // Old offers created during a draft cannot silently supersede it at activation.
            f.s.forceParent(admin,"n:B","n:C");int r=f.rev();f.cs.agree(a,f.id,r);f.cs.agree(b,f.id,r);fails(()->f.cs.sign(a,f.id,r));
        }
        try(var f=new F()){
            f.cs.agree(a,f.id,1);f.cs.agree(b,f.id,1);f.cs.sign(a,f.id,1);
            var changed=f.entities.stream().map(e->e.id().equals("n:A")?new Estate(e.id(),e.kind(),e.name(),e.nation(),C,true):e).toList();f.s.sync(new Snapshot(changed,f.citizens));fails(()->f.cs.sign(b,f.id,1));
        }
        try(var f=new F()){
            f.set("days","14");f.sign();long end=number(f.cs.get(f.id),"ends");f.advance(1);f.cs.amend(a,f.id,f.rev());f.set("extra","Нова рольова умова");f.sign();check(number(f.cs.get(f.id),"ends")==end);
            f.cs.amend(a,f.id,f.rev());f.set("extend","7");f.sign();check(number(f.cs.get(f.id),"ends")==end+7*FeudalService.DAY);
            f.cs.amend(a,f.id,f.rev());check(f.cs.terms(f.id,f.rev()).n("extend")==0);
        }
        try(var f=new F()){
            f.set("days","14");f.set("renew","false");f.sign();int r=f.cs.amend(a,f.id,f.rev());fails(()->f.cs.edit(a,f.id,r,"days","21"));fails(()->f.cs.edit(a,f.id,r,"extend","7"));
        }
        // SQL survives close/reopen, including signatures, active terms, debt and immutable copies.
        Path temp=Files.createTempDirectory("vassal-contract-");String url="jdbc:h2:"+temp.resolve("state")+";DATABASE_TO_LOWER=TRUE";long saved;
        try(var f=new Fixture(url)){f.s.createCurrency(a,"n:A","SLV","Срібло","S");var cs=new ContractService(f.s,null);saved=cs.create(a,"n:B","n:A");cs.agree(a,saved,1);cs.agree(b,saved,1);cs.sign(a,saved,1);cs.sign(b,saved,1);}
        try(var f=new Fixture(url)){var cs=new ContractService(f.s,null);check(number(cs.get(saved),"active_rev")==1);check(number(cs.revision(saved,1),"signed_at")>0);}
        finally{try(var files=Files.walk(temp)){for(var file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}}
        var terms=new ContractTerms();check(ContractTerms.decode(terms.encode()).encode().equals(terms.encode()));fails(()->terms.with("period","0"));fails(()->terms.with("amount","-1"));fails(()->terms.with("resources","WHEAT:1,WHEAT:2"));fails(()->terms.with("extra","§kтекст"));fails(()->terms.with("first_date","2026-02-30"));
        for(int variant=0;variant<5;variant++)check(ContractBook.duty(variant,"Дубове","100 SLV кожні 7 реальних діб").endsWith("100 SLV кожні 7 реальних діб"));
        check(new HashSet<>(Arrays.asList(ContractBook.DUTY)).size()==5);
        var row=new HashMap<String,Object>();row.put("signed_at",1800000000000L);row.put("child_sign",A);row.put("parent_sign",B);
        var chapters=ContractBook.chapters(27,3,row,terms.with("extra","Забезпечувати прохід союзних військ"),"Дубове","Північ",id->id);
        var pages=ContractBook.pages(chapters);check(pages.size()<100);check(pages.stream().allMatch(p->p.length()<=210));check(String.join(" ",chapters).contains("не виконуються автоматично"));
        // Every stylistic variant preserves the complete operative string for every duty category.
        for(String clause:List.of("сплачувати 100.00 SLV кожні 7 реальних діб", "постачати 128 одиниць пшениці", "допомагати лише в оборонних війнах", "надавати субсидію 50.00 SLV кожні 14 діб"))for(int variant=0;variant<5;variant++)check(ContractBook.duty(variant,"Володіння",clause).endsWith(clause));
        var longTerms=terms.with("extra","А".repeat(4000));check(ContractBook.pages(ContractBook.chapters(27,3,row,longTerms,"Дубове","Північ",id->id)).size()<100);
        System.out.println("PASS ContractTests: "+checks+" lifecycle, treasury, council, inventory journal, persistence and wording checks");
        }
    }
}
