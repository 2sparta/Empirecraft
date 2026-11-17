package ua.empirecraft.earth;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.earth.mixin.TerrainAccess;

/** New independent completion flag; never resets any legacy flag or replenishes mined deposits. */
public final class RegionalDeposits {
    private static final Capability<State> CAP=CapabilityManager.get(new CapabilityToken<>(){});
    private static final ResourceLocation KEY=new ResourceLocation(EarthMod.ID,"regional_deposits_v1");
    private static final Set<Block> VANILLA=Set.of(Blocks.STONE,Blocks.GRANITE,Blocks.DIORITE,Blocks.ANDESITE,Blocks.DEEPSLATE,Blocks.TUFF);
    public static final class State implements ICapabilitySerializable<CompoundTag>{
        private boolean done;private final LazyOptional<State> self=LazyOptional.of(()->this);
        public <T> LazyOptional<T> getCapability(Capability<T> c,Direction s){return c==CAP?self.cast():LazyOptional.empty();}
        public CompoundTag serializeNBT(){var n=new CompoundTag();n.putBoolean("Complete",done);return n;}
        public void deserializeNBT(CompoundTag n){done=n.getBoolean("Complete");}
    }
    private record Job(ServerLevel level,int x,int z){}
    private final Set<Job> queue=new LinkedHashSet<>();private int ticks;
    public RegionalDeposits(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addGenericListener(LevelChunk.class,(AttachCapabilitiesEvent<LevelChunk> e)->{if(!e.getObject().getLevel().isClientSide){var s=new State();e.addCapability(KEY,s);e.addListener(s.self::invalidate);}});
        bus.addListener((ChunkEvent.Load e)->{if(e.getLevel() instanceof ServerLevel l&&e.getChunk() instanceof LevelChunk c){int x=c.getPos().x,z=c.getPos().z;l.getServer().execute(()->{if(EarthMod.enabled(l))queue.add(new Job(l,x,z));});}});
        bus.addListener((ChunkEvent.Unload e)->{if(e.getLevel() instanceof ServerLevel l){int x=e.getChunk().getPos().x,z=e.getChunk().getPos().z;l.getServer().execute(()->queue.remove(new Job(l,x,z)));}});
        bus.addListener((net.minecraftforge.event.level.LevelEvent.Unload e)->{if(e.getLevel() instanceof ServerLevel l)l.getServer().execute(()->queue.removeIf(j->j.level==l));});
        bus.addListener((ServerStoppedEvent e)->{queue.clear();ticks=0;});
        bus.addListener((TickEvent.ServerTickEvent e)->{
            if(e.phase!=TickEvent.Phase.END||++ticks%20!=0||queue.isEmpty())return;
            var it=queue.iterator();var job=it.next();it.remove();
            var c=job.level.getChunkSource().getChunkNow(job.x,job.z);
            if(c!=null)process(job.level,c);
        });
    }
    private static void process(ServerLevel l,LevelChunk c){
        if(!EarthMod.enabled(l)||!l.getWorldBorder().isWithinBounds(c.getPos()))return;
        var state=c.getCapability(CAP).orElse(null);if(state==null||state.done)return;
        int ox=c.getPos().getMinBlockX(),oz=c.getPos().getMinBlockZ();
        if(!TerrainAccess.earth$wilderness(l,new BlockPos(ox+8,64,oz+8)))return;
        var p=EarthMod.data.coordinates(ox+8,oz+8);
        Set<String> ores=new TreeSet<>();
        for(var f:EarthMod.data.features)if(f.layer().equals("deposit")&&f.contains(p))for(var ore:f.values().getAsJsonArray("ores"))ores.add(ore.getAsString());
        Random r=new Random(l.getSeed()^c.getPos().toLong()^0x45415254484f5245L);
        var geology=EarthMod.data.at("geology",p);String defaultRock=geology==null?"granite":geology.values().getAsJsonArray("rocks").get(0).getAsString();
        for(String ore:ores){
            if(r.nextDouble()>.24)continue;
            int x=3+r.nextInt(10),z=3+r.nextInt(10),top=Math.min(64,c.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z)-10);
            int bottom=Math.max(l.getMinBuildHeight()+3,-60);if(top<=bottom)continue;
            int y=bottom+r.nextInt(top-bottom+1),placed=0;
            for(int attempt=0;attempt<48&&placed<8;attempt++){
                BlockPos pos=new BlockPos(ox+x+r.nextInt(5)-2,y+r.nextInt(5)-2,oz+z+r.nextInt(5)-2);
                if(pos.getY()<bottom||pos.getY()>c.getHeight(Heightmap.Types.OCEAN_FLOOR,pos.getX()-ox,pos.getZ()-oz)-8||c.getBlockEntity(pos)!=null)continue;
                if(!l.getWorldBorder().isWithinBounds(pos)||!TerrainAccess.earth$wilderness(l,pos))continue;
                var before=c.getBlockState(pos);String rock=host(before,defaultRock);if(rock==null)continue;
                String name=oreName(ore);
                var id=new ResourceLocation("tfc","ore/"+name+"/"+rock);var block=ForgeRegistries.BLOCKS.getValue(id);
                if(block==null||block==Blocks.AIR)throw new IllegalStateException("Missing regional ore "+id);
                if(l.setBlock(pos,block.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))placed++;
            }
        }
        state.done=true;c.setUnsaved(true);
    }
    public static String oreName(String ore){return Set.of("hematite","magnetite","limonite","cassiterite","garnierite","native_copper","native_gold","native_silver","sphalerite").contains(ore)?"normal_"+ore:ore;}
    private static String host(BlockState before,String fallback){
        if(VANILLA.contains(before.getBlock()))return fallback;
        var id=ForgeRegistries.BLOCKS.getKey(before.getBlock());
        return id!=null&&id.getNamespace().equals("tfc")&&id.getPath().startsWith("rock/raw/")?id.getPath().substring(9):null;
    }
}
