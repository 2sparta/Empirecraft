package ua.townyrecruits;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import ua.townyrecruits.MilitaryData.*;

public final class ExchangeProjectionTest {
    static final UUID REG=UUID.randomUUID(),GROUP=UUID.randomUUID(),CAPTAIN=UUID.randomUUID(),NEXT=UUID.randomUUID(),TOWN=UUID.randomUUID(),BUYER=UUID.randomUUID(),NATION=UUID.randomUUID(),OTHER=UUID.randomUUID(),CONTRACT=UUID.randomUUID();
    static Map<String,Object> job(String kind,long seq){var out=new HashMap<String,Object>();out.put("asset",(kind.equals("SALE")?"R:":"C:")+(kind.equals("SALE")?REG:GROUP));out.put("seq",seq);out.put("kind",kind);out.put("regiment",REG.toString());out.put("groups",GROUP.toString());out.put("town","t:"+(kind.equals("RETURN")?TOWN:BUYER));out.put("nation",(kind.equals("RETURN")?NATION:OTHER).toString());out.put("commander",(kind.equals("SALE")?NEXT:CAPTAIN).toString());out.put("contract",CONTRACT.toString());out.put("origin","t:"+TOWN);return out;}
    static MilitaryData military(){var d=new MilitaryData();d.regiments.put(REG,new Regiment(REG,NATION,"Полк",CAPTAIN));d.companies.put(GROUP,new Company(REG,CAPTAIN));d.cities.put(REG,TOWN);d.banners.put(REG,new RegimentalBanners.Design(12,List.of(new RegimentalBanners.Layer("bs",2))));return d;}
    static Policy.Snapshot policy(UUID captainNation){var a=new Policy.Nation(NATION,"A",CAPTAIN,"C",1,Map.of(),Set.of(),Set.of());var b=new Policy.Nation(OTHER,"B",NEXT,"N",1,Map.of(),Set.of(),Set.of());return new Policy.Snapshot(Map.of(NATION,a,OTHER,b),Map.of(CAPTAIN,captainNation,NEXT,OTHER));}
    static boolean apply(ArmyData a,MilitaryData d,Map<String,Object> job){boolean changed=ExchangeProjection.apply(a,d,job);if(changed)d.exchangeVersions.put(job.get("asset").toString(),((Number)job.get("seq")).longValue());return changed;}
    public static void main(String[] args){
        UUID live=UUID.randomUUID(),offline=UUID.randomUUID(),retired=UUID.randomUUID(),missing=UUID.randomUUID();var a=new ArmyData();var d=military();
        for(UUID id:List.of(live,offline)){a.put(id,new ArmyData.Unit(CAPTAIN,NATION,false));d.members.put(id,GROUP);}a.put(retired,new ArmyData.Unit(CAPTAIN,NATION,true));d.members.put(retired,GROUP);d.members.put(missing,GROUP);d.bearers.put(REG,live);
        assert apply(a,d,job("SALE",1));assert a.units.get(offline).owner().equals(NEXT)&&a.units.get(live).nation().equals(OTHER);assert a.counts.get(OTHER)==2&&!a.counts.containsKey(NATION);assert a.units.get(retired).retired()&&!a.units.containsKey(missing);
        assert d.regiments.get(REG).leader().equals(NEXT)&&d.cities.get(REG).equals(BUYER)&&d.bearers.get(REG).equals(live);assert !apply(a,d,job("SALE",1));
        var persisted=MilitaryData.load(d.m_7176_(new CompoundTag()));assert persisted.cities.equals(d.cities)&&persisted.exchangeVersions.equals(d.exchangeVersions);
        a.units.put(live,new ArmyData.Unit(CAPTAIN,NATION,false));a.units.put(offline,new ArmyData.Unit(CAPTAIN,NATION,false));ExchangeProjection.repair(a,persisted,policy(OTHER));a.reconcile(policy(OTHER));assert a.units.get(offline).owner().equals(NEXT)&&!a.units.get(live).retired()&&a.counts.get(OTHER)==2;assert a.units.get(retired).retired()&&!a.units.containsKey(missing);
        a=new ArmyData();d=military();for(UUID id:List.of(live,offline)){a.put(id,new ArmyData.Unit(CAPTAIN,NATION,false));d.members.put(id,GROUP);}d.bearers.put(REG,live);
        assert apply(a,d,job("HIRE",2));assert d.loans.get(GROUP).original().id().equals(REG)&&d.companies.get(GROUP).regiment().equals(CONTRACT);assert d.companies.get(GROUP).commander().equals(CAPTAIN);assert d.cities.get(CONTRACT).equals(BUYER)&&d.bearers.get(CONTRACT).equals(live)&&!d.bearers.containsKey(REG);
        assert MilitaryPolicy.protectsAssignment(d,CAPTAIN,UUID.randomUUID(),GROUP,false);assert MilitaryPolicy.protectsAssignment(d,NEXT,GROUP,GROUP,false);assert MilitaryPolicy.protectsAssignment(d,CAPTAIN,GROUP,null,false);assert !MilitaryPolicy.protectsAssignment(d,CAPTAIN,GROUP,GROUP,false);
        // A partially restored Towny membership must not retire contracted loaded/offline records.
        a.reconcile(policy(NATION),ExchangeProjection.nations(d));assert a.counts.get(OTHER)==2&&!a.units.get(offline).retired();
        a.units.put(offline,new ArmyData.Unit(CAPTAIN,NATION,false));ExchangeProjection.repair(a,d,policy(OTHER));assert a.units.get(offline).nation().equals(OTHER)&&!a.units.get(offline).retired();
        d=MilitaryData.load(d.m_7176_(new CompoundTag()));a=ArmyData.load(a.m_7176_(new CompoundTag()));assert d.loans.size()==1&&d.loans.get(GROUP).commander().equals(CAPTAIN);assert !apply(a,d,job("HIRE",2));
        a.remove(live);d.members.remove(live);d.bearers.remove(CONTRACT); // A casualty/deserter never comes back with the contract.
        assert apply(a,d,job("RETURN",3));assert a.units.size()==1&&a.units.get(offline).owner().equals(CAPTAIN)&&a.units.get(offline).nation().equals(NATION);assert !d.regiments.containsKey(CONTRACT)&&d.loans.isEmpty()&&!d.bearers.containsKey(REG);assert d.companies.get(GROUP).regiment().equals(REG)&&d.cities.get(REG).equals(TOWN);assert !apply(a,d,job("HIRE",2));assert !apply(a,d,job("RETURN",3));
        a.reconcile(policy(NATION),ExchangeProjection.nations(d));assert a.counts.get(NATION)==1&&!a.units.get(offline).retired();
        System.out.println("PASS production exchange projections: permanent city/commander/nation transfer, loaded/offline counts, lease captain/temporary regiment, heraldry, real NBT/restart, transition nation guards, no casualty/deserter resurrection, ordered replay and idempotent return");
    }
}
