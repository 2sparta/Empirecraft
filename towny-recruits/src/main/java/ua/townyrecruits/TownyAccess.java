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
    int blockSize;
    record Key(String dim,int x,int z) {}
    record Claim(UUID nation,boolean wilderness) {}
    TownyAccess(Runnable changed) throws ReflectiveOperationException {
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
        try{Class.forName("org.bukkit.event.HandlerList").getMethod("unregisterAll",Class.forName("org.bukkit.event.Listener")).invoke(null,listener);}catch(ReflectiveOperationException ignored){}
    }
}
