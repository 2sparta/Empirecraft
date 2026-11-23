package ua.empirecraft.townymap;

import net.minecraft.server.level.ServerLevel;
import java.lang.reflect.Method;
import java.util.*;

/** Uses Towny's own plugin classloader on Mohist, without bundling Bukkit or Towny classes. */
final class TownyAccess {
    private static final com.google.gson.Gson JSON = new com.google.gson.Gson();
    private final Class<?> bukkit;
    private final Object plugin;
    private final Object api;
    private final Class<?> settings;
    private final Method nativeSnapshot;
    private final Map<Class<?>, Map<String, Method>> methods = new HashMap<>();
    private Map<String,Map<String,Object>> batchEstates;
    private List<Map<String,Object>> batchPoints;
    private boolean batch;

    Map<MapMode,Map<String,Claims>> snapshotAll(boolean independentTowns) throws ReflectiveOperationException, NotReadyException {
        batch=true;batchEstates=null;batchPoints=null;
        try {
            var result=new EnumMap<MapMode,Map<String,Claims>>(MapMode.class);
            for(var mode:MapMode.values())result.put(mode,snapshot(independentTowns,mode));
            return Map.copyOf(result);
        } finally {batch=false;batchEstates=null;batchPoints=null;}
    }

    TownyAccess() throws ReflectiveOperationException, NotReadyException {
        Method provider;
        try {
            provider = Class.forName("ua.empirecraft.townyforge.TownyForge").getMethod("integrationSnapshot");
        } catch (ClassNotFoundException absent) {
            provider = null;
        }
        nativeSnapshot = provider;
        if (nativeSnapshot != null) {
            bukkit = settings = null;
            plugin = api = null;
            return;
        }
        bukkit = Class.forName("org.bukkit.Bukkit");
        plugin = readyPlugin();
        ClassLoader loader = plugin.getClass().getClassLoader();
        api = Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, loader)
                .getMethod("getInstance").invoke(null);
        if (api == null) throw new NotReadyException("Towny API is still initialising");
        settings = Class.forName("com.palmergames.bukkit.towny.TownySettings", true, loader);
    }

    private Object readyPlugin() throws ReflectiveOperationException, NotReadyException {
        // Forge can start before Mohist has created Bukkit or enabled its plugins.
        if (bukkit.getMethod("getServer").invoke(null) == null)
            throw new NotReadyException("Bukkit server is still initialising");
        Object manager = bukkit.getMethod("getPluginManager").invoke(null);
        if (manager == null) throw new NotReadyException("Bukkit plugin manager is still initialising");
        Object current = Class.forName("org.bukkit.plugin.PluginManager")
                .getMethod("getPlugin", String.class).invoke(manager, "Towny");
        if (current == null) throw new NotReadyException("Towny is not registered yet");
        if (!(boolean) Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(current))
            throw new NotReadyException("Towny is registered but not enabled");
        return current;
    }

    Map<String, Claims> snapshot(boolean independentTowns) throws ReflectiveOperationException, NotReadyException {
        return snapshot(independentTowns,MapMode.HIERARCHY);
    }
    Map<String, Claims> snapshot(boolean independentTowns,MapMode mode) throws ReflectiveOperationException, NotReadyException {
        if (mode.environmental()) return Map.of();
        if (nativeSnapshot != null) return forgeSnapshot(independentTowns, mode);
        if (readyPlugin() != plugin)
            throw new NotReadyException("Towny plugin instance changed; reconnecting");
        int size = ((Number) settings.getMethod("getTownBlockSize").invoke(null)).intValue();
        if (size < 1 || size > 1024) throw new IllegalStateException("Invalid Towny town block size: " + size);
        Map<String, MutableClaims> groups = new TreeMap<>();
        Map<String, String> dimensions = new HashMap<>();
        Object manager=bukkit.getMethod("getPluginManager").invoke(null);
        Object feudal=Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"EmpirecraftFeudal");
        Map<String,Map<String,Object>> estates=new HashMap<>();
        List<Map<String,Object>> points=new ArrayList<>();
        if(batch && batchEstates!=null) {estates.putAll(batchEstates);points.addAll(batchPoints);}
        else if(feudal!=null && (boolean)Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(feudal)) {
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
        if(batch && batchEstates==null){batchEstates=Map.copyOf(estates);batchPoints=List.copyOf(points);}
        for (Object town : (Collection<?>) call(api, "getTowns")) {
            Object nation = call(town, "getNationOrNull");
            if (nation == null && !independentTowns) continue;
            String banditNation = banditNation(nation);
            Object owner = nation == null || mode==MapMode.TOWNS ? town : nation;
            String ownerId = mode.name()+"/"+(nation == null || mode==MapMode.TOWNS ? "town/" : "nation/") + call(owner, "getUUID");
            String name = call(owner, "getName").toString().replace('_', ' ');
            int color = mapColor(call(owner, "getMapColorHexCode"), ownerId);
            String townId="t:"+call(town,"getUUID"),nationId=nation==null?null:"n:"+call(nation,"getUUID");
            Map<String,Object> ownerData=estates.get(nation==null || mode==MapMode.TOWNS?townId:nationId);
            if (ownerData == null && nation != null && mode != MapMode.TOWNS) {
                Object capital = call(nation,"getCapital");
                if (capital != null) ownerData = estates.get("t:" + call(capital,"getUUID"));
            }
            if (mode == MapMode.PROVINCES) {
                Map<String,Object> province = estates.get(townId);
                Set<String> seen = new HashSet<>(); String cursor = townId;
                while (cursor != null && !cursor.isBlank() && seen.add(cursor) && seen.size() <= 16) {
                    var row = estates.get(cursor); if (row == null) break;
                    if ("COUNTY".equals(row.get("kind"))) { province = row; break; }
                    cursor = Objects.toString(row.get("parent"), "");
                }
                ownerData = province;
                ownerId = "PROVINCES/" + (province == null ? townId : province.get("id"));
                name = (province != null && "COUNTY".equals(province.get("kind")) ? "Графство " : "Місто ")
                        + Objects.toString(province == null ? call(town,"getName") : province.get("name")).replace('_',' ');
                color = mapColor(null,ownerId);
            }
            String profile = profile(ownerData);
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
                    group = new MutableClaims(id, name, dimension, color, size,detail,mode==MapMode.TRADE?1:0,banditNation);
                    groups.put(id, group);
                    group.profile = profile;
                }
                var cell=new ClaimGeometry.Cell(((Number)call(block,"getX")).intValue(),((Number)call(block,"getZ")).intValue());
                group.cells.add(cell);
                Set<String> visited=new HashSet<>();String cursor=townId;
                while(mode==MapMode.HIERARCHY && cursor!=null && !cursor.isBlank() && visited.add(cursor) && visited.size()<=16) {
                    Map<String,Object> data=estates.get(cursor);if(data==null || "KINGDOM".equals(data.get("kind")))break;
                    int layer=4-((Number)data.get("level")).intValue();String regionalId="estate/"+cursor+"/"+dimension+(banditNation.isEmpty()?"":"/bandit/"+banditNation);
                    MutableClaims regional=groups.get(regionalId);
                    if(regional==null){String title=switch(data.get("kind").toString()){case "DUCHY"->"Герцогство ";case "COUNTY"->"Графство ";default->"Місто ";};regional=new MutableClaims(regionalId,title+data.get("name"),dimension,layer==3?0xaaaaaa:mapColor(null,cursor),size,data.get("detail").toString(),layer,banditNation);groups.put(regionalId,regional);}
                    regional.cells.add(cell);cursor=data.get("parent").toString();
                    regional.profile = profile(data);
                }
            }
            if(mode!=MapMode.TOWNS && mode!=MapMode.PROVINCES && nation!=null && town.equals(call(nation,"getCapital"))) {
                try{Object spawn=call(town,"getSpawn");String world=call(call(spawn,"getWorld"),"getName").toString();String dim=dimensions.get(world);
                    if(dim!=null)addPoint(groups,"capital/"+nationId,"★ Столиця: "+name,dim,((Number)call(spawn,"getBlockX")).intValue(),((Number)call(spawn,"getBlockZ")).intValue(),detail,0xffd700,banditNation);
                }catch(java.lang.reflect.InvocationTargetException ignored){/* Towny permits towns without a saved spawn. */}
            }
        }
        for(var p:points){if(mode==MapMode.TOWNS || mode==MapMode.KINGDOMS || mode==MapMode.TRADE && !"trade".equals(p.get("kind")))continue;String world=p.get("world").toString(),dim=dimensions.get(world);if(dim==null)continue;Map<String,Object> data=estates.get(p.get("estate").toString());if(data==null)continue;
            int x=((Number)p.get("x")).intValue(),z=((Number)p.get("z")).intValue();
            // Reassigned land must not retain another lord's stale centre marker.
            Object bukkitWorld=bukkit.getMethod("getWorld",String.class).invoke(null,world);
            Object location=Class.forName("org.bukkit.Location").getConstructor(Class.forName("org.bukkit.World"),double.class,double.class,double.class).newInstance(bukkitWorld,(double)x,64d,(double)z);
            Object actualTown=api.getClass().getMethod("getTown",Class.forName("org.bukkit.Location")).invoke(api,location);if(actualTown==null)continue;
            String cursor="t:"+call(actualTown,"getUUID");boolean owns=false;Set<String> seen=new HashSet<>();while(cursor!=null && !cursor.isBlank() && seen.add(cursor) && seen.size()<=16){if(cursor.equals(p.get("estate")))owns=true;var row=estates.get(cursor);cursor=row==null?null:row.get("parent").toString();}if(!owns)continue;
            String banditNation=banditNation(call(actualTown,"getNationOrNull"));
            boolean trade="trade".equals(p.get("kind"));addPoint(groups,"point/"+p.get("estate")+"/"+p.get("kind"),(trade?"◆ Торговий центр: ":"★ Столиця: ")+p.get("name"),dim,x,z,data.get("detail").toString(),trade?0x00e6aa:0xffd700,banditNation);
        }
        Map<String, Claims> snapshot = new TreeMap<>();
        groups.forEach((id, g) -> snapshot.put(id, new Claims(g.id, g.name, g.dimension, g.color, g.size, g.cells,g.detail,g.layer,g.banditNation,g.profile)));
        return Map.copyOf(snapshot);
    }

    private record ForgeTown(UUID id, String name, UUID nation, UUID mayor, String color, ForgeSpawn spawn) {}
    private record ForgeNation(UUID id, String name, UUID capital, String color) {}
    private record ForgeSpawn(String dimension, double x, double z) {}
    private record ForgeClaim(String dimension, int x, int z, UUID town) {}
    private record ForgeData(Map<UUID, ForgeTown> towns, Map<UUID, ForgeNation> nations, List<ForgeClaim> claims) {}
    private record Estate(String id, String name, String kind, String parent) {}
    private record Point(String estate, String kind, String world, int x, int z, String name) {}
    private record FeudalData(List<Estate> estates, List<Point> points) {}

    private String forgeJson;
    private ForgeData forgeData;
    private FeudalData feudalData;

    private Map<String, Claims> forgeSnapshot(boolean independentTowns, MapMode mode)
            throws ReflectiveOperationException, NotReadyException {
        String json = (String) nativeSnapshot.invoke(null);
        if (json == null) throw new NotReadyException("Native Towny is still initialising");
        if (forgeJson != json) {
            forgeData = JSON.fromJson(json, ForgeData.class);
            forgeJson = json;
        }
        if (feudalData == null) {
            String archived = (String) nativeSnapshot.getDeclaringClass().getMethod("feudalMap").invoke(null);
            feudalData = archived == null ? new FeudalData(List.of(), List.of()) : JSON.fromJson(archived, FeudalData.class);
        }
        var estates = new HashMap<String, Estate>();
        feudalData.estates().forEach(e -> estates.put(e.id(), e));
        var groups = new TreeMap<String, MutableClaims>();
        var occupied = new HashMap<String, ForgeClaim>();
        for (var claim : forgeData.claims()) {
            occupied.put(claim.dimension() + "/" + claim.x() + "/" + claim.z(), claim);
            var town = forgeData.towns().get(claim.town());
            var nation = town.nation() == null ? null : forgeData.nations().get(town.nation());
            if (nation == null && !independentTowns) continue;
            String bandit = nation != null && BanditNames.matches(nation.name()) ? nation.id().toString() : "";
            boolean byTown = nation == null || mode == MapMode.TOWNS || mode == MapMode.PROVINCES;
            String id = byTown ? "town/" + town.id() : "nation/" + nation.id();
            String title = byTown ? town.name() : nation.name();
            String color = byTown ? town.color() : nation.color();
            var lineage = lineage(estates, "t:" + town.id());
            if (mode == MapMode.PROVINCES) {
                Estate county = lineage.stream().filter(e -> "COUNTY".equals(e.kind())).findFirst().orElse(null);
                if (county != null) {
                    id = county.id();
                    title = "Графство " + county.name();
                    color = "";
                }
            }
            addForgeCell(groups, mode.name() + "/" + id, title, claim, color, mode == MapMode.TRADE ? 1 : 0, bandit);
            if (mode == MapMode.HIERARCHY) {
                for (Estate estate : lineage) {
                    int layer = switch (estate.kind()) {
                        case "DUCHY" -> 1;
                        case "COUNTY" -> 2;
                        case "BARONY" -> 3;
                        default -> 0;
                    };
                    if (layer > 0) addForgeCell(groups, "estate/" + estate.id(), estate.name(), claim, "", layer, bandit);
                }
            }
        }
        if (mode == MapMode.KINGDOMS || mode == MapMode.HIERARCHY || mode == MapMode.TRADE) {
            for (var nation : forgeData.nations().values()) {
                var town = forgeData.towns().get(nation.capital());
                if (town == null || town.spawn() == null) continue;
                var at = town.spawn();
                addPoint(groups, "capital/n:" + nation.id(), "★ Столиця: " + nation.name(), at.dimension(),
                        (int)Math.floor(at.x()), (int)Math.floor(at.z()), town.name(), 0xffd700,
                        BanditNames.matches(nation.name()) ? nation.id().toString() : "");
            }
        }
        for (Point point : feudalData.points()) {
            if (mode == MapMode.TOWNS || mode == MapMode.KINGDOMS || mode == MapMode.TRADE && !"trade".equals(point.kind())) continue;
            String dimension = switch (point.world()) {
                case "world", "world_europe" -> "minecraft:overworld";
                case "DIM1" -> "minecraft:the_end";
                case "DIM-1" -> "minecraft:the_nether";
                default -> point.world();
            };
            var claim = occupied.get(dimension + "/" + (point.x() >> 4) + "/" + (point.z() >> 4));
            if (claim == null || lineage(estates, "t:" + claim.town()).stream().noneMatch(e -> e.id().equals(point.estate()))) continue;
            boolean trade = "trade".equals(point.kind());
            addPoint(groups, "point/" + point.estate() + "/" + point.kind(),
                    (trade ? "◆ Торговий центр: " : "★ Столиця: ") + point.name(), dimension,
                    point.x(), point.z(), estates.get(point.estate()).name(), trade ? 0x00e6aa : 0xffd700, "");
        }
        var result = new TreeMap<String, Claims>();
        groups.forEach((id, group) -> result.put(id, new Claims(id, group.name, group.dimension, group.color, group.size,
                group.cells, group.detail, group.layer, group.banditNation)));
        return Map.copyOf(result);
    }

    private static List<Estate> lineage(Map<String, Estate> estates, String start) {
        List<Estate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id = start; seen.add(id) && seen.size() <= 16;) {
            Estate estate = estates.get(id);
            if (estate == null) break;
            result.add(estate);
            id = estate.parent();
        }
        return result;
    }

    private static void addForgeCell(Map<String, MutableClaims> groups, String id, String title, ForgeClaim cell,
                                     String color, int layer, String bandit) {
        String key = id + "/" + cell.dimension() + (bandit.isEmpty() ? "" : "/bandit/" + bandit);
        String name = title.replace('_', ' ');
        var group = groups.computeIfAbsent(key,
                ignored -> new MutableClaims(key, name, cell.dimension(), mapColor(color, id), 16, name, layer, bandit));
        group.cells.add(new ClaimGeometry.Cell(cell.x(), cell.z()));
    }

    private String banditNation(Object nation) throws ReflectiveOperationException {
        return nation != null && BanditNames.matches(call(nation,"getName").toString()) ? call(nation,"getUUID").toString() : "";
    }

    private static String profile(Map<String,Object> data) {
        if (data == null || !data.containsKey("profile")) return "";
        String json = JSON.toJson(ordered(JSON.toJsonTree(data.get("profile"))));
        return json.length() <= 32768 ? json : "";
    }

    private static com.google.gson.JsonElement ordered(com.google.gson.JsonElement value) {
        if(value.isJsonObject()) {
            var object=new com.google.gson.JsonObject();var entries=new TreeMap<String,com.google.gson.JsonElement>();
            value.getAsJsonObject().entrySet().forEach(e->entries.put(e.getKey(),e.getValue()));
            entries.forEach((key,child)->object.add(key,ordered(child)));return object;
        }
        if(value.isJsonArray()){var array=new com.google.gson.JsonArray();value.getAsJsonArray().forEach(child->array.add(ordered(child)));return array;}
        return value;
    }

    private static void addPoint(Map<String,MutableClaims> groups,String id,String name,String dimension,int x,int z,String detail,int color,String banditNation){
        String key=id+"/"+dimension;var marker=new MutableClaims(key,name,dimension,color,1,detail,4,banditNation);
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

    /** Expected lifecycle state, separate from broken reflection/API compatibility. */
    static final class NotReadyException extends Exception {
        NotReadyException(String reason) { super(reason); }
    }

    private static final class MutableClaims {
        final String id, name, dimension, banditNation;
        final int color, size;
        final Set<ClaimGeometry.Cell> cells = new HashSet<>();
        final String detail;final int layer;
        String profile = "";
        MutableClaims(String id, String name, String dimension, int color, int size,String detail,int layer,String banditNation) {
            this.id = id; this.name = name; this.dimension = dimension; this.color = color; this.size = size;
            this.detail=detail;this.layer=layer;
            this.banditNation=banditNation;
        }
    }
}



