package ua.empirecraft.feudal;

import java.util.*;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.WorldCoord;
import static ua.empirecraft.feudal.Model.*;
import static ua.empirecraft.feudal.Database.*;

/** Main-thread Towny/native adapter. Never loads world chunks to inspect a hull or a claim. */
final class FleetWorld implements FleetService.World {
    final FeudalService s;final PoliticsService politics;final Supplier<ArmyPolicy.View> diplomacy;
    private Class<?> nativeApi;
    FleetWorld(FeudalService s,PoliticsService politics,Supplier<ArmyPolicy.View> diplomacy){this.s=s;this.politics=politics;this.diplomacy=diplomacy;}
    @SuppressWarnings("unchecked") Map<String,Object> call(String action,Map<String,Object> data){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Fleet needs the main thread");
        try{
            if(nativeApi==null){Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());nativeApi=Class.forName("com.quintonc.vs_sails.protection.FleetNative",true,server.getClass().getClassLoader());}
            return (Map<String,Object>)nativeApi.getMethod("request",String.class,Map.class).invoke(null,action,data);
        }catch(Exception ex){Throwable cause=ex instanceof java.lang.reflect.InvocationTargetException?ex.getCause():ex;throw new IllegalStateException("Корабельна система недоступна: "+cause.getMessage(),cause);}
    }
    Map<String,Object> raw(long id){return call("snapshot",Map.of("ship",id));}
    @SuppressWarnings("unchecked") List<Map<String,Object>> all(){return (List<Map<String,Object>>)(List<?>)call("list",Map.of()).getOrDefault("ships",List.of());}
    static FleetService.Hull hull(Map<String,Object> row){
        if(row.isEmpty())return null;
        var points=new ArrayList<FleetGeometry.Point>();for(Object raw:(List<?>)row.get("footprint")){var p=(List<?>)raw;points.add(new FleetGeometry.Point(((Number)p.get(0)).doubleValue(),((Number)p.get(1)).doubleValue()));}
        return new FleetService.Hull(((Number)row.get("id")).longValue(),row.get("world").toString(),FleetGeometry.hull(points),((Number)row.get("x")).doubleValue(),((Number)row.get("y")).doubleValue(),((Number)row.get("z")).doubleValue(),row.get("town").toString(),row.get("name").toString(),row.get("information").toString(),Boolean.TRUE.equals(row.get("loaded")));
    }
    @Override public FleetService.Hull hull(long id){return hull(raw(id));}
    @Override public boolean combat(long id){
        try{
            Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            var api=Class.forName("ua.empirecraft.engagement.EngagementNative",true,server.getClass().getClassLoader());
            var answer=(Map<?,?>)api.getMethod("request",String.class,Map.class).invoke(null,"combat",Map.of("ship",id));
            return Boolean.TRUE.equals(answer.get("active"));
        }catch(ClassNotFoundException absent){return false;}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Бойова система тимчасово недоступна.",error);}
    }
    static Town town(String id){try{return id==null||!id.startsWith("t:")?null:TownyAPI.getInstance().getTown(UUID.fromString(id.substring(2)));}catch(IllegalArgumentException ex){return null;}}
    @Override public String hometown(String player){var r=TownyAPI.getInstance().getResident(UUID.fromString(player));var t=r==null?null:r.getTownOrNull();return t==null?null:TownyBridge.townId(t);}
    @Override public boolean person(String id){try{return TownyAPI.getInstance().getResident(UUID.fromString(id))!=null;}catch(IllegalArgumentException ex){return false;}}
    @Override public boolean mayor(String person,String town){var t=town(town);return t!=null && t.getMayor()!=null && t.getMayor().getUUID().toString().equals(person);}
    @Override public boolean direct(String seller,String buyer){var t=town(seller);var p=Bukkit.getPlayer(UUID.fromString(buyer));var q=t==null||t.getMayor()==null?null:Bukkit.getPlayer(t.getMayor().getUUID());return p!=null&&q!=null&&p.getWorld()==q.getWorld()&&p.getLocation().distanceSquared(q.getLocation())<=36;}
    private ArmyPolicy.View view(){var view=diplomacy.get();require(view!=null && System.currentTimeMillis()-view.updated()<5000,"Дані дипломатії оновлюються. Спробуйте ще раз.");return view;}
    @Override public boolean allied(String a,String b){if(Objects.equals(a,b)||view().friendly(a,b))return true;var left=town(a);var right=town(b);return left!=null&&right!=null&&left.isAlliedWith(right);}
    @Override public boolean war(String a,String b){
        if(a==null || a.isEmpty() || b==null || b.isEmpty())return false;if(view().related(view().wars(),a,b))return true;
        var left=town(a);var right=town(b);var n1=left==null?null:left.getNationOrNull();var n2=right==null?null:right.getNationOrNull();
        return n1!=null && n2!=null && (n1.hasEnemy(n2)||n2.hasEnemy(n1));
    }
    @Override public boolean republic(String town){
        try{Set<String> seen=new HashSet<>();String at=town;
            while(at!=null && seen.add(at)){
                if(s.db.one("SELECT estate FROM political_states WHERE estate=?",at)!=null){var state=politics.state(at);if(state.stage()==PoliticalModel.Stage.TRADE_REPUBLIC && state.government()==PoliticalModel.Government.REPUBLIC)return true;}
                at=s.parent(at);
            }return false;
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private String claim(org.bukkit.World world,int x,int z){var t=new WorldCoord(world,x,z).getTownOrNull();return t==null?null:TownyBridge.townId(t);}
    @Override public Set<String> territories(FleetService.Hull hull){
        var world=Bukkit.getWorld(UUID.fromString(hull.world()));if(world==null)return Set.of();int size=TownySettings.getTownBlockSize();var polygon=hull.footprint();
        double ax=polygon.stream().mapToDouble(FleetGeometry.Point::x).min().orElse(0),bx=polygon.stream().mapToDouble(FleetGeometry.Point::x).max().orElse(0),az=polygon.stream().mapToDouble(FleetGeometry.Point::z).min().orElse(0),bz=polygon.stream().mapToDouble(FleetGeometry.Point::z).max().orElse(0);
        int x1=(int)Math.floor(ax/size),x2=(int)Math.floor(Math.nextDown(bx)/size),z1=(int)Math.floor(az/size),z2=(int)Math.floor(Math.nextDown(bz)/size);
        require((long)(x2-x1+1)*(z2-z1+1)<=4096,"Корпус завеликий для перевірки приватів.");Set<String> result=new HashSet<>();
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++)if(!FleetGeometry.intersection(polygon,(double)x*size,(double)z*size,(double)(x+1)*size,(double)(z+1)*size).isEmpty()){
            String town=claim(world,x,z);if(town!=null)result.add(town);
        }return result;
    }
    @Override public boolean docked(FleetService.Hull hull,String town,CityMarketService.Place counter){
        var world=Bukkit.getWorld(UUID.fromString(hull.world()));return world!=null && FleetGeometry.docked(hull.footprint(),counter.x(),counter.z(),TownySettings.getTownBlockSize(),200,(x,z)->town.equals(claim(world,x,z)));
    }
    boolean visible(UUID player,long ship){return Boolean.TRUE.equals(call("visible",Map.of("ship",ship,"player",player.toString())).get("visible"));}
    @Override public boolean visible(String player,FleetService.Hull hull){return visible(UUID.fromString(player),hull.id());}
    @Override public boolean empty(String player,Map<String,Object> old){
        try{return Boolean.TRUE.equals(Class.forName("ua.empirecraft.townymap.FleetObservationNative").getMethod("empty",Map.class).invoke(null,
            Map.of("player",player,"ship",number(old,"ship"),"world",string(old,"world"),"x",old.get("x"),"y",old.get("y"),"z",old.get("z"))));
        }catch(ReflectiveOperationException|LinkageError unavailable){return false;}
    }
}
