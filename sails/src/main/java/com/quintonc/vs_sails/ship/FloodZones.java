package com.quintonc.vs_sails.ship;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Compact membership masks; clients receive geometry only, water uses normal block packets. */
public final class FloodZones {
    public static final class Region {
        public final int x,y,z,sx,sy,sz;
        private final BitSet bits;
        public Region(int x,int y,int z,int sx,int sy,int sz,byte[] mask){
            long volume=(long)sx*sy*sz;
            if(sx<=0||sy<=0||sz<=0||volume>FloodTopology.MAX_SCAN||volume<=0)throw new IllegalArgumentException("Invalid flood mask bounds");
            this.x=x;this.y=y;this.z=z;this.sx=sx;this.sy=sy;this.sz=sz;bits=BitSet.valueOf(mask);
            if(bits.length()>volume || bits.cardinality()>FloodModel.MAX_CELLS)throw new IllegalArgumentException("Invalid flood mask size");
        }
        public static Region of(long[] positions){
            if(positions.length==0)return null;
            int x=Integer.MAX_VALUE,y=x,z=x,mx=Integer.MIN_VALUE,my=mx,mz=mx;
            for(long packed:positions){var p=BlockPos.of(packed);x=Math.min(x,p.getX());y=Math.min(y,p.getY());z=Math.min(z,p.getZ());mx=Math.max(mx,p.getX());my=Math.max(my,p.getY());mz=Math.max(mz,p.getZ());}
            int sx=mx-x+1,sy=my-y+1,sz=mz-z+1;BitSet bits=new BitSet();
            for(long packed:positions){var p=BlockPos.of(packed);bits.set(((p.getX()-x)*sy+p.getY()-y)*sz+p.getZ()-z);}
            return new Region(x,y,z,sx,sy,sz,bits.toByteArray());
        }
        public byte[] bytes(){return bits.toByteArray();}
        public boolean contains(BlockPos p){
            int a=p.getX()-x,b=p.getY()-y,c=p.getZ()-z;
            return a>=0&&a<sx&&b>=0&&b<sy&&c>=0&&c<sz&&bits.get((a*sy+b)*sz+c);
        }
    }
    private record Cached(Region region,long seen) {}
    private static final Map<String,Map<Long,Cached>> CLIENT=new ConcurrentHashMap<>();
    private FloodZones(){}
    public static void accept(String dimension,long ship,Region region){
        var regions=CLIENT.computeIfAbsent(dimension,k->new ConcurrentHashMap<>());
        long now=System.nanoTime();
        for(var map:CLIENT.values())map.values().removeIf(v->now-v.seen()>60_000_000_000L);
        if(region==null)regions.remove(ship);else{
            if(regions.size()>=256 && !regions.containsKey(ship)){
                var oldest=regions.entrySet().stream().min(Comparator.comparingLong(e->e.getValue().seen()));
                oldest.ifPresent(e->regions.remove(e.getKey()));
            }
            regions.put(ship,new Cached(region,now));
        }
    }
    public static void clearClient(){CLIENT.clear();}
    public static Region region(Level level,long ship){
        if(level.isClientSide){var cached=CLIENT.getOrDefault(VSGameUtilsKt.getDimensionId(level),Map.of()).get(ship);return cached==null || System.nanoTime()-cached.seen()>60_000_000_000L?null:cached.region();}
        return level instanceof net.minecraft.server.level.ServerLevel server?ShipFlooding.region(server,ship):null;
    }
    public static boolean contains(Level level,Ship ship,BlockPos local){var region=region(level,ship.getId());return region!=null&&region.contains(local);}
    public static boolean containsWorld(Level level,double x,double y,double z){
        return shipAt(level,x,y,z)!=null;
    }
    public static Ship shipAt(Level level,double x,double y,double z){
        BlockPos direct=BlockPos.containing(x,y,z);Ship owner=VSGameUtilsKt.getShipManagingPos(level,direct);
        if(owner!=null && contains(level,owner,direct))return owner;
        for(Ship ship:VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getIntersecting(new AABBd(x-.1,y-.1,z-.1,x+.1,y+.1,z+.1),VSGameUtilsKt.getDimensionId(level))){
            var local=ship.getWorldToShip().transformPosition(new Vector3d(x,y,z));
            if(contains(level,ship,BlockPos.containing(local.x,local.y,local.z)))return ship;
        }
        return null;
    }
}
