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
  assert Policy.hireStatus(true,false,false,true,0,100)==Policy.HireStatus.CENSUS_FAILED;
  assert Policy.hireStatus(true,true,false,true,0,100)==Policy.HireStatus.ALLOWED;
  assert Policy.hireStatus(false,true,false,true,0,100)==Policy.HireStatus.NOT_READY;
  assert Policy.hireStatus(true,true,false,false,0,100)==Policy.HireStatus.NO_NATION;
  assert Policy.hireStatus(true,true,true,true,0,100)==Policy.HireStatus.ALREADY_OWNED;
  assert Policy.hireStatus(true,true,false,true,100,100)==Policy.HireStatus.LIMIT_REACHED;
  String active=Policy.team(a),deleted=Policy.team(b),legacy="ec_legacy_nation",manual="manual";
  Set<String> removed=Policy.staleTeams(Set.of(active),List.of(active,deleted,legacy,manual));assert removed.equals(Set.of(deleted,legacy));
  assert Policy.staleTeams(Set.of(),List.of(active,deleted,legacy,manual)).equals(Set.of(active,deleted,legacy));
  Map<String,Map<String,Integer>> relations=new HashMap<>();relations.put(active,new HashMap<>(Map.of(deleted,-1,legacy,1,manual,1)));relations.put(deleted,new HashMap<>(Map.of(active,-1)));relations.put(manual,new HashMap<>(Map.of(legacy,-1,active,0)));
  UUID p1=UUID.randomUUID(),p2=UUID.randomUUID();Map<UUID,String> embargoes=new HashMap<>(Map.of(p1,active+","+deleted+","+legacy+","+manual,p2,deleted));
  Policy.clearRelations(removed,relations,embargoes);
  assert !relations.containsKey(deleted);assert relations.get(active).equals(Map.of(manual,1));assert relations.get(manual).equals(Map.of(active,0));
  assert embargoes.equals(Map.of(p1,active+","+manual));
  System.out.println("PASS zero-of-100 census diagnostic; deleted nation and legacy faction graph cleanup; unrelated faction retained");
  System.out.println("PASS limits, unlimited residents, zero override, UUID identity, symmetric enemies, alliances and neutrality");
 }
}
