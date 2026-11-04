package com.quintonc.vs_sails.ship;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** One fair server scheduler. Topology and active-water work never force chunks. */
@Mod.EventBusSubscriber(modid="vs_sails")
public final class ShipFlooding {
    private static final Map<ServerLevel,LinkedHashMap<Long,Context>> LEVELS=new HashMap<>();
    private static final ArrayDeque<Context> QUEUE=new ArrayDeque<>();
    private static final ThreadLocal<Boolean> WRITING=ThreadLocal.withInitial(()->false);
    private static int building;
    public record Load(double mass,double x,double y,double z) {public static final Load EMPTY=new Load(0,0,0,0);}
    private record Sent(int version,long tick) {}
    private ShipFlooding(){}
    static final class Context {
        final ServerLevel level;final SailsShipControl control;final long id;
        final Map<UUID,Sent> sent=new HashMap<>();
        FloodTopology.Job job;FloodTopology.Result topology;FloodZones.Region region;
        FloodState installing;int installCursor;
        double installX,installY,installZ;long installUnits;
        boolean dirty=true;int version,portCursor,jobAge;long seen,forceUntil,nextPorts,nextNetwork,retryAt;
        List<FloodModel.Port> sampled=new ArrayList<>();
        double sumX,sumY,sumZ;long waterUnits;
        Context(ServerLevel level,SailsShipControl control){
            this.level=level;this.control=control;id=control.ship.getId();
            for(var e:control.floodState.water.entrySet()){
                var p=BlockPos.of(e.getKey());int q=e.getValue();waterUnits+=q;
                sumX+=(p.getX()+.5)*q;sumY+=(p.getY()+.5)*q;sumZ+=(p.getZ()+.5)*q;
            }
        }
        LoadedServerShip ship(){return VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(id);}
    }
    public static void track(ServerLevel level,SailsShipControl control){
        var map=LEVELS.computeIfAbsent(level,k->new LinkedHashMap<>());var c=map.get(control.ship.getId());
        if(c==null || c.control!=control){c=new Context(level,control);map.put(c.id,c);QUEUE.addLast(c);}
        c.seen=level.getServer().getTickCount();
    }
    public static FloodZones.Region region(ServerLevel level,long id){var map=LEVELS.get(level);var c=map==null?null:map.get(id);return c==null?null:c.region;}
    public static int knownVolume(ServerLevel level,long id,BlockPos pos,int fallback){
        var map=LEVELS.get(level);var c=map==null?null:map.get(id);
        return c==null?fallback:c.control.floodState.water.getOrDefault(pos.asLong(),fallback);
    }
    private static Context context(ServerLevel level,BlockPos pos){
        var ship=VSGameUtilsKt.getShipObjectManagingPos(level,pos);if(ship==null)return null;
        var control=SailsShipControl.getOrCreate(ship,level);track(level,control);return LEVELS.get(level).get(ship.getId());
    }
    public static boolean shipPosition(Level level,BlockPos pos){return VSGameUtilsKt.getShipManagingPos(level,pos)!=null;}
    public static void changed(Level level,BlockPos pos){
        if(WRITING.get() || !(level instanceof ServerLevel server))return;
        var map=LEVELS.get(server);if(map==null)return;
        var ship=VSGameUtilsKt.getShipObjectManagingPos(server,pos);if(ship==null)return;
        var c=map.get(ship.getId());if(c==null)return;
        c.dirty=true;c.forceUntil=server.getServer().getTickCount()+200;c.retryAt=0;
        cancel(c);
    }
    private static void cancel(Context c){if(c.job!=null){c.job=null;building--;}c.installing=null;c.installCursor=0;}
    public static int volume(ServerLevel level,BlockPos pos){
        var c=context(level,pos);if(c==null)return 0;
        Integer n=c.topology==null?null:c.topology.index().get(pos.asLong());
        if(n!=null && !c.dirty)return c.topology.model().amount(n);
        var state=level.getBlockState(pos);if(!state.getFluidState().is(FluidTags.WATER))return 0;
        return c.control.floodState.water.getOrDefault(pos.asLong(),Math.min(8,state.getFluidState().getAmount()));
    }
    public static int bail(ServerLevel level,BlockPos pos,int maximum){
        var c=context(level,pos);if(c==null)return 0;Integer n=c.topology==null?null:c.topology.index().get(pos.asLong());
        int removed;
        if(n!=null && !c.dirty){removed=c.topology.model().bail(n,maximum);commit(c);}
        else{int old=volume(level,pos);removed=Math.min(old,maximum);write(c,pos,old-removed);c.dirty=true;cancel(c);publish(c);}
        c.forceUntil=level.getServer().getTickCount()+200;return removed;
    }
    public static boolean pour(ServerLevel level,BlockPos pos,int units){
        if(units<=0 || units>8 || !level.hasChunkAt(pos) || !DoorWater.canContain(level.getBlockState(pos)))return false;
        var c=context(level,pos);if(c==null)return false;Integer n=c.topology==null?null:c.topology.index().get(pos.asLong());
        if(n!=null && !c.dirty){c.topology.model().pour(n,units);commit(c);}
        else{
            int old=volume(level,pos),add=Math.min(8-old,units);write(c,pos,old+add);c.control.floodState.reserve+=units-add;
            c.dirty=true;cancel(c);publish(c);
        }
        c.forceUntil=level.getServer().getTickCount()+200;return true;
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event){LEVELS.clear();QUEUE.clear();building=0;}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel().isClientSide())FloodZones.clearClient();}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var server=org.valkyrienskies.mod.common.ValkyrienSkiesMod.getCurrentServer();
        if(server!=null && server.getTickCount()%20==0)discover(server);
        if(QUEUE.isEmpty())return;
        int remaining=8192,turns=Math.min(QUEUE.size(),32);
        for(int i=0;i<turns && remaining>0;i++){
            Context c=QUEUE.removeFirst();var ship=c.ship();
            if(ship==null || !c.control.owns(ship) || ship.getAttachment(SailsShipControl.class)!=c.control || c.level.getServer().getTickCount()-c.seen>100){
                cancel(c);var map=LEVELS.get(c.level);
                if(map!=null && map.remove(c.id,c))clearNetwork(c);continue;
            }
            QUEUE.addLast(c);long now=c.level.getServer().getTickCount();
            var world=ship.getTransform().getPositionInWorld();
            boolean nearby=c.level.getNearestPlayer(world.x(),world.y(),world.z(),192,false)!=null;
            if(now>=c.nextNetwork){network(c,ship);c.nextNetwork=now+20;}
            if(!nearby && now>c.forceUntil && c.control.floodState.water.isEmpty() && c.control.floodState.reserve==0)continue;
            if(c.dirty){
                if(now<c.retryAt)continue;
                if(c.job==null){
                    if(building>=4 || ship.getShipAABB()==null)continue;
                    try{c.job=new FloodTopology.Job(ship,c.control.floodState);building++;c.jobAge=0;}
                    catch(IllegalArgumentException tooLarge){c.retryAt=now+100;continue;}
                }
                int work=c.job.result==null?c.job.work(c.level,ship,Math.min(remaining,2048)):install(c,Math.min(remaining,512));
                remaining-=Math.max(1,work);c.jobAge++;
                if(c.job!=null && (c.job.failed || c.job.waiting && c.jobAge>100)){c.retryAt=now+(c.job.failed?100:20);cancel(c);continue;}
                continue;
            }
            if(c.topology==null)continue;
            var transform=ship.getTransform().getShipToWorld();c.topology.model().project(transform.m01(),transform.m11(),transform.m21());
            if(now>=c.nextPorts){int work=ports(c,ship,Math.min(remaining,128));remaining-=work;}
            if(now%4==c.id%4 && remaining>0){remaining-=c.topology.model().step(Math.min(remaining,512));commit(c);}
        }
    }
    /** Restore saved controllers and initialise nearby bare hulls, including ships without a helm. */
    private static void discover(net.minecraft.server.MinecraftServer server){
        int added=0;
        for(var level:server.getAllLevels())for(var player:level.players()){
            var bounds=new org.joml.primitives.AABBd(player.getX()-192,player.getY()-192,player.getZ()-192,player.getX()+192,player.getY()+192,player.getZ()+192);
            var world=VSGameUtilsKt.getShipObjectWorld(level);
            for(var candidate:world.getAllShips().getIntersecting(bounds,VSGameUtilsKt.getDimensionId(level))){
                var map=LEVELS.get(level);if(map!=null && map.containsKey(candidate.getId()))continue;
                var ship=world.getLoadedShips().getById(candidate.getId());if(ship==null)continue;
                var c=SailsShipControl.getOrCreate(ship,level);if(c.floodState==null)c.floodState=new FloodState();track(level,c);
                if(++added>=32)return;
            }
        }
    }
    private static int install(Context c,int budget){
        var topology=c.job.result;
        if(c.installing==null){
            c.installing=new FloodState();c.installing.interior=topology.cells();c.installing.reserve=topology.model().reserve();
            c.installX=c.installY=c.installZ=0;c.installUnits=0;c.installCursor=0;
        }
        int used=0;
        while(c.installCursor<topology.cells().length && used<budget){
            int n=c.installCursor++,q=topology.model().amount(n);used++;
            if(q==0)continue;BlockPos p=BlockPos.of(topology.cells()[n]);
            if(!rawWrite(c,p,q)){cancel(c);return used;}
            c.installing.water.put(p.asLong(),q);c.installUnits+=q;
            c.installX+=(p.getX()+.5)*q;c.installY+=(p.getY()+.5)*q;c.installZ+=(p.getZ()+.5)*q;
        }
        if(c.installCursor<topology.cells().length)return used;
        // Publish atomically only after the bounded world conversion completes. Until
        // then the previous sparse ledger remains valid for saves and bucket edits.
        c.topology=topology;c.control.floodState=c.installing;
        c.waterUnits=c.installUnits;c.sumX=c.installX;c.sumY=c.installY;c.sumZ=c.installZ;
        var next=FloodZones.Region.of(topology.cells());
        if(c.region==null || next==null || !Arrays.equals(c.region.bytes(),next.bytes()) || c.region.x!=next.x || c.region.y!=next.y || c.region.z!=next.z || c.region.sx!=next.sx || c.region.sy!=next.sy || c.region.sz!=next.sz)c.version++;
        c.region=next;publish(c);CabinAirSpace.invalidate(c.level,BlockPos.of(c.control.helmBlock));
        c.portCursor=0;c.sampled.clear();c.nextPorts=0;c.nextNetwork=0;c.dirty=false;cancel(c);return used;
    }
    private static int ports(Context c,LoadedServerShip ship,int budget){
        var mouths=c.topology.mouths();if(mouths.isEmpty()){c.topology.model().setPorts(List.of());c.nextPorts=c.level.getServer().getTickCount()+10;return 0;}
        var transform=ship.getTransform().getShipToWorld();double origin=ship.getTransform().getPositionInWorld().y();int used=0;
        while(used<budget && c.portCursor<mouths.size()){
            var mouth=mouths.get(c.portCursor++);BlockPos p=BlockPos.of(c.topology.cells()[mouth.node()]);var d=mouth.direction();
            Vector3d point=transform.transformPosition(new Vector3d(p.getX()+.5+d.getStepX()*.55,p.getY()+.5+d.getStepY()*.55,p.getZ()+.5+d.getStepZ()*.55));
            Vector3d normal=transform.transformDirection(new Vector3d(d.getStepX(),d.getStepY(),d.getStepZ())).normalize();
            double faceSpan=(d.getAxis()==net.minecraft.core.Direction.Axis.X?0:Math.abs(transform.m01()))
                    +(d.getAxis()==net.minecraft.core.Direction.Axis.Y?0:Math.abs(transform.m11()))
                    +(d.getAxis()==net.minecraft.core.Direction.Axis.Z?0:Math.abs(transform.m21()));
            double opening=point.y-faceSpan*.5;BlockPos outside=BlockPos.containing(point.x,opening+.02,point.z);
            var chunk=c.level.getChunkSource().getChunkNow(outside.getX()>>4,outside.getZ()>>4);used++;
            if(chunk==null)continue;var fluid=chunk.getFluidState(outside);boolean wet=fluid.is(FluidTags.WATER);
            double surface=opening;
            if(wet){BlockPos top=outside;for(int up=0;up<48;up++){used++;var above=top.above();var f=chunk.getFluidState(above);if(!f.is(FluidTags.WATER))break;top=above;}
                surface=top.getY()+chunk.getFluidState(top).getHeight(c.level,top);}
            boolean air=!wet && chunk.getBlockState(outside).getCollisionShape(c.level,outside).isEmpty();
            c.sampled.add(new FloodModel.Port(mouth.node(),surface-origin,opening-origin,normal.y,wet,wet&&fluid.isSource(),air));
        }
        if(c.portCursor>=mouths.size()){
            c.topology.model().setPorts(c.sampled);c.sampled=new ArrayList<>();c.portCursor=0;c.nextPorts=c.level.getServer().getTickCount()+4;
        }
        return used;
    }
    private static void commit(Context c){
        BitSet changes=c.topology.model().takeChanges();
        for(int n=changes.nextSetBit(0);n>=0;n=changes.nextSetBit(n+1))write(c,BlockPos.of(c.topology.cells()[n]),c.topology.model().amount(n));
        c.control.floodState.reserve=c.topology.model().reserve();publish(c);
    }
    private static void write(Context c,BlockPos pos,int amount){
        int old=c.control.floodState.water.getOrDefault(pos.asLong(),0);
        if(!rawWrite(c,pos,amount))return;
        if(amount==0)c.control.floodState.water.remove(pos.asLong());else c.control.floodState.water.put(pos.asLong(),amount);
        int delta=amount-old;c.waterUnits+=delta;c.sumX+=(pos.getX()+.5)*delta;c.sumY+=(pos.getY()+.5)*delta;c.sumZ+=(pos.getZ()+.5)*delta;
    }
    private static boolean rawWrite(Context c,BlockPos pos,int amount){
        if(!c.level.hasChunkAt(pos))return false;
        var owner=VSGameUtilsKt.getShipObjectManagingPos(c.level,pos);if(owner==null || owner.getId()!=c.id)return false;
        WRITING.set(true);try{return DoorWater.write(c.level,pos,amount);}finally{WRITING.set(false);}
    }
    private static void publish(Context c){
        long wet=c.waterUnits;
        if(wet<=0){
            var ship=c.ship();var p=ship==null?null:ship.getTransform().getPositionInShip();
            c.control.floodLoad=p==null || c.control.floodState.reserve==0?Load.EMPTY:new Load(c.control.floodState.reserve*125.0,p.x(),p.y(),p.z());return;
        }
        c.control.floodLoad=new Load((wet+c.control.floodState.reserve)*125.0,c.sumX/wet,c.sumY/wet,c.sumZ/wet);
    }
    private static void network(Context c,LoadedServerShip ship){
        var p=ship.getTransform().getPositionInWorld();String dimension=VSGameUtilsKt.getDimensionId(c.level);long now=c.level.getServer().getTickCount();
        Set<UUID> nearby=new HashSet<>();
        for(var player:c.level.players())if(player.distanceToSqr(p.x(),p.y(),p.z())<192*192){
            UUID id=player.getUUID();nearby.add(id);Sent sent=c.sent.get(id);
            if(sent==null || sent.version()!=c.version || now-sent.tick()>=200){
                FloodNetwork.send(player,dimension,c.id,c.region);c.sent.put(id,new Sent(c.version,now));
            }
        }
        for(var it=c.sent.keySet().iterator();it.hasNext();){var id=it.next();if(nearby.contains(id))continue;
            var player=c.level.getServer().getPlayerList().getPlayer(id);if(player!=null)FloodNetwork.send(player,dimension,c.id,null);it.remove();}
    }
    private static void clearNetwork(Context c){
        for(UUID id:c.sent.keySet()){var p=c.level.getServer().getPlayerList().getPlayer(id);if(p!=null)FloodNetwork.send(p,VSGameUtilsKt.getDimensionId(c.level),c.id,null);}
    }
}
