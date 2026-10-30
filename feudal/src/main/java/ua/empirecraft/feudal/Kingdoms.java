package ua.empirecraft.feudal;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.event.nation.*;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import static ua.empirecraft.feudal.Database.*;
import static ua.empirecraft.feudal.Model.*;

/** Native nations are a materialized view of the signed feudal tree, not a second membership system.
 * Legacy nation IDs remain political identities so treaties, wallets and invoices keep their IDs.
 * The durable registry is written BEFORE Towny changes, allowing interrupted reconciliation to resume.
 */
final class Kingdoms implements Listener {
    final FeudalPlugin plugin;final FeudalService s;final Database db;
    boolean applying;
    Kingdoms(FeudalPlugin plugin,FeudalService service)throws SQLException {
        this.plugin=plugin;s=service;db=s.db;
        db.execute("CREATE TABLE IF NOT EXISTS kingdom_seats(authority VARCHAR(80) PRIMARY KEY,capital VARCHAR(80),native_id VARCHAR(36) UNIQUE,name VARCHAR(64))");
        db.execute("CREATE TABLE IF NOT EXISTS kingdom_members(town VARCHAR(80) PRIMARY KEY,identity VARCHAR(80))");
        db.execute("CREATE TABLE IF NOT EXISTS kingdom_settings(id VARCHAR(32) PRIMARY KEY)");
        s.derivedKingdoms=db.one("SELECT id FROM kingdom_settings WHERE id='imported'")!=null;
    }
    private Town town(String id){return TownyAPI.getInstance().getTown(UUID.fromString(id.substring(2)));}
    private List<Map<String,Object>> seats()throws SQLException{return db.query("SELECT * FROM kingdom_seats ORDER BY authority");}
    Snapshot snapshot(){
        require(Bukkit.isPrimaryThread(),"Towny kingdoms require the server thread");
        try{
            var api=TownyAPI.getInstance();
            // Import pre-existing nations once. Projected nations never become a new political identity.
            for(Nation n:List.copyOf(api.getNations()))if(!s.derivedKingdoms && n.hasCapital() && db.one("SELECT authority FROM kingdom_seats WHERE native_id=?",n.getUUID().toString())==null)
                db.execute("INSERT INTO kingdom_seats VALUES(?,?,?,?)",TownyBridge.nationId(n),TownyBridge.townId(n.getCapital()),n.getUUID().toString(),n.getName());
            for(Town t:api.getTowns())if(db.one("SELECT town FROM kingdom_members WHERE town=?",TownyBridge.townId(t))==null){
                Nation n=t.getNationOrNull();var seat=n==null?null:db.one("SELECT authority FROM kingdom_seats WHERE native_id=?",n.getUUID().toString());
                String identity=seat==null?null:string(seat,"authority");
                if(identity!=null && !identity.startsWith("n:"))identity=null;
                db.execute("INSERT INTO kingdom_members VALUES(?,?)",TownyBridge.townId(t),identity);
            }
            var estates=new ArrayList<Estate>();var citizens=new HashSet<String>();s.kingdomCapitals.clear();
            for(var seat:seats()){
                String id=string(seat,"authority"),capital=string(seat,"capital");Town t=town(capital);if(t==null)continue;
                s.kingdomCapitals.put(id,capital);
                if(id.startsWith("n:"))estates.add(new Estate(id,Kind.KINGDOM,string(seat,"name"),id,t.getMayor().getUUID().toString(),true));
            }
            for(Town t:api.getTowns()){
                String id=TownyBridge.townId(t),identity=string(db.one("SELECT identity FROM kingdom_members WHERE town=?",id),"identity");
                if(identity!=null && !s.kingdomCapitals.containsKey(identity))identity=null;
                estates.add(new Estate(id,Kind.BARONY,t.getName(),identity,t.getMayor().getUUID().toString(),true));
                for(Resident r:t.getResidents()){
                    if(identity!=null)citizens.add(r.getUUID()+"@"+identity);
                    // The sovereign crown includes citizens of all vassals, including legacy sub-crowns.
                    var old=db.one("SELECT id FROM estates WHERE id=?",id);
                    if(old!=null){String root=root(id);citizens.add(r.getUUID()+"@"+root);}
                }
            }
            return new Snapshot(estates,citizens);
        }catch(SQLException ex){throw new IllegalStateException("Не вдалося прочитати склад королівств",ex);}
    }
    void finishImport()throws SQLException {
        if(!s.derivedKingdoms){db.execute("MERGE INTO kingdom_settings KEY(id) VALUES('imported')");s.derivedKingdoms=true;}
    }
    String root(String id)throws SQLException {return KingdomTree.root(s,id);}
    Nation nativeNation(String id)throws SQLException {
        var row=db.one("SELECT native_id FROM kingdom_seats WHERE authority=?",root(id));
        return row==null?null:TownyAPI.getInstance().getNation(UUID.fromString(string(row,"native_id")));
    }
    Town capital(String id)throws SQLException {
        var row=db.one("SELECT capital FROM kingdom_seats WHERE authority=?",id);
        return row==null?null:town(string(row,"capital"));
    }
    void reconcile(){
        if(applying)return;
        try{
            var api=TownyAPI.getInstance();var groups=new TreeMap<String,List<Town>>();
            for(Town t:List.copyOf(api.getTowns()))groups.computeIfAbsent(root(TownyBridge.townId(t)),k->new ArrayList<>()).add(t);
            // Validate the WHOLE plan before touching native membership.
            for(var entry:groups.entrySet()){
                String root=entry.getKey();var seat=db.one("SELECT * FROM kingdom_seats WHERE authority=?",root);
                String capital=seat==null?(root.startsWith("t:")?root:null):string(seat,"capital");
                if(capital==null){
                    var row=db.one("SELECT capital FROM political_states WHERE estate=?",root);capital=row==null?null:string(row,"capital");
                    if(capital==null)capital=TownyBridge.townId(entry.getValue().stream().filter(t->t.getMayor().getUUID().toString().equals(lord(root))).findFirst().orElseThrow(()->new IllegalArgumentException("Володінню потрібна столиця: "+root)));
                }
                final String selected=capital;
                require(entry.getValue().stream().anyMatch(t->TownyBridge.townId(t).equals(selected)),"Столиця має належати власному королівству: "+root);
                if(seat==null){
                    UUID uuid=UUID.nameUUIDFromBytes(("empirecraft:kingdom:"+root).getBytes(StandardCharsets.UTF_8));
                    String name=town(capital).getName();
                    if(TownyUniverse.getInstance().hasNation(name))name="K_"+uuid.toString().replace("-","").substring(0,12);
                    db.execute("INSERT INTO kingdom_seats VALUES(?,?,?,?)",root,capital,uuid.toString(),name);
                }
            }
            applying=true;
            var targets=new HashMap<Town,UUID>();
            for(var entry:groups.entrySet()){
                var seat=db.one("SELECT * FROM kingdom_seats WHERE authority=?",entry.getKey());UUID uuid=UUID.fromString(string(seat,"native_id"));
                entry.getValue().forEach(t->targets.put(t,uuid));
            }
            // Detach before creating destinations; a former capital can now be an ordinary vassal town.
            for(var entry:targets.entrySet())if(entry.getKey().hasNation() && !entry.getKey().getNationOrNull().getUUID().equals(entry.getValue()))entry.getKey().removeNation();
            for(var entry:groups.entrySet()){
                var seat=db.one("SELECT * FROM kingdom_seats WHERE authority=?",entry.getKey());UUID uuid=UUID.fromString(string(seat,"native_id"));Nation n=api.getNation(uuid);boolean changed=false;Town capital=town(string(seat,"capital"));
                if(n==null){
                    changed=true;
                    TownyUniverse.getInstance().getDataSource().newNation(string(seat,"name"),uuid);n=api.getNation(uuid);
                    require(n!=null,"Towny не створив королівство.");
                    n.setRegistered(System.currentTimeMillis());n.setMapColorHexCode(TownySettings.getDefaultNationMapColor());
                    capital.setNation(n);n.setCapital(capital);n.setSpawn(capital.getSpawnOrNull());capital.save();n.save();
                    Bukkit.getPluginManager().callEvent(new NewNationEvent(n));
                }
                for(Town t:entry.getValue())if(t.getNationOrNull()!=n){changed=true;if(t.hasNation())t.removeNation();t.setNation(n);t.save();}
                if(n.getCapital()!=capital){changed=true;n.setCapital(capital);n.setSpawn(capital.getSpawnOrNull());}
                if(changed)n.save();s.kingdomCapitals.put(entry.getKey(),string(seat,"capital"));
            }
        }catch(Exception ex){throw new IllegalStateException("Синхронізація королівств перервана; план збережено для повтору",ex);}
        finally{applying=false;}
    }
    private String lord(String id){try{return s.estate(id).lord();}catch(SQLException ex){throw new IllegalStateException(ex);}}
    private void derived(CancellableTownyEvent e){if(!applying){e.setCancelled(true);e.setCancelMessage("Королівство — столиця та її васали. Склад змінюється через /t vassal, створення /n new більше не потрібне.");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void create(PreNewNationEvent e){derived(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void join(NationPreAddTownEvent e){derived(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void leave(NationPreTownLeaveEvent e){derived(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void kick(NationPreTownKickEvent e){derived(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void merge(NationPreMergeEvent e){derived(e);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void delete(PreDeleteNationEvent e){if(e.getCause()!=DeleteNationEvent.Cause.NO_TOWNS)derived(e);}
}
