package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.WorldCoord;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

/** Authoritative Towny diplomacy and national ensigns; native combat never trusts a client-supplied city. */
final class EngagementController {
    final FleetController fleet;final Database db;private Class<?> nativeApi;
    EngagementController(FleetController fleet)throws Exception {
        this.fleet=fleet;db=fleet.fleet.db;
        db.execute("CREATE TABLE IF NOT EXISTS naval_ensigns(nation VARCHAR(36) PRIMARY KEY,kind VARCHAR(16),payload CLOB,hash VARCHAR(64),revision BIGINT,author VARCHAR(36))");
    }
    @SuppressWarnings("unchecked") Map<String,Object> nativeCall(String action,Map<String,Object> data)throws Exception {
        require(Bukkit.isPrimaryThread(),LocalText.tr("Морські прапори потребують серверного потоку."));
        try {
            if(nativeApi==null){Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());nativeApi=Class.forName("ua.empirecraft.engagement.EngagementNative",true,server.getClass().getClassLoader());}
            return (Map<String,Object>)nativeApi.getMethod("request",String.class,Map.class).invoke(null,action,data);
        }catch(InvocationTargetException error){if(error.getCause() instanceof IllegalArgumentException invalid)throw new IllegalArgumentException(LocalText.tr(invalid.getMessage()),invalid);throw new IllegalStateException(LocalText.tr("Бойова система тимчасово недоступна."),error.getCause());}
        catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException(LocalText.tr("Потрібен мод Empirecraft Engagement на сервері та клієнті."),error);}
    }
    String water(String owner,String territory){
        if(territory==null||territory.isBlank())return "WILDERNESS";
        if(owner==null||owner.isBlank())return "UNKNOWN";
        if(owner.equals(territory))return "OWN";
        if(fleet.world.war(owner,territory))return "ENEMY";
        return fleet.world.allied(owner,territory)?"ALLIED":"NEUTRAL";
    }
    String nation(String city){var town=FleetWorld.town(city);var nation=town==null?null:town.getNationOrNull();return nation==null?"":nation.getUUID().toString();}
    Map<String,Object> design(String city)throws Exception {
        String nation=nation(city);var row=nation.isEmpty()?null:db.one("SELECT * FROM naval_ensigns WHERE nation=?",nation);
        if(row==null)return Map.of();var result=new LinkedHashMap<String,Object>(row);result.remove("author");return result;
    }
    Map<String,Object> request(String action,Map<String,Object> data)throws Exception {
        require(Bukkit.isPrimaryThread(),LocalText.tr("Бойові правила перевіряються в серверному потоці."));
        if(action.equals("point")){
            String owner=Objects.toString(data.get("town"),"");var world=Bukkit.getWorld(UUID.fromString(data.get("world").toString()));
            if(world==null)return Map.of("water","UNKNOWN");
            if(!TownyAPI.getInstance().isTownyWorld(world))return Map.of("water","WILDERNESS");
            int size=com.palmergames.bukkit.towny.TownySettings.getTownBlockSize();var town=new WorldCoord(world,(int)Math.floor(((Number)data.get("x")).doubleValue()/size),(int)Math.floor(((Number)data.get("z")).doubleValue()/size)).getTownOrNull();
            return Map.of("water",water(owner,town==null?null:TownyBridge.townId(town)));
        }
        if(action.equals("resident")){String town=fleet.world.hometown(data.get("player").toString());return Map.of("town",Objects.toString(town,""));}
        if(action.equals("ship")){
            long id=((Number)data.get("ship")).longValue();fleet.ensure(id);var vessel=fleet.fleet.ship(id);String owner=string(vessel,"town");var hull=fleet.world.hull(id);
            if(hull==null)return Map.of("ready",false);
            var claims=fleet.world.territories(hull);var waters=claims.stream().map(city->water(owner,city)).toList();
            return Map.of("ready",true,"town",owner,"waters",waters.isEmpty()?List.of("WILDERNESS"):waters,
                "locked",db.one("SELECT ship FROM naval_locks WHERE ship=?",id)!=null,"nation",nation(owner),"design",design(owner));
        }
        if(action.equals("controls")){
            long id=((Number)data.get("ship")).longValue();fleet.ensure(id);
            var player=Bukkit.getPlayer(UUID.fromString(data.get("player").toString()));boolean crew=fleet.fleet.controls(data.get("player").toString(),id);
            return Map.of("allow",player!=null&&(player.hasPermission("vs_sails.ship.bypass")||crew&&player.hasPermission("vs_sails.ship.control")),"crew",crew,"locked",db.one("SELECT ship FROM naval_locks WHERE ship=?",id)!=null);
        }
        if(action.equals("friendly"))return Map.of("friendly",fleet.world.allied(data.get("a").toString(),data.get("b").toString())&&!fleet.world.war(data.get("a").toString(),data.get("b").toString()));
        if(action.equals("open")){
            Player player=Bukkit.getPlayer(UUID.fromString(data.get("player").toString()));require(player!=null,LocalText.tr("Гравець вийшов із сервера."));
            fleet.openFlags(player,((Number)data.get("ship")).longValue());return Map.of("ok",true);
        }
        throw new IllegalArgumentException(LocalText.tr("Невідома дія бойової системи."));
    }
    private String authority(Player player){
        var resident=TownyAPI.getInstance().getResident(player.getUniqueId());var town=resident==null?null:resident.getTownOrNull();var nation=town==null?null:town.getNationOrNull();
        require(nation!=null&&nation.getCapital()!=null&&nation.getKing()!=null&&nation.getKing().getUUID().equals(player.getUniqueId()),LocalText.tr("Державний морський прапор затверджує правитель держави."));
        return nation.getUUID().toString();
    }
    Map<String,Object> inspectDesign(Player p,String source)throws Exception {authority(p);return nativeCall("design",Map.of("player",p.getUniqueId().toString(),"source",source));}
    String designStamp(Player p,String source)throws Exception {return authority(p)+"|"+inspectDesign(p,source).get("hash")+"|"+db.one("SELECT revision FROM naval_ensigns WHERE nation=?",authority(p));}
    void createDesign(Player p,String source)throws Exception {
        String nation=authority(p);var design=inspectDesign(p,source);
        db.atomic(()->{var old=db.one("SELECT revision FROM naval_ensigns WHERE nation=?",nation);long revision=old==null?1:number(old,"revision")+1;
            db.execute("MERGE INTO naval_ensigns KEY(nation) VALUES(?,?,?,?,?,?)",nation,design.get("kind"),design.get("payload"),design.get("hash"),revision,p.getUniqueId().toString());
            fleet.fleet.s.audit(new FeudalService.Actor(p.getUniqueId().toString(),false),"NAVAL_ENSIGN",nation+" "+design.get("hash"));return null;});
    }
}
