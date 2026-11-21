package ua.townyrecruits;

import com.talhanation.recruits.RecruitEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import net.minecraft.world.entity.player.Player;
import ua.townyrecruits.MilitaryData.*;
import ua.townyrecruits.tactics.*;

/** Main-thread native projection of Feudal's durable commerce ledger. No Bukkit API copies. */
public final class RegimentExchange {
    private static Set<UUID> locks=Set.of(),regimentLocks=Set.of();
    private static boolean syncing;
    private RegimentExchange() {}
    private static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
    static UUID uuid(Object value){return UUID.fromString(value.toString().replace("t:",""));}
    static String string(Map<?,?> row,String key){return Objects.toString(row.get(key),"");}
    public static void clear(){locks=Set.of();regimentLocks=Set.of();syncing=false;}
    public static boolean locked(UUID group){return group!=null&&locks.contains(group);}
    public static boolean regimentLocked(UUID id){var b=Bridge.INSTANCE;return regimentLocks.contains(id)||b!=null&&b.military.data.loans.values().stream().anyMatch(l->l.temporary().equals(id)||l.original().id().equals(id));}
    public static boolean captain(UUID owner){var b=Bridge.INSTANCE;return b!=null&&b.military.data.loans.values().stream().anyMatch(l->l.commander().equals(owner));}
    public static boolean mayControl(AbstractRecruitEntity r,UUID person){var b=Bridge.INSTANCE;return r.getIsOwned()&&r.m_6084_()&&person!=null&&person.equals(r.getOwnerUUID())&&(r.m_20193_().m_5776_()||b!=null&&b.ready&&b.censusReady()&&!locked(r.getGroup()));}
    static UUID city(Bridge b,Regiment regiment)throws ReflectiveOperationException {
        UUID city=b.military.data.cities.get(regiment.id());if(city==null){city=b.towny.city(regiment.leader());if(city!=null){b.military.data.cities.put(regiment.id(),city);b.military.data.m_77762_();}}return city;
    }
    static Map<UUID,UUID> nations(Bridge b){return ExchangeProjection.nations(b.military.data);}
    static UUID expected(Bridge b,AbstractRecruitEntity r){var data=b.military.data;UUID group=data.members.get(r.m_20148_());var loan=data.loans.get(group);if(loan!=null)return loan.nation();var company=data.companies.get(group);var regiment=company==null?null:data.regiments.get(company.regiment());return regiment==null?b.snapshot.residents().get(r.getOwnerUUID()):regiment.nation();}
    /** Called before any nation retirement. A half-restored external ledger can never dismiss contracted soldiers. */
    @SuppressWarnings("unchecked") static void sync(Bridge b)throws ReflectiveOperationException {
        if(syncing)return;Object plugin=b.towny.feudal();
        if(plugin==null){require(b.military.data.loans.isEmpty()&&b.military.data.exchangeVersions.isEmpty(),"Договори рекрутів очікують EmpirecraftFeudal.");locks=Set.of();regimentLocks=Set.of();return;}
        java.lang.reflect.Method method;
        try{method=plugin.getClass().getMethod("recruitState");}catch(NoSuchMethodException old){require(b.military.data.exchangeVersions.isEmpty()&&b.military.data.loans.isEmpty(),"Оновіть EmpirecraftFeudal для відновлення договорів рекрутів.");return;}
        var state=(Map<String,Object>)method.invoke(plugin);
        require(Boolean.TRUE.equals(state.get("ready")),"Реєстр договорів рекрутів ще не готовий.");syncing=true;
        try {
            for(Object raw:(List<?>)state.getOrDefault("jobs",List.of()))apply(b,(Map<String,Object>)raw);
            Set<UUID> groups=new HashSet<>(),regiments=new HashSet<>();
            for(Object raw:(List<?>)state.getOrDefault("locks",List.of())){var row=(Map<String,Object>)raw;regiments.add(uuid(row.get("regiment")));for(String group:string(row,"groups").split(","))if(!group.isBlank())groups.add(uuid(group));}
            locks=Set.copyOf(groups);regimentLocks=Set.copyOf(regiments);
        } finally {syncing=false;}
    }
    public static Map<String,Object> request(String action,Map<String,Object> data)throws Exception {
        var b=Bridge.INSTANCE;if(b==null||!b.server.m_18695_()||!b.ready||!b.censusReady())throw new IllegalStateException("Облік рекрутів ще не готовий.");
        if(action.equals("sync")){sync(b);b.refreshNow();return Map.of("ok",true,"versions",Map.copyOf(b.military.data.exchangeVersions));}
        if(action.equals("identity")){
            UUID unit=uuid(data.get("unit"));var records=b.military.data;var company=records.companies.get(records.members.get(unit));var regiment=company==null?null:records.regiments.get(company.regiment());
            if(regiment==null)return Map.of("managed",false);UUID town=city(b,regiment);
            return Map.of("managed",true,"town",town==null?"":"t:"+town,"nation",regiment.nation().toString());
        }
        if(action.equals("capacity")){UUID nation=uuid(data.get("nation"));int incoming=((Number)data.get("incoming")).intValue();return Map.of("allowed",incoming>=0&&b.snapshot.nations().containsKey(nation)&&(incoming==0||(long)b.count(nation)+incoming<=b.limit(nation)));}
        if(action.equals("list")){List<Map<String,Object>> rows=new ArrayList<>();for(var regiment:List.copyOf(b.military.data.regiments.values())){
            if(b.military.data.loans.values().stream().anyMatch(l->l.temporary().equals(regiment.id())))continue;
            rows.add(snapshot(b,"R:"+regiment.id()));for(var e:b.military.data.companies.entrySet())if(e.getValue().regiment().equals(regiment.id()))rows.add(snapshot(b,"C:"+e.getKey()));
        }return Map.of("assets",rows);}
        if(action.equals("snapshot"))return snapshot(b,string(data,"asset"));
        throw new IllegalArgumentException("Невідома дія обліку полків.");
    }
    static Map<String,Object> snapshot(Bridge b,String asset)throws Exception {
        require(asset.matches("[RC]:[0-9a-fA-F-]{36}"),"Укажіть R:UUID полку або C:UUID загону.");UUID id=uuid(asset.substring(2));var d=b.military.data;
        UUID reg=asset.startsWith("R:")?id:Optional.ofNullable(d.companies.get(id)).orElseThrow(()->new IllegalArgumentException("Загін не знайдено.")).regiment();
        var r=d.regiments.get(reg);require(r!=null,"Полк не знайдено.");UUID town=city(b,r);require(town!=null,"Командир полку не має міста.");
        var groups=d.companies.entrySet().stream().filter(e->e.getValue().regiment().equals(reg)&&(asset.startsWith("R:")||e.getKey().equals(id))).map(Map.Entry::getKey).sorted().toList();
        UUID commander=asset.startsWith("R:")?r.leader():d.companies.get(id).commander();Map<UUID,UUID> homes=new HashMap<>();List<String> roster=new ArrayList<>();boolean eligible=!groups.isEmpty(),exclusive=true,routing=false,local=town.equals(b.towny.city(commander));
        for(var e:d.members.entrySet())if(groups.contains(e.getValue())){var u=b.data.units.get(e.getKey());if(u!=null&&!u.retired()){var hire=b.recruitment.state.purchases.get(e.getKey());if(hire!=null&&hire.militiaUntil>0)eligible=false;roster.add(e.getKey()+":"+u.owner()+":"+u.nation());if(!homes.containsKey(u.owner()))homes.put(u.owner(),b.towny.city(u.owner()));local&=town.equals(homes.get(u.owner()));eligible&=b.military.canCommand(r,u.owner())&&r.nation().equals(u.nation());}}
        for(var e:b.data.units.entrySet())if(!e.getValue().retired()&&e.getValue().owner().equals(commander)&&!groups.contains(d.members.get(e.getKey())))exclusive=false;
        for(var e:d.companies.entrySet())if(e.getValue().commander().equals(commander)&&!groups.contains(e.getKey()))exclusive=false;
        for(UUID group:groups)routing|=d.squad(group).routing;
        for(var e:d.companies.entrySet())if(groups.contains(e.getKey()))roster.add("group:"+e.getKey()+":"+e.getValue().commander());
        Collections.sort(roster);String joined=String.join(",",groups.stream().map(UUID::toString).toList());
        String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((asset+"|"+town+"|"+r+"|"+roster).getBytes(StandardCharsets.UTF_8)));
        int count=(int)roster.stream().filter(x->!x.startsWith("group:")).count();
        var out=new LinkedHashMap<String,Object>();out.put("asset",asset);out.put("regiment",reg.toString());out.put("groups",joined);out.put("town","t:"+town);out.put("nation",r.nation().toString());out.put("commander",commander.toString());out.put("name",r.name());out.put("count",count);out.put("fingerprint",fingerprint);out.put("eligible",eligible&&count>0&&!routing);out.put("exclusive",exclusive);out.put("local",local);return out;
    }
    static void apply(Bridge b,Map<String,Object> job)throws ReflectiveOperationException {
        String asset=string(job,"asset"),kind=string(job,"kind");long version=((Number)job.get("seq")).longValue();var d=b.military.data;
        if(d.exchangeVersions.getOrDefault(asset,0L)>=version)return;
        require(RecruitEvents.recruitsGroupsManager!=null,"Групи Recruits ще не готові.");
        UUID reg=uuid(job.get("regiment")),town=uuid(job.get("town")),nation=uuid(job.get("nation")),commander=uuid(job.get("commander"));
        List<UUID> groups=Arrays.stream(string(job,"groups").split(",")).filter(x->!x.isBlank()).map(UUID::fromString).toList();
        if(Set.of("HIRE","RETURN").contains(kind)&&!town.equals(b.towny.city(commander)))return;
        if(!ExchangeProjection.apply(b.data,d,job))return;
        for(UUID group:groups){var g=RecruitEvents.recruitsGroupsManager.getGroup(group);if(g==null)continue;UUID previous=g.getPlayerUUID();
            g.setPlayer(new RecruitsPlayerInfo(commander,b.towny.name(commander)));
            for(var recruit:List.copyOf(b.loaded))if(group.equals(d.members.get(recruit.m_20148_()))&&recruit.getIsOwned()){
                TacticalOrders.cancel(recruit);MilitarySystem.applyCommander(recruit,group,commander);recruit.m_6710_(null);
            }
            RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(b.server.m_129783_(),previous);RecruitEvents.recruitsGroupsManager.broadCastGroupsToPlayer(b.server.m_129783_(),commander);
        }
        RecruitEvents.recruitsGroupsManager.save(b.server.m_129783_());d.exchangeVersions.put(asset,version);d.m_77762_();b.data.recount();b.data.m_77762_();b.dirty=true;
    }
}
