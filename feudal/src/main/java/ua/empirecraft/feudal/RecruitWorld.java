package ua.empirecraft.feudal;

import com.google.gson.*;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.*;
import com.palmergames.bukkit.towny.event.TownAddResidentEvent;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import org.bukkit.Bukkit;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;

/** Towny/native adapter. Temporary citizenship deliberately never calls Resident.removeTown, which strips plots. */
final class RecruitWorld implements RecruitMarketService.World {
    private Class<?> nativeApi;
    private final FleetWorld towns;
    final Set<String> moving=new HashSet<>();
    RecruitWorld(FleetWorld towns){this.towns=towns;}
    @SuppressWarnings("unchecked") Map<String,Object> call(String action,Map<String,Object> data){
        require(Bukkit.isPrimaryThread(),LocalText.tr("Керування військом виконується в головному потоці."));
        try {
            if(nativeApi==null){Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());nativeApi=Class.forName("ua.townyrecruits.RegimentExchange",true,server.getClass().getClassLoader());}
            return (Map<String,Object>)nativeApi.getMethod("request",String.class,Map.class).invoke(null,action,data);
        }catch(InvocationTargetException error){if(error.getCause() instanceof IllegalArgumentException invalid)throw invalid;throw new IllegalStateException(LocalText.tr("Облік військ тимчасово недоступний."),error.getCause());}
        catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException(LocalText.tr("Потрібен Towny Recruits 1.5.0 на сервері."),error);}
    }
    static RecruitMarketService.Roster roster(Map<String,Object> row){return new RecruitMarketService.Roster(string(row,"asset"),string(row,"regiment"),string(row,"groups"),string(row,"town"),string(row,"nation"),string(row,"commander"),string(row,"name"),((Number)row.get("count")).intValue(),string(row,"fingerprint"),Boolean.TRUE.equals(row.get("eligible")),Boolean.TRUE.equals(row.get("exclusive")),!Boolean.FALSE.equals(row.get("local")));}
    @Override public RecruitMarketService.Roster roster(String asset){try{return roster(call("snapshot",Map.of("asset",asset)));}catch(IllegalArgumentException removed){return null;}}
    @SuppressWarnings("unchecked") List<RecruitMarketService.Roster> all(){return ((List<Map<String,Object>>)call("list",Map.of()).get("assets")).stream().map(RecruitWorld::roster).toList();}
    @Override public String home(String person){return towns.hometown(person);}
    @Override public boolean commander(String town,String person){String home=home(person);return home!=null&&(town.equals(home)||!towns.war(town,home)&&towns.allied(town,home));}
    @Override public String nation(String town){var t=FleetWorld.town(town);var n=t==null?null:t.getNationOrNull();return n==null?null:n.getUUID().toString();}
    @Override public boolean mayor(String person,String town){return towns.mayor(person,town);}
    @Override public boolean republic(String town){return towns.republic(town);}
    @Override public boolean direct(String seller,String buyer){return towns.direct(seller,buyer);}
    @Override public boolean safeCaptain(String person){var r=TownyAPI.getInstance().getResident(UUID.fromString(person));return r!=null&&r.hasTown()&&!r.isMayor()&&!r.isKing()&&!r.isJailed();}
    @Override public boolean capacity(String nation,int incoming){return Boolean.TRUE.equals(call("capacity",Map.of("nation",nation,"incoming",incoming)).get("allowed"));}
    @Override public String citizenship(String person){
        var r=TownyAPI.getInstance().getResident(UUID.fromString(person));require(r!=null&&r.hasTown(),LocalText.tr("Командир не має початкового міста."));
        var out=new LinkedHashMap<String,Object>();out.put("town",TownyBridge.townId(r.getTownOrNull()));out.put("townRanks",List.copyOf(r.getTownRanks()));out.put("nationRanks",List.copyOf(r.getNationRanks()));out.put("title",r.getTitle());out.put("surname",r.getSurname());out.put("joined",r.getJoinedTownAt());return new Gson().toJson(out);
    }
    private static List<String> strings(JsonObject state,String key){List<String> out=new ArrayList<>();for(var item:state.getAsJsonArray(key))out.add(item.getAsString());return out;}
    void move(String person,String destination,String saved,boolean restoring)throws Exception {
        Resident r=TownyAPI.getInstance().getResident(UUID.fromString(person));Town target=FleetWorld.town(destination);
        require(r!=null&&target!=null,LocalText.tr("Місто або командир для відновлення договору недоступні."));
        JsonObject state=JsonParser.parseString(saved).getAsJsonObject();
        require(state.get("town").getAsString().startsWith("t:"),LocalText.tr("Збережене громадянство пошкоджене."));
        if(restoring)require(destination.equals(state.get("town").getAsString()),LocalText.tr("Повернення дозволене лише у початкове місто."));
        Town previous=r.getTownOrNull();
        if(previous==target&&!restoring)return;
        if(previous==target&&restoring&&r.getTownRanks().equals(strings(state,"townRanks"))&&r.getNationRanks().equals(strings(state,"nationRanks"))&&r.getTitle().equals(state.get("title").getAsString())&&r.getSurname().equals(state.get("surname").getAsString())&&r.getJoinedTownAt()==state.get("joined").getAsLong())return;
        String before=previous==null?null:citizenship(person);moving.add(person);
        try {
            if(previous!=target){
                require(!r.isMayor()&&!r.isKing(),LocalText.tr("Командир із державною посадою не може тимчасово змінити місто."));
                // Direct Town list transfer preserves resident-owned non-embassy plots.
                if(previous!=null)previous.removeResident(r);
                r.setTownRanks(new ArrayList<>());r.setNationRanks(new ArrayList<>());r.setTown(null,false);r.setTown(target,false);
                r.setJoinedTownAt(System.currentTimeMillis());
            }
            if(restoring){r.setTownRanks(strings(state,"townRanks"));r.setNationRanks(strings(state,"nationRanks"));r.setTitle(state.get("title").getAsString());r.setSurname(state.get("surname").getAsString());r.setJoinedTownAt(state.get("joined").getAsLong());}
            else {r.setTownRanks(new ArrayList<>());r.setNationRanks(new ArrayList<>());}
            r.save();target.save();if(previous!=null&&previous!=target)previous.save();
            if(previous!=target){if(previous!=null)Bukkit.getPluginManager().callEvent(new TownRemoveResidentEvent(r,previous));Bukkit.getPluginManager().callEvent(new TownAddResidentEvent(r,target));}
        } catch(Exception error){
            try {
                Town current=r.getTownOrNull();if(current!=null&&current!=previous)current.removeResident(r);
                r.setTown(null,false);if(previous!=null){r.setTown(previous,false);JsonObject rollback=JsonParser.parseString(before).getAsJsonObject();r.setTownRanks(strings(rollback,"townRanks"));r.setNationRanks(strings(rollback,"nationRanks"));r.setTitle(rollback.get("title").getAsString());r.setSurname(rollback.get("surname").getAsString());r.setJoinedTownAt(rollback.get("joined").getAsLong());previous.save();}
                r.save();target.save();
            }catch(Exception recovery){error.addSuppressed(recovery);}
            throw error;
        } finally {moving.remove(person);}
    }
}
