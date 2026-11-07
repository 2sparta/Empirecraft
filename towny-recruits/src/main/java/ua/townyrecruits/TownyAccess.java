package ua.townyrecruits;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/** Towny lives in a Bukkit plugin classloader on hybrid servers. No bundled API copies. */
final class TownyAccess {
    final Object plugin, api;
    final Class<?> bukkit, pluginType;
    final Map<String,Method> methods=new HashMap<>();
    final Map<Key,Claim> claims=new HashMap<>();
    Object listener;
    final Map<String,Object> armyCommands = new HashMap<>();
    Class<?> addonApi, commandType; Object nationCommands;
    int blockSize;
    final Map<String,Boolean> commandAuthority=new HashMap<>();
    record Key(String dim,int x,int z) {}
    record Claim(UUID nation,boolean wilderness) {}
    TownyAccess(Runnable changed, MilitarySystem military) throws ReflectiveOperationException {
        bukkit=Class.forName("org.bukkit.Bukkit");
        pluginType=Class.forName("org.bukkit.plugin.Plugin");
        if(bukkit.getMethod("getServer").invoke(null)==null) throw new IllegalStateException("Bukkit not ready");
        Object manager=bukkit.getMethod("getPluginManager").invoke(null);
        plugin=Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"Towny");
        if(plugin==null || !(boolean)pluginType.getMethod("isEnabled").invoke(plugin))throw new IllegalStateException("Towny not enabled");
        ClassLoader cl=plugin.getClass().getClassLoader();
        api=Class.forName("com.palmergames.bukkit.towny.TownyAPI",true,cl).getMethod("getInstance").invoke(null);
        blockSize=(int)Class.forName("com.palmergames.bukkit.towny.TownySettings",true,cl).getMethod("getTownBlockSize").invoke(null);
        if(blockSize<1)throw new IllegalStateException("Invalid Towny block size");
        Class<?> lt=Class.forName("org.bukkit.event.Listener"), et=Class.forName("org.bukkit.plugin.EventExecutor");
        listener=Proxy.newProxyInstance(lt.getClassLoader(),new Class[]{lt},TownyAccess::proxyObject);
        Object executor=Proxy.newProxyInstance(et.getClassLoader(),new Class[]{et},(p,m,a)->{
            if(m.getName().equals("execute")){ claims.clear(); changed.run(); return null; }
            return proxyObject(p,m,a);
        });
        Class<?> priority=Class.forName("org.bukkit.event.EventPriority");
        @SuppressWarnings({"unchecked","rawtypes"}) Object monitor=Enum.valueOf((Class)priority,"MONITOR");
        Method register=Class.forName("org.bukkit.plugin.PluginManager").getMethod("registerEvent",Class.class,lt,priority,et,pluginType,boolean.class);
        String[] events={"NewNationEvent","DeleteNationEvent","DeleteTownEvent","NationAddTownEvent","NationRemoveTownEvent","TownAddResidentEvent","TownRemoveResidentEvent","resident.NewResidentEvent","DeleteResidentEvent","RenameResidentEvent","RenameNationEvent","NationAddEnemyEvent","NationRemoveEnemyEvent","NationAcceptAllyRequestEvent","NationRemoveAllyEvent","TownClaimEvent","town.TownUnclaimEvent","nation.NationMergeEvent","town.TownMergeEvent","nation.NationTownLeaveEvent","nation.NationKingChangeEvent"};
        for(String name:events) {
            try { register.invoke(manager,Class.forName("com.palmergames.bukkit.towny.event."+name,true,cl),listener,monitor,executor,plugin,true); }
            catch(ClassNotFoundException ignored) { /* Version-specific events; periodic reconciliation is also active. */ }
        }
        try { registerArmyCommands(cl, military); } catch (ReflectiveOperationException | RuntimeException e) { close(); throw e; }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerArmyCommands(ClassLoader cl, MilitarySystem military) throws ReflectiveOperationException {
        addonApi = Class.forName("com.palmergames.bukkit.towny.TownyCommandAddonAPI", true, cl);
        commandType = Class.forName("com.palmergames.bukkit.towny.TownyCommandAddonAPI$CommandType", true, cl);
        nationCommands = Enum.valueOf((Class)commandType, "NATION");
        Class<?> executor = Class.forName("org.bukkit.command.CommandExecutor"), tab = Class.forName("org.bukkit.command.TabCompleter");
        Class<?> addon = Class.forName("com.palmergames.bukkit.towny.object.AddonCommand", true, cl);
        for (String name : List.of("regiment", "army")) {
            Object proxy = Proxy.newProxyInstance(executor.getClassLoader(), new Class[]{executor, tab}, (p, m, a) -> {
                if (!m.getName().equals("onCommand") && !m.getName().equals("onTabComplete")) return proxyObject(p,m,a);
                Object sender = a[0]; String[] args = (String[])a[3];
                Class<?> playerType = Class.forName("org.bukkit.entity.Player");
                if (!playerType.isInstance(sender)) {
                    if (m.getName().equals("onCommand")) Class.forName("org.bukkit.command.CommandSender").getMethod("sendMessage", String.class).invoke(sender, "Ця команда призначена для жителів нації Towny.");
                    return m.getName().equals("onCommand") ? true : List.of();
                }
                UUID id = (UUID)playerType.getMethod("getUniqueId").invoke(sender);
                var player = military.bridge.server.m_6846_().m_11259_(id);
                if (m.getName().equals("onTabComplete")) return military.complete(id, args);
                if (player != null) military.bridge.server.execute(() -> military.command(player, name.equals("army"), args));
                return true;
            });
            Object command = addon.getConstructor(commandType, String.class, executor).newInstance(nationCommands, name, proxy);
            if ((boolean)addonApi.getMethod("addSubCommand", addon).invoke(null, command)) armyCommands.put(name, command);
            else TownyRecruits.LOG.warn("Towny /nation {} is already registered; use /nation regiment for military information.", name);
        }
    }
    boolean officer(UUID player) throws ReflectiveOperationException {
        Object resident = api.getClass().getMethod("getResident", UUID.class).invoke(api, player);
        if (resident == null) return false;
        if ((boolean)call(resident, "isKing")) return true;
        for (Object rank : (Collection<?>)call(resident, "getNationRanks"))
            if (Set.of("assistant", "general", "military").contains(rank.toString().toLowerCase(Locale.ROOT))) return true;
        Object online = bukkit.getMethod("getPlayer", UUID.class).invoke(null, player);
        return online != null && (boolean)Class.forName("org.bukkit.permissions.Permissible").getMethod("hasPermission", String.class).invoke(online, "townyrecruits.regiments.manage");
    }
    UUID city(UUID player) throws ReflectiveOperationException {
        Object resident=api.getClass().getMethod("getResident",UUID.class).invoke(api,player);
        Object town=resident==null?null:call(resident,"getTownOrNull");return town==null?null:id(town);
    }
    @SuppressWarnings("unchecked")
    boolean commander(UUID ownerCity,UUID nation,UUID player,Policy.Snapshot snapshot)throws ReflectiveOperationException {
        String key=ownerCity+":"+player;var cached=commandAuthority.get(key);if(cached!=null)return cached;
        boolean allowed;
        Object feudal=feudal();
        if(feudal!=null){
            var answer=(Map<String,Object>)feudal.getClass().getMethod("fleetRequest",String.class,Map.class).invoke(feudal,"command-authority",Map.of("town","t:"+ownerCity,"player",player.toString()));
            allowed=Boolean.TRUE.equals(answer.get("allow"));
        }else allowed=ownerCity.equals(city(player))||Policy.relation(snapshot,nation,snapshot.residents().get(player))==1;
        commandAuthority.put(key,allowed);return allowed;
    }
    String name(UUID player)throws ReflectiveOperationException {
        Object resident=api.getClass().getMethod("getResident",UUID.class).invoke(api,player);return resident==null?player.toString():call(resident,"getName").toString();
    }
    UUID resident(String name)throws ReflectiveOperationException {
        Object found;
        try{found=api.getClass().getMethod("getResident",UUID.class).invoke(api,UUID.fromString(name));}
        catch(IllegalArgumentException notUuid){found=api.getClass().getMethod("getResident",String.class).invoke(api,name);}
        if(found==null)throw new IllegalArgumentException("Командира не знайдено.");return id(found);
    }
    UUID mayor(UUID city)throws ReflectiveOperationException {
        Object town=api.getClass().getMethod("getTown",UUID.class).invoke(api,city);Object mayor=town==null?null:call(town,"getMayor");return mayor==null?null:id(mayor);
    }
    Object feudal() throws ReflectiveOperationException {
        Object manager=bukkit.getMethod("getPluginManager").invoke(null);
        Object found=Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"EmpirecraftFeudal");
        return found!=null && (boolean)pluginType.getMethod("isEnabled").invoke(found)?found:null;
    }
    static Object proxyObject(Object p,Method m,Object[] a) {
        return switch(m.getName()){case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];case "toString"->"TownyRecruitsListener";default->null;};
    }
    Object call(Object o,String name) throws ReflectiveOperationException {
        String key=o.getClass().getName()+"#"+name;
        Method m=methods.get(key); if(m==null){m=o.getClass().getMethod(name);methods.put(key,m);} return m.invoke(o);
    }
    boolean ready() throws ReflectiveOperationException {return (boolean)pluginType.getMethod("isEnabled").invoke(plugin);}
    UUID id(Object o) throws ReflectiveOperationException {return (UUID)call(o,"getUUID");}
    java.util.Set<java.nio.file.Path> worldRoots() throws ReflectiveOperationException {
        java.util.Set<java.nio.file.Path> roots=new java.util.HashSet<>();
        for(Object world:(Collection<?>)bukkit.getMethod("getWorlds").invoke(null))roots.add(((java.io.File)call(world,"getWorldFolder")).toPath().toAbsolutePath().normalize());
        return roots;
    }
    Policy.Snapshot snapshot() throws ReflectiveOperationException {
        commandAuthority.clear();
        if(!ready())throw new IllegalStateException("Towny disabled");
        Map<UUID,Policy.Nation> ns=new HashMap<>(); Map<UUID,UUID> residents=new HashMap<>();
        for(Object n:(Collection<?>)call(api,"getNations")) {
            UUID id=id(n); Map<UUID,String> members=new HashMap<>();
            for(Object r:(Collection<?>)call(n,"getResidents")){members.put(id(r),call(r,"getName").toString());residents.put(id(r),id);}
            Set<UUID> enemies=new HashSet<>(),allies=new HashSet<>();
            for(Object x:(Collection<?>)call(n,"getEnemies"))enemies.add(id(x));
            for(Object x:(Collection<?>)call(n,"getAllies"))allies.add(id(x));
            Object king=call(n,"getKing");
            ns.put(id,new Policy.Nation(id,call(n,"getName").toString(),king==null?id:id(king),king==null?"Towny":call(king,"getName").toString(),((Collection<?>)call(n,"getTowns")).size(),Map.copyOf(members),Set.copyOf(enemies),Set.copyOf(allies)));
        }
        return new Policy.Snapshot(Map.copyOf(ns),Map.copyOf(residents));
    }
    boolean allowed(ServerLevel level,BlockPos pos,UUID nation) throws ReflectiveOperationException {
        if(!ready())return false;
        Key key=new Key(level.m_46472_().m_135782_().toString(),Math.floorDiv(pos.m_123341_(),blockSize),Math.floorDiv(pos.m_123343_(),blockSize));
        Claim c=claims.get(key);
        if(c==null){
            Object bw=null;
            for(Object w:(Collection<?>)bukkit.getMethod("getWorlds").invoke(null)) if(call(w,"getHandle")==level){bw=w;break;}
            if(bw==null)return false;
            Class<?> loc=Class.forName("org.bukkit.Location");
            Object location=loc.getConstructor(Class.forName("org.bukkit.World"),double.class,double.class,double.class).newInstance(bw,(double)pos.m_123341_(),(double)pos.m_123342_(),(double)pos.m_123343_());
            Object tb=api.getClass().getMethod("getTownBlock",loc).invoke(api,location);
            if(tb==null)c=new Claim(null,true);
            else {Object town=call(tb,"getTownOrNull");Object n=town==null?null:call(town,"getNationOrNull");c=new Claim(n==null?null:id(n),false);}
            if(claims.size()>=8192)claims.clear();claims.put(key,c);
        }
        return c.wilderness || nation!=null && nation.equals(c.nation);
    }
    void close() {
        for (var e : armyCommands.entrySet()) try {
            if (addonApi.getMethod("getAddonCommand", commandType, String.class).invoke(null, nationCommands, e.getKey()) == e.getValue())
                addonApi.getMethod("removeSubCommand", commandType, String.class).invoke(null, nationCommands, e.getKey());
        } catch (ReflectiveOperationException ignored) {}
        armyCommands.clear();
        try{Class.forName("org.bukkit.event.HandlerList").getMethod("unregisterAll",Class.forName("org.bukkit.event.Listener")).invoke(null,listener);}catch(ReflectiveOperationException ignored){}
    }
}
