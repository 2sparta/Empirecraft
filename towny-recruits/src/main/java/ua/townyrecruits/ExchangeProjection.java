package ua.townyrecruits;

import java.util.*;
import ua.townyrecruits.MilitaryData.*;

/** Persistent native records are rewritten before entities are touched. Missing/dead/deserted records stay absent. */
public final class ExchangeProjection {
    private ExchangeProjection() {}
    private static UUID id(Object value){return UUID.fromString(value.toString().replace("t:",""));}
    private static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
    public static Map<UUID,UUID> nations(MilitaryData data){var out=new HashMap<UUID,UUID>();data.members.forEach((id,group)->{var loan=data.loans.get(group);var company=data.companies.get(group);var regiment=company==null?null:data.regiments.get(company.regiment());if(loan!=null)out.put(id,loan.nation());else if(regiment!=null)out.put(id,regiment.nation());});return out;}
    /** SavedData files may flush in different orders; current company metadata repairs an older army ledger. */
    public static void repair(ArmyData army,MilitaryData data,Policy.Snapshot snapshot){
        boolean changed=false;
        for(var entry:data.members.entrySet()){
            var company=data.companies.get(entry.getValue());if(company==null)continue;var regiment=data.regiments.get(company.regiment());if(regiment==null||!snapshot.nations().containsKey(regiment.nation()))continue;
            var loan=data.loans.get(entry.getValue());UUID expected=loan==null?regiment.nation():loan.nation();
            if(!regiment.nation().equals(expected))continue;var unit=army.units.get(entry.getKey());
            if(unit!=null&&!unit.retired()&&(!unit.owner().equals(company.commander())||!Objects.equals(unit.nation(),regiment.nation()))){army.units.put(entry.getKey(),new ArmyData.Unit(company.commander(),regiment.nation(),false));changed=true;}
        }
        if(changed){army.recount();army.m_77762_();}
    }
    public static boolean apply(ArmyData army,MilitaryData data,Map<String,Object> job){
        String asset=job.get("asset").toString(),kind=job.get("kind").toString();long version=((Number)job.get("seq")).longValue();
        if(data.exchangeVersions.getOrDefault(asset,0L)>=version)return false;
        UUID regiment=id(job.get("regiment")),town=id(job.get("town")),nation=id(job.get("nation")),commander=id(job.get("commander"));
        List<UUID> groups=Arrays.stream(job.get("groups").toString().split(",")).filter(x->!x.isBlank()).map(UUID::fromString).toList();
        require(Set.of("SALE","HIRE","RETURN").contains(kind)&&!groups.isEmpty(),"Невідомий стан передачі війська.");
        if(kind.equals("SALE")){
            var original=data.regiments.get(regiment);require(original!=null,"Полк для передачі недоступний.");
            for(UUID group:groups)require(!data.loans.containsKey(group),"Полк має активний найм.");
            data.regiments.put(regiment,new Regiment(regiment,nation,original.name(),commander));data.cities.put(regiment,town);
            for(UUID group:groups)if(data.companies.containsKey(group))data.companies.put(group,new Company(regiment,commander));
        }else if(kind.equals("HIRE")){
            require(groups.size()==1,"Контракт найманців має містити один загін.");UUID group=groups.get(0),temporary=id(job.get("contract"));
            var original=data.regiments.get(regiment);require(original!=null&&data.companies.containsKey(group),"Полк найманців недоступний.");
            if(!data.loans.containsKey(group))data.loans.put(group,new Loan(group,temporary,original,id(job.get("origin")),commander,town,nation));
            data.regiments.put(temporary,new Regiment(temporary,nation,"Найманці_"+original.name(),commander));data.cities.put(temporary,town);
            data.banners.putIfAbsent(temporary,RegimentalBanners.design(data,regiment));UUID bearer=data.bearers.get(regiment);
            if(bearer!=null&&group.equals(data.members.get(bearer))){data.bearers.put(temporary,bearer);data.bearers.remove(regiment);}
            data.companies.put(group,new Company(temporary,commander));
        }else{
            for(UUID group:groups){var loan=data.loans.get(group);if(loan==null)continue;
                data.regiments.putIfAbsent(loan.original().id(),loan.original());data.cities.put(loan.original().id(),loan.town());data.companies.put(group,new Company(loan.original().id(),loan.commander()));
                UUID bearer=data.bearers.remove(loan.temporary());if(bearer!=null)data.bearers.putIfAbsent(loan.original().id(),bearer);
                data.regiments.remove(loan.temporary());data.cities.remove(loan.temporary());data.banners.remove(loan.temporary());data.loans.remove(group);
            }
        }
        for(var entry:data.members.entrySet())if(groups.contains(entry.getValue())){
            var unit=army.units.get(entry.getKey());if(unit!=null&&!unit.retired())army.units.put(entry.getKey(),new ArmyData.Unit(commander,nation,false));
        }
        army.recount();army.m_77762_();data.m_77762_();return true;
    }
}
