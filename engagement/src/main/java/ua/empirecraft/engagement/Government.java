package ua.empirecraft.engagement;

import com.quintonc.vs_sails.protection.FleetNative;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** Main-thread boundary. Read-only diplomacy is cached for one tick, never guessed from scoreboards. */
public final class Government {
    private record Cached(long tick,Map<String,Object> data){}
    private static final Map<String,Cached> cache=new HashMap<>();
    private static long generation=Long.MIN_VALUE;
    private Government(){}
    @SuppressWarnings("unchecked") public static Map<String,Object> call(String action,Map<String,Object> data){
        try {
            Object plugin=FleetNative.plugin();if(plugin==null)throw new IllegalStateException("Feudal is unavailable");
            return (Map<String,Object>)plugin.getClass().getMethod("engagementRequest",String.class,Map.class).invoke(plugin,action,data);
        }catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("Towny combat authority is unavailable",error);}
    }
    public static Map<String,Object> read(ServerLevel level,String action,Map<String,Object> data){
        long tick=level.getServer().overworld().getGameTime();if(generation!=tick){cache.clear();generation=tick;}
        String key=action+data;var cached=cache.get(key);if(cached!=null)return cached.data;
        var result=call(action,data);if(cache.size()<16384)cache.put(key,new Cached(tick,result));return result;
    }
    public static String town(ServerLevel level,UUID person){return person==null?"":Objects.toString(read(level,"resident",Map.of("player",person.toString())).get("town"),"");}
    public static String recruitTown(ServerLevel level,UUID unit,UUID commander){var data=read(level,"recruit-owner",Map.of("unit",unit.toString()));return Boolean.TRUE.equals(data.get("managed"))?Objects.toString(data.get("town"),""):town(level,commander);}
    public static String world(ServerLevel level){try{Object world=level.getClass().getMethod("getWorld").invoke(level);return world.getClass().getMethod("getUID").invoke(world).toString();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    public static CombatPolicy.Water point(ServerLevel level,String town,double x,double z){
        return CombatPolicy.Water.valueOf(read(level,"point",Map.of("town",town,"world",world(level),"x",x,"z",z)).get("water").toString());
    }
    public static boolean controls(ServerLevel level,UUID player,long ship){return player!=null&&Boolean.TRUE.equals(read(level,"controls",Map.of("player",player.toString(),"ship",ship)).get("allow"));}
    public static boolean crew(ServerLevel level,UUID player,long ship){return player!=null&&Boolean.TRUE.equals(read(level,"controls",Map.of("player",player.toString(),"ship",ship)).get("crew"));}
    public static void clear(){cache.clear();generation=Long.MIN_VALUE;}
}
