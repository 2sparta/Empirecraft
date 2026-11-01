package guardtest;
import ua.townyrecruits.Policy;
import java.util.*;
public final class PolicyTest {
 public static void main(String[] args){
  assert Policy.limit(1,null)==20;assert Policy.limit(2,null)==25;assert Policy.limit(4,null)==35;assert Policy.limit(5,0)==0;assert Policy.limit(1,90)==90;
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();
  var na=new Policy.Nation(a,"A",a,"a",1,Map.of(),Set.of(b),Set.of());
  var nb=new Policy.Nation(b,"B",b,"b",1,Map.of(),Set.of(),Set.of(a));
  var s=new Policy.Snapshot(Map.of(a,na,b,nb),Map.of());
  assert Policy.relation(s,a,b)==-1;assert Policy.relation(s,b,a)==-1;assert Policy.relation(s,a,a)==1;assert Policy.relation(s,a,null)==0;
  assert Policy.team(a).equals(Policy.team(a));assert !Policy.team(a).equals(Policy.team(b));
  var neutral=new Policy.Nation(a,"renamed",a,"a",1,Map.of(),Set.of(),Set.of());
  s=new Policy.Snapshot(Map.of(a,neutral,b,nb),Map.of());assert Policy.relation(s,a,b)==1;
  nb=new Policy.Nation(b,"B",b,"b",1,Map.of(),Set.of(),Set.of());s=new Policy.Snapshot(Map.of(a,neutral,b,nb),Map.of());assert Policy.relation(s,a,b)==0;
  assert Policy.limit(Integer.MAX_VALUE,null)==Integer.MAX_VALUE;
  System.out.println("PASS limits, unlimited residents, zero override, UUID identity, symmetric enemies, alliances and neutrality");
 }
}
