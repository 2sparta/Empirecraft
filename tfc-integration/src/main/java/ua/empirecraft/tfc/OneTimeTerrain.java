package ua.empirecraft.tfc;

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

/** Independent chunk flags: existing sulfur/zinc/quartz completion is never reset. */
public final class OneTimeTerrain {
    private static final Capability<State> CAP=CapabilityManager.get(new CapabilityToken<>(){});
    private static final ResourceLocation KEY=new ResourceLocation(Integration.ID,"terrain_v1");
    private static final Set<Block> HOSTS=Set.of(Blocks.STONE,Blocks.GRANITE,Blocks.DIORITE,Blocks.ANDESITE,Blocks.DEEPSLATE,Blocks.TUFF);
    public record Deposit(String block,double chance,int size) {}
    public static final List<Deposit> DEPOSITS=List.of(
        new Deposit("antiquelegacy:tin_ore",0.24,8),
        new Deposit("tfc:ore/normal_bismuthinite/granite",0.10,6),
        new Deposit("tfc:ore/normal_garnierite/granite",0.07,6),
        new Deposit("tfc:ore/normal_native_silver/granite",0.07,6),
        new Deposit("tfc:white_kaolin_clay",0.08,8),
        new Deposit("tfc:ore/borax/granite",0.10,6),
        new Deposit("tfc:ore/sylvite/granite",0.12,6),
        new Deposit("tfc:ore/saltpeter/granite",0.12,6),
        new Deposit("tfc:ore/gypsum/granite",0.10,8),
        new Deposit("tfc:ore/bituminous_coal/granite",0.14,10),
        new Deposit("tfc:ore/lignite/granite",0.14,10),
        new Deposit("tfc:ore/ruby/granite",0.025,3),
        new Deposit("tfc:ore/sapphire/granite",0.025,3),
        new Deposit("tfc:ore/topaz/granite",0.025,3),
        new Deposit("tfc:ore/opal/granite",0.025,3),
        new Deposit("tfc:ore/pyrite/granite",0.04,4),
        new Deposit("tfc:ore/amethyst/granite",0.02,3),
        new Deposit("tfc:ore/diamond/granite",0.012,2),
        new Deposit("tfc:ore/emerald/granite",0.012,2),
        new Deposit("tfc:ore/lapis_lazuli/granite",0.025,3));
    public static final class State implements ICapabilitySerializable<CompoundTag> {
        public boolean stones,ores;
        private final LazyOptional<State> self=LazyOptional.of(()->this);
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){return cap==CAP?self.cast():LazyOptional.empty();}
        @Override public CompoundTag serializeNBT(){var n=new CompoundTag();n.putInt("Version",1);n.putBoolean("Stones",stones);n.putBoolean("Ores",ores);return n;}
        @Override public void deserializeNBT(CompoundTag n){stones=n.getBoolean("Stones");ores=n.getBoolean("Ores");}
    }
    private record Job(ServerLevel level,int x,int z){}
    private final Set<Job> queue=new LinkedHashSet<>();private int ticks;
    public OneTimeTerrain(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addGenericListener(LevelChunk.class,this::attach);bus.addListener(this::load);bus.addListener(this::unload);
        bus.addListener(this::tick);bus.addListener((net.minecraftforge.event.level.LevelEvent.Unload e)->queue.removeIf(j->j.level==e.getLevel()));
        bus.addListener((ServerStoppedEvent e)->{queue.clear();ticks=0;});
    }
    private static boolean allowed(ServerLevel level){String id=level.dimension().location().toString();return id.equals("minecraft:overworld")||id.equals("minecraft:world_europe");}
    private void attach(AttachCapabilitiesEvent<LevelChunk> e){
        if(e.getObject().getLevel().isClientSide)return;
        var state=new State();e.addCapability(KEY,state);e.addListener(state.self::invalidate);
    }
    private void load(ChunkEvent.Load e){
        if(e.getLevel() instanceof ServerLevel level&&e.getChunk() instanceof LevelChunk chunk){
            int x=chunk.getPos().x,z=chunk.getPos().z;
            level.getServer().execute(()->{if(allowed(level))queue.add(new Job(level,x,z));});
        }
    }
    private void unload(ChunkEvent.Unload e){
        if(e.getLevel() instanceof ServerLevel level){int x=e.getChunk().getPos().x,z=e.getChunk().getPos().z;
            level.getServer().execute(()->queue.remove(new Job(level,x,z)));}
    }
    private void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||++ticks%20!=0||queue.isEmpty())return;
        var it=queue.iterator();var job=it.next();it.remove();
        var chunk=job.level.getChunkSource().getChunkNow(job.x,job.z);if(chunk!=null)process(job.level,chunk);
    }
    public static boolean process(ServerLevel level,LevelChunk chunk){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Terrain changes require server thread");
        if(!allowed(level)||!level.getWorldBorder().isWithinBounds(chunk.getPos()))return false;
        State state=chunk.getCapability(CAP).orElse(null);if(state==null||state.stones&&state.ores)return false;
        int ox=chunk.getPos().getMinBlockX(),oz=chunk.getPos().getMinBlockZ();
        if(!wilderness(level,new BlockPos(ox+8,64,oz+8)))return false;
        long seed=level.getSeed()^chunk.getPos().toLong()^((long)level.dimension().location().hashCode()<<32)^0x544643454d504952L;
        Random random=new Random(seed);
        if(!state.ores){
            for(Deposit deposit:DEPOSITS){
                if(random.nextDouble()>=deposit.chance)continue;
                Block ore=ForgeRegistries.BLOCKS.getValue(new ResourceLocation(deposit.block));
                if(ore==null||ore==Blocks.AIR)throw new IllegalStateException("Missing planned deposit "+deposit.block);
                int x=1+random.nextInt(14),z=1+random.nextInt(14);
                int ceiling=Math.min(64,chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z)-8);
                int bottom=Math.max(level.getMinBuildHeight()+1,-60);if(ceiling<=bottom)continue;
                int y=bottom+random.nextInt(ceiling-bottom+1);
                for(int placed=0,attempt=0;attempt<deposit.size*6&&placed<deposit.size;attempt++){
                    int px=x+random.nextInt(5)-2,pz=z+random.nextInt(5)-2,py=y+random.nextInt(5)-2;
                    if(px<0||px>15||pz<0||pz>15||py<bottom||py>chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,px,pz)-8)continue;
                    BlockPos pos=new BlockPos(ox+px,py,oz+pz);BlockState before=chunk.getBlockState(pos);
                    if(!HOSTS.contains(before.getBlock())||chunk.getBlockEntity(pos)!=null)continue;
                    if(level.setBlock(pos,ore.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE))placed++;
                }
            }
            state.ores=true;
        }
        if(!state.stones){
            Block loose=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc:rock/loose/granite"));
            if(loose==null||loose==Blocks.AIR)throw new IllegalStateException("Missing TFC loose rock");
            for(int n=0;n<4;n++){
                int x=1+random.nextInt(14),z=1+random.nextInt(14),y=chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1;
                BlockPos pos=new BlockPos(ox+x,y,oz+z);var floor=chunk.getBlockState(pos.below());
                if(!chunk.getBlockState(pos).isAir()||!Set.of(Blocks.GRASS_BLOCK,Blocks.DIRT,Blocks.COARSE_DIRT,Blocks.PODZOL,Blocks.STONE,Blocks.GRANITE,Blocks.ANDESITE,Blocks.DIORITE).contains(floor.getBlock()))continue;
                if(loose.defaultBlockState().canSurvive(level,pos))level.setBlock(pos,loose.defaultBlockState(),Block.UPDATE_CLIENTS);
            }
            state.stones=true;
        }
        chunk.setUnsaved(true);return true;
    }
    /** Fail closed if Towny's API exists but cannot establish wilderness. */
    private static boolean wilderness(ServerLevel level,BlockPos pos){
        Class<?> bukkit;
        try{bukkit=Class.forName("org.bukkit.Bukkit");}catch(ClassNotFoundException ex){return true;}
        try{
            Object manager=bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin=Class.forName("org.bukkit.plugin.PluginManager").getMethod("getPlugin",String.class).invoke(manager,"Towny");
            if(plugin==null||!(boolean)Class.forName("org.bukkit.plugin.Plugin").getMethod("isEnabled").invoke(plugin))return false;
            Object world=level.getClass().getMethod("getWorld").invoke(level);
            Class<?> location=Class.forName("org.bukkit.Location");
            Object loc=location.getConstructor(Class.forName("org.bukkit.World"),double.class,double.class,double.class)
                .newInstance(world,(double)pos.getX(),(double)pos.getY(),(double)pos.getZ());
            Class<?> api=Class.forName("com.palmergames.bukkit.towny.TownyAPI",true,plugin.getClass().getClassLoader());Object instance=api.getMethod("getInstance").invoke(null);
            return (boolean)api.getMethod("isWilderness",location).invoke(instance,loc);
        }catch(ReflectiveOperationException ex){return false;}
    }
}
