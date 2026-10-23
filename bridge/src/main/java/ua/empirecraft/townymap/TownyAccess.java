package ua.empirecraft.townymap;

import net.minecraft.server.level.ServerLevel;
import java.lang.reflect.Method;
import java.util.*;

/** Uses Towny's own plugin classloader on Mohist, without bundling Bukkit or Towny classes. */
final class TownyAccess {
    private final Class<?> bukkit;
    private final Object plugin;
    private final Object api;
    private final Class<?> settings;
    private final Map<Class<?>, Map<String, Method>> methods = new HashMap<>();

    TownyAccess() throws ReflectiveOperationException {
        bukkit = Class.forName("org.bukkit.Bukkit");
        Object manager = bukkit.getMethod("getPluginManager").invoke(null);
        plugin = Class.forName("org.bukkit.plugin.PluginManager")
                .getMethod("getPlugin", String.class).invoke(manager, "Towny");
        if (plugin == null || !enabled()) throw new IllegalStateException("Towny is not enabled");
        ClassLoader loader = plugin.getClass().getClassLoader();
        api = Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, loader)
                .getMethod("getInstance").invoke(null);
        settings = Class.forName("com.palmergames.bukkit.towny.TownySettings", true, loader);
    }

    boolean enabled() throws ReflectiveOperationException {
        return (boolean) Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(plugin);
    }

    Map<String, Claims> snapshot(boolean independentTowns) throws ReflectiveOperationException {
        if (!enabled()) throw new IllegalStateException("Towny is not enabled");
        int size = ((Number) settings.getMethod("getTownBlockSize").invoke(null)).intValue();
        if (size < 1 || size > 1024) throw new IllegalStateException("Invalid Towny town block size: " + size);
        Map<String, MutableClaims> groups = new TreeMap<>();
        Map<String, String> dimensions = new HashMap<>();
        Object manager=bukkit.getMethod("getPluginManager").invoke(null);
        Object feudal=Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"EmpirecraftFeudal");
        Map<String,Map<String,Object>> estates=new HashMap<>();
        List<Map<String,Object>> points=new ArrayList<>();
        if(feudal!=null && (boolean)Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(feudal)) {
            // Optional bridge protocol: old Feudal versions keep the original nation-only map.
            try {
                for(Object raw:(List<?>)feudal.getClass().getMethod("mapEstates").invoke(feudal)) {
                    @SuppressWarnings("unchecked") Map<String,Object> row=(Map<String,Object>)raw;estates.put(row.get("id").toString(),row);
                }
                for(Object raw:(List<?>)feudal.getClass().getMethod("mapPoints").invoke(feudal)) {
                    @SuppressWarnings("unchecked") Map<String,Object> row=(Map<String,Object>)raw;points.add(row);
                }
            } catch(NoSuchMethodException ignored) {estates.clear();points.clear();}
        }
        for (Object town : (Collection<?>) call(api, "getTowns")) {
            Object nation = call(town, "getNationOrNull");
            if (nation == null && !independentTowns) continue;
            Object owner = nation == null ? town : nation;
            String ownerId = (nation == null ? "town/" : "nation/") + call(owner, "getUUID");
            String name = call(owner, "getName").toString().replace('_', ' ');
            int color = mapColor(call(owner, "getMapColorHexCode"), ownerId);
            String townId="t:"+call(town,"getUUID"),nationId=nation==null?null:"n:"+call(nation,"getUUID");
            Map<String,Object> ownerData=estates.get(nation==null?townId:nationId);
            String detail=ownerData==null?name:ownerData.get("detail").toString();
            for (Object block : (Collection<?>) call(town, "getTownBlocks")) {
                String worldName = call(call(block, "getWorld"), "getName").toString();
                String dimension = dimensions.get(worldName);
                if (dimension == null) {
                    Object world = bukkit.getMethod("getWorld", String.class).invoke(null, worldName);
                    if (world == null) continue;
                    Object handle = call(world, "getHandle");
                    if (!(handle instanceof ServerLevel level))
                        throw new IllegalStateException("Bukkit world does not expose a Mohist ServerLevel: " + worldName);
                    dimension = level.dimension().location().toString();
                    dimensions.put(worldName, dimension);
                }
                String id = ownerId + "/" + dimension;
                MutableClaims group = groups.get(id);
                if (group == null) {
                    group = new MutableClaims(id, name, dimension, color, size,detail,0);
                    groups.put(id, group);
                }
                var cell=new ClaimGeometry.Cell(((Number)call(block,"getX")).intValue(),((Number)call(block,"getZ")).intValue());
                group.cells.add(cell);
                Set<String> visited=new HashSet<>();String cursor=townId;
                while(cursor!=null && !cursor.isBlank() && visited.add(cursor) && visited.size()<=16) {
                    Map<String,Object> data=estates.get(cursor);if(data==null || "KINGDOM".equals(data.get("kind")))break;
                    int layer=4-((Number)data.get("level")).intValue();String regionalId="estate/"+cursor+"/"+dimension;
                    MutableClaims regional=groups.get(regionalId);
                    if(regional==null){String title=switch(data.get("kind").toString()){case "DUCHY"->"Герцогство ";case "COUNTY"->"Графство ";default->"Місто ";};regional=new MutableClaims(regionalId,title+data.get("name"),dimension,layer==3?0xaaaaaa:mapColor(null,cursor),size,data.get("detail").toString(),layer);groups.put(regionalId,regional);}
                    regional.cells.add(cell);cursor=data.get("parent").toString();
                }
            }
            if(nation!=null && town.equals(call(nation,"getCapital"))) {
                try{Object spawn=call(town,"getSpawn");String world=call(call(spawn,"getWorld"),"getName").toString();String dim=dimensions.get(world);
                    if(dim!=null)addPoint(groups,"capital/"+nationId,"★ Столиця: "+name,dim,((Number)call(spawn,"getBlockX")).intValue(),((Number)call(spawn,"getBlockZ")).intValue(),detail,0xffd700);
                }catch(java.lang.reflect.InvocationTargetException ignored){/* Towny permits towns without a saved spawn. */}
            }
        }
        for(var p:points){String world=p.get("world").toString(),dim=dimensions.get(world);if(dim==null)continue;Map<String,Object> data=estates.get(p.get("estate").toString());if(data==null)continue;
            int x=((Number)p.get("x")).intValue(),z=((Number)p.get("z")).intValue();
            // Reassigned land must not retain another lord's stale centre marker.
            Object bukkitWorld=bukkit.getMethod("getWorld",String.class).invoke(null,world);
            Object location=Class.forName("org.bukkit.Location").getConstructor(Class.forName("org.bukkit.World"),double.class,double.class,double.class).newInstance(bukkitWorld,(double)x,64d,(double)z);
            Object actualTown=api.getClass().getMethod("getTown",Class.forName("org.bukkit.Location")).invoke(api,location);if(actualTown==null)continue;
            String cursor="t:"+call(actualTown,"getUUID");boolean owns=false;Set<String> seen=new HashSet<>();while(cursor!=null && !cursor.isBlank() && seen.add(cursor) && seen.size()<=16){if(cursor.equals(p.get("estate")))owns=true;var row=estates.get(cursor);cursor=row==null?null:row.get("parent").toString();}if(!owns)continue;
            boolean trade="trade".equals(p.get("kind"));addPoint(groups,"point/"+p.get("estate")+"/"+p.get("kind"),(trade?"◆ Торговий центр: ":"★ Столиця: ")+p.get("name"),dim,x,z,data.get("detail").toString(),trade?0x00e6aa:0xffd700);
        }
        Map<String, Claims> snapshot = new TreeMap<>();
        groups.forEach((id, g) -> snapshot.put(id, new Claims(g.id, g.name, g.dimension, g.color, g.size, g.cells,g.detail,g.layer)));
        return Map.copyOf(snapshot);
    }

    private static void addPoint(Map<String,MutableClaims> groups,String id,String name,String dimension,int x,int z,String detail,int color){
        String key=id+"/"+dimension;var marker=new MutableClaims(key,name,dimension,color,1,detail,4);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)marker.cells.add(new ClaimGeometry.Cell(x+dx,z+dz));groups.put(key,marker);
    }

    private Object call(Object target, String name) throws ReflectiveOperationException {
        Map<String, Method> cached = methods.computeIfAbsent(target.getClass(), c -> new HashMap<>());
        Method method = cached.get(name);
        if (method == null) {
            method = target.getClass().getMethod(name);
            cached.put(name, method);
        }
        return method.invoke(target);
    }

    static int mapColor(Object raw, String id) {
        if (raw != null) {
            String value = raw.toString().trim().replaceFirst("^(#|0[xX])", "");
            if (value.matches("[0-9a-fA-F]{6}")) return Integer.parseInt(value, 16);
        }
        return java.awt.Color.HSBtoRGB((id.hashCode() & 0x7fffffff) / (float) Integer.MAX_VALUE, 0.65f, 0.9f) & 0xffffff;
    }

    private static final class MutableClaims {
        final String id, name, dimension;
        final int color, size;
        final Set<ClaimGeometry.Cell> cells = new HashSet<>();
        final String detail;final int layer;
        MutableClaims(String id, String name, String dimension, int color, int size,String detail,int layer) {
            this.id = id; this.name = name; this.dimension = dimension; this.color = color; this.size = size;
            this.detail=detail;this.layer=layer;
        }
    }
}

