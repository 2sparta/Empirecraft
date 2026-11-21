package ua.townyrecruits;
import java.util.*;import java.math.*;import java.nio.file.*;
public final class PopulationTest {
 static int count;static void check(boolean ok,String msg){count++;if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args)throws Exception {
  Path file=Files.createTempDirectory("population-test").resolve("state.json");var state=new RecruitmentState(file);UUID town=UUID.randomUUID();long now=System.currentTimeMillis();
  state.approveVillage("oak",town,10,6,now,new Random(3));var v=state.villages.get("oak");var rolls=List.copyOf(v.households);int cap=v.capacity();check(cap>=20&&cap<=40,"2–4 people per approved house");
  state.approveVillage("oak",town,10,6,now,new Random(9));check(v.households.equals(rolls)&&v.available==cap,"Approval does not reroll existing houses");
  state.change(()->v.available=0);v.refreshed=now;
  state.refreshPeople(now+6*3600000L-1);check(v.available==0,"No early recovery");state.refreshPeople(now+6*3600000L);check(v.available==1,"One person after six real hours");
  state.refreshPeople(now+18*3600000L);check(v.available==3,"Offline catch up without tick dependence");
  state.refreshPeople(now+1000*3600000L);check(v.available==cap,"Recovery cap");
  var disk=new RecruitmentState(file);check(disk.people(town)==cap,"Persistent village resource");check(disk.villages.get("oak").households.equals(rolls),"Persistent per-house rolls");
  var p=new RecruitmentState.Purchase();p.unit=UUID.randomUUID();p.player=UUID.randomUUID();p.town=town;p.nation=UUID.randomUUID();p.phase="PREPARED";p.development=BigDecimal.ZERO;p.people=1;p.militiaUntil=now+7200000;
  disk.reserve(p);check(disk.people(town)==cap-1,"One resource per purchase");
  try{disk.reserve(p);throw new AssertionError("Duplicate purchase accepted");}catch(IllegalStateException expected){}check(disk.people(town)==cap-1,"Blocked operation doesn't debit twice");
  disk.refund(p.unit);check(disk.people(town)==cap,"Failed purchase returns resource");disk.refund(p.unit);check(disk.people(town)==cap,"Refund idempotence");
  check(new RecruitmentState(file).purchases.get(p.unit).militiaUntil==now+7200000,"Militia deadline survives restart");
  String legacy="{\"schema\":1,\"nations\":{},\"development\":{\""+town+"\":5},\"homes\":{},\"purchases\":{}}";disk.decode(legacy);check(disk.balance(town).intValue()==5&&disk.people(town)==0,"Old development is preserved but is not manpower");
  disk.approveVillage("oak",town,2,6,now,new Random(1));int before=disk.people(town);try{disk.approveVillage("oak",UUID.randomUUID(),2,6,now,new Random(1));throw new AssertionError();}catch(IllegalArgumentException expected){}check(disk.people(town)==before,"Village cannot be reassigned to mint population");
  System.out.println("POPULATION_LEDGER_PASS checks="+count);
 }
}
