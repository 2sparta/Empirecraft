package ua.empirecraft.townymap;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** A creation portrait refreshed at most once per real day. Work is spread across ticks and all reads are loaded-chunk only. */
final class ShipIconAtlas implements AutoCloseable {
    private record Cached(ShipIcon image,long updated) {}
    private record Job(long id,ServerLevel level,ShipIcon.Projection projection) {}
    private final Path directory;
    private final LinkedHashMap<Long,Cached> images=new LinkedHashMap<>(32,.75f,true);
    private final Map<Long,Job> jobs=new LinkedHashMap<>();
    private final Map<Long,Long> retry=new HashMap<>();
    private final Set<Long> missing=new HashSet<>();
    private final Set<Long> queuedWrites=ConcurrentHashMap.newKeySet();
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Empirecraft-ship-icons");t.setDaemon(true);return t;});
    private int clock,cursor;
    private final java.util.function.LongSupplier time;
    ShipIconAtlas(Path directory){this(directory,System::currentTimeMillis);}
    ShipIconAtlas(Path directory,java.util.function.LongSupplier time){this.directory=directory;this.time=time;}
    ShipIcon image(long id){
        var found=images.get(id);if(found!=null)return found.image();
        if(missing.contains(id))return null;Path file=directory.resolve(id+".bin.gz");if(!Files.isRegularFile(file)){if(missing.size()>=2048)missing.clear();missing.add(id);return null;}
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))){
            int format=in.readInt();long updated;ShipIcon image;
            if(format==2){updated=in.readLong();if(updated<0)throw new IOException("Invalid portrait time");image=ShipIcon.read(in);}
            else if(format==1){updated=0;int[] pixels=new int[ShipIcon.SIDE*ShipIcon.SIDE];for(int i=0;i<pixels.length;i++)pixels[i]=in.readInt();image=new ShipIcon(pixels);}
            else throw new IOException("Unsupported portrait cache");
            if(in.read()!=-1)throw new IOException("Trailing ship image data");put(id,new Cached(image,Math.min(updated,time.getAsLong())));return image;
        }catch(IOException|RuntimeException error){if(clock%1200==0)EmpireTownyMap.LOG.warn("Ship image unavailable: {}",id);return null;}
    }
    private void put(long id,Cached image){missing.remove(id);images.put(id,image);while(images.size()>512)images.remove(images.keySet().iterator().next());}
    void tick(MinecraftServer server){
        clock++;
        if(clock%20==1){
            var loaded=new ArrayList<Ship>();
            for(var level:server.getAllLevels())for(var ship:VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips())
                if(ship.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(level)))loaded.add(ship);
            int count=Math.min(16,loaded.size());
            for(int i=0;i<count;i++){
                Ship ship=loaded.get((cursor+i)%loaded.size());if(jobs.size()>=8)break;
                if(jobs.containsKey(ship.getId())||retry.getOrDefault(ship.getId(),0L)>clock)continue;
                if(image(ship.getId())!=null&&!ShipIcon.refreshDue(time.getAsLong(),images.get(ship.getId()).updated()))continue;
                for(var level:server.getAllLevels())if(ship.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(level))){schedule(level,ship);break;}
            }
            if(!loaded.isEmpty())cursor=(cursor+count)%loaded.size();
        }
        int budget=4096;
        for(var it=jobs.entrySet().iterator();it.hasNext()&&budget>0;){var entry=it.next();Job job=entry.getValue();
            if(VSGameUtilsKt.getShipObjectWorld(job.level()).getLoadedShips().getById(job.id())==null){it.remove();continue;}
            budget-=job.projection().step(budget);
            if(job.projection().blocked()){retry.put(job.id(),(long)clock+40);it.remove();}
            else if(job.projection().complete()){var image=new Cached(job.projection().image(),time.getAsLong());put(job.id(),image);persist(job.id(),image);it.remove();}
        }
        if(retry.size()>2048)retry.entrySet().removeIf(e->e.getValue()<=clock);
    }
    private void schedule(ServerLevel level,Ship ship){
        var box=ship.getShipAABB();if(box==null)return;
        try{
            var scan=new ShipIcon.Projection(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ(),new ShipIcon.Projection.Columns(){
                public int top(int x,int z){var c=level.getChunkSource().getChunkNow(x>>4,z>>4);return c==null?Integer.MIN_VALUE:c.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15);}
                public int color(int x,int y,int z){var c=level.getChunkSource().getChunkNow(x>>4,z>>4);if(c==null)return 0;
                    var p=new BlockPos(x,y,z);var state=c.getBlockState(p);if(state.isAir()||state.getBlock() instanceof LiquidBlock)return 0;
                    int rgb=state.getMapColor(level,p).col;return rgb==0?0:0xff000000|rgb;
                }
            });jobs.put(ship.getId(),new Job(ship.getId(),level,scan));
        }catch(IllegalArgumentException invalid){retry.put(ship.getId(),(long)clock+1200);}
    }
    private void persist(long id,Cached image){
        if(!queuedWrites.add(id))return;
        writer.execute(()->{Path temp=null;try{
            Files.createDirectories(directory);temp=Files.createTempFile(directory,id+"-",".tmp");
            try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temp)))){out.writeInt(2);out.writeLong(image.updated());image.image().write(out);}
            try{Files.move(temp,directory.resolve(id+".bin.gz"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,directory.resolve(id+".bin.gz"),StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException error){EmpireTownyMap.LOG.error("Could not save creation image for ship "+id,error);}
        finally{queuedWrites.remove(id);if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}});
    }
    public void close()throws IOException{writer.shutdown();try{if(!writer.awaitTermination(30,TimeUnit.SECONDS))throw new IOException("Ship icons still saving");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}images.clear();jobs.clear();retry.clear();}
}
