package com.quintonc.vs_sails.protection;

import net.minecraft.server.level.ServerLevel;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import java.lang.reflect.*;
import java.util.*;

/** All calls stay on the server thread. Resolve Towny through its actual plugin loader after enable. */
public final class ShipTowny {
    final Class<?> bukkit, location, townType;
    final Object plugin, api;
    public ShipTowny() throws ReflectiveOperationException {
        bukkit=Class.forName("org.bukkit.Bukkit");
        if(!Boolean.TRUE.equals(bukkit.getMethod("isPrimaryThread").invoke(null)))throw new IllegalStateException("Towny outside server thread");
        Object manager=bukkit.getMethod("getPluginManager").invoke(null);
        plugin=Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"Towny");
        if(plugin==null || !Boolean.TRUE.equals(Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(plugin)))throw new IllegalStateException("Towny is unavailable");
        ClassLoader loader=plugin.getClass().getClassLoader();
        api=Class.forName("com.palmergames.bukkit.towny.TownyAPI",true,loader).getMethod("getInstance").invoke(null);
        if(api==null)throw new IllegalStateException("Towny is initialising");
        location=Class.forName("org.bukkit.Location");townType=Class.forName("com.palmergames.bukkit.towny.object.Town",true,loader);
    }
    public static boolean installed() {
        try {Class.forName("org.bukkit.Bukkit");return true;}catch(ClassNotFoundException plainForge){return false;}
    }
    static Object call(Object target,String method) throws ReflectiveOperationException {return target.getClass().getMethod(method).invoke(target);}
    public static UUID id(Object object) throws ReflectiveOperationException {return object==null?null:(UUID)call(object,"getUUID");}
    public Object town(UUID id) throws ReflectiveOperationException {return id==null?null:api.getClass().getMethod("getTown",UUID.class).invoke(api,id);}
    public Object town(String name) throws ReflectiveOperationException {
        try{return town(UUID.fromString(name));}catch(IllegalArgumentException ignored){return api.getClass().getMethod("getTown",String.class).invoke(api,name);}
    }
    public Object resident(UUID player) throws ReflectiveOperationException {return api.getClass().getMethod("getResident",UUID.class).invoke(api,player);}
    public UUID playerTown(UUID player) throws ReflectiveOperationException {Object r=resident(player);return r==null?null:id(call(r,"getTownOrNull"));}
    public UUID playerId(String name) throws ReflectiveOperationException {
        try{return UUID.fromString(name);}catch(IllegalArgumentException ignored){Object r=api.getClass().getMethod("getResident",String.class).invoke(api,name);return id(r);}
    }
    public boolean permission(UUID player,String node) throws ReflectiveOperationException {
        Object actor=bukkit.getMethod("getPlayer",UUID.class).invoke(null,player);
        return actor!=null && Boolean.TRUE.equals(Class.forName("org.bukkit.permissions.Permissible").getMethod("hasPermission",String.class).invoke(actor,node));
    }
    public boolean manages(UUID actor,UUID owner) throws ReflectiveOperationException {
        if(!Objects.equals(playerTown(actor),owner) || owner==null)return false;
        Object town=town(owner);return town!=null && (actor.equals(id(call(town,"getMayor"))) || permission(actor,ProtectionConfig.get().managePermission));
    }
    private Object at(Object world,double x,double y,double z) throws ReflectiveOperationException {
        Object loc=location.getConstructor(Class.forName("org.bukkit.World"),double.class,double.class,double.class).newInstance(world,x,y,z);
        return api.getClass().getMethod("getTown",location).invoke(api,loc);
    }
    public boolean allied(Object owner,Object territory) throws ReflectiveOperationException {
        if(id(owner).equals(id(territory)))return true;
        var config=ProtectionConfig.get();Object n1=call(owner,"getNationOrNull"),n2=call(territory,"getNationOrNull");
        if(config.protectSameNation && n1!=null && id(n1).equals(id(n2)))return true;
        return config.protectAlliedTowns && Boolean.TRUE.equals(townType.getMethod("isAlliedWith",townType).invoke(owner,territory));
    }
    /** Any claim intersected by the WORLD footprint protects the whole ship, including an overhanging bow. */
    public boolean protectedWaters(ServerLevel level,LoadedServerShip ship,UUID owner) throws ReflectiveOperationException {
        Object world=level.getClass().getMethod("getWorld").invoke(level);
        if(!Boolean.TRUE.equals(api.getClass().getMethod("isTownyWorld",Class.forName("org.bukkit.World")).invoke(api,world)))return false;
        Object home=town(owner);
        var box=ship.getWorldAABB();
        if(box==null || !Double.isFinite(box.minX()) || !Double.isFinite(box.maxX()) || !Double.isFinite(box.minZ()) || !Double.isFinite(box.maxZ()))return true;
        int size=((Number)Class.forName("com.palmergames.bukkit.towny.TownySettings",true,plugin.getClass().getClassLoader()).getMethod("getTownBlockSize").invoke(null)).intValue();
        if(size<1 || size>1024)throw new IllegalStateException("Towny block size");
        long minX=(long)Math.floor(box.minX()/size),maxX=(long)Math.floor(Math.nextDown(box.maxX())/size);
        long minZ=(long)Math.floor(box.minZ()/size),maxZ=(long)Math.floor(Math.nextDown(box.maxZ())/size);
        if(maxX-minX>1024 || maxZ-minZ>1024 || (maxX-minX+1)*(maxZ-minZ+1)>4096)return true;
        for(long x=minX;x<=maxX;x++)for(long z=minZ;z<=maxZ;z++) {
            Object claimed=at(world,x*size+.5,64,z*size+.5);
            // Legacy/owner-deleted ships are quarantined in claims until an admin explicitly assigns them.
            if(claimed!=null && (home==null || allied(home,claimed)))return true;
        }
        return false;
    }
    public String describe(UUID owner) throws ReflectiveOperationException {
        Object town=town(owner);if(town==null)return String.valueOf(owner);
        Object nation=call(town,"getNationOrNull");return call(town,"getName")+" ["+owner+"] / "+(nation==null?"—":call(nation,"getName"));
    }
}
