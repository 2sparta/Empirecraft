package ua.empirecraft.leaves;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import sereneseasons.api.season.SeasonHelper;

/** Seasonal seed banks are saved atomically with the affected chunk, not in a global world map. */
public final class SeasonalVegetation {
    private static final String DATA="empirecraft_seasonal_vegetation_v1";
    private static SeasonalVegetation instance;
    private final Map<ChunkAccess,CompoundTag> data=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ServerLevel,LinkedHashMap<Long,Work>> worlds=new IdentityHashMap<>();
    private final WildernessAccess wilderness=new WildernessAccess();
    SeasonalVegetation() {
        instance=this;
        MinecraftForge.EVENT_BUS.addListener(this::read);
        MinecraftForge.EVENT_BUS.addListener(this::save);
        MinecraftForge.EVENT_BUS.addListener(this::load);
        MinecraftForge.EVENT_BUS.addListener(this::unload);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::placed);
        MinecraftForge.EVENT_BUS.addListener(this::broken);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }
    private void stopped(ServerStoppedEvent event){worlds.clear();data.clear();instance=null;}
    private void read(ChunkDataEvent.Load e){data.put(e.getChunk(),e.getData().getCompound(DATA).copy());}
    private void save(ChunkDataEvent.Save e){var tag=data.get(e.getChunk());if(tag!=null)e.getData().put(DATA,tag.copy());}
    private void load(ChunkEvent.Load e){
        if(e.getLevel() instanceof ServerLevel level && e.getChunk() instanceof LevelChunk chunk)
            worlds.computeIfAbsent(level,x->new LinkedHashMap<>()).put(chunk.getPos().toLong(),new Work(chunk,data.computeIfAbsent(chunk,x->new CompoundTag())));
    }
    private void unload(ChunkEvent.Unload e){if(e.getLevel() instanceof ServerLevel l){var map=worlds.get(l);if(map!=null)map.remove(e.getChunk().getPos().toLong());}}
    private Work work(ServerLevel l,BlockPos p){var map=worlds.get(l);return map==null?null:map.get(net.minecraft.world.level.ChunkPos.asLong(p.getX()>>4,p.getZ()>>4));}
    private static String key(BlockPos p){return Long.toString(p.asLong());}
    private void placed(BlockEvent.EntityPlaceEvent e){
        if(!(e.getLevel() instanceof ServerLevel l) || !(e.getEntity() instanceof Player))return;
        if(e instanceof BlockEvent.EntityMultiPlaceEvent multiple){
            for(var snapshot:multiple.getReplacedBlockSnapshots())protect(l,snapshot.getPos());
        }else protect(l,e.getPos());
    }
    private void protect(ServerLevel l,BlockPos p){
        Work w=work(l,p);if(w==null)return;
        cancel(w,p);cancel(w,p.below());
        BlockState state=l.getBlockState(p);
        if(PlantCatalog.profile(state)!=null){
            w.protectedPos.add(p.asLong());
            if(upper(state))w.protectedPos.add(p.below().asLong());
            w.dirty();
        }
    }
    private void broken(BlockEvent.BreakEvent e){
        if(e.getLevel() instanceof ServerLevel l){Work w=work(l,e.getPos());if(w!=null){
            BlockPos p=e.getPos();cancel(w,p);cancel(w,p.below());
            boolean changed=w.protectedPos.remove(p.asLong());
            if(tall(e.getState()))changed|=w.protectedPos.remove((upper(e.getState())?p.below():p.above()).asLong());
            if(changed)w.dirty();
        }}
    }
    private static void cancel(Work w,BlockPos p){if(w.records.contains(key(p))){w.records.remove(key(p));w.dirty();}}
    private static boolean enabled(ServerLevel l){return SeasonalLeaves.enabled() && VegetationConfig.ENABLED.get() && sereneseasons.init.ModConfig.seasons.isDimensionWhitelisted(l.dimension());}
    private static boolean waterlogged(BlockState s){return !s.getFluidState().isEmpty() || s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED);}
    private static boolean upper(BlockState s){return s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER;}
    private static boolean tall(BlockState s){return s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);}
    private static double visible(ServerLevel l,BlockPos p,PlantCatalog.Profile profile){
        if(!enabled(l) || SeasonHelper.usesTropicalSeasons(l.getBiome(p)))return 1;
        var season=SeasonHelper.getSeasonState(l);
        return VegetationPolicy.visibility(profile.seasons(),season.getSeasonCycleTicks(),season.getSubSeasonDuration());
    }
    private static boolean inSeason(ServerLevel l,BlockPos p,BlockState s,PlantCatalog.Profile profile){return SeasonCurve.threshold(p.asLong() ^ PlantCatalog.id(s).hashCode())<visible(l,p,profile);}
    private static boolean replaceable(BlockState s){return s.isAir() || s.is(Blocks.SNOW);}
    private static boolean soil(ServerLevel l,BlockPos p){return VegetationPolicy.SOILS.contains(PlantCatalog.id(l.getBlockState(p.below())));}
    private static int rareCount(ServerLevel l,int cx,int cz){
        var chunk=l.getChunkSource().getChunkNow(cx,cz);if(chunk==null)return Integer.MAX_VALUE;
        int count=0;
        for(int x=cx*16;x<cx*16+16;x++)for(int z=cz*16;z<cz*16+16;z++){
            int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15);
            for(int d=0;d<4;d++){
                BlockState state=chunk.getBlockState(new BlockPos(x,y-d,z));
                if(VegetationPolicy.RARE.contains(PlantCatalog.id(state)) && !upper(state)){count++;break;}
            }
        }
        return count;
    }
    private boolean rareAllowed(ServerLevel l,BlockPos p,Work w){
        return VegetationPolicy.rareChunk(l.getSeed(),p.getX()>>4,p.getZ()>>4,VegetationConfig.RARE_CHUNKS.get())
            && (w==null || l.getGameTime()>=w.tag.getLong("RareUntil")) && rareCount(l,p.getX()>>4,p.getZ()>>4)==0;
    }
    private void rareSeen(ServerLevel l,Work w){
        long until=l.getGameTime()+(long)VegetationConfig.RARE_DAYS.get()*SeasonHelper.getSeasonState(l).getDayDuration();
        if(until>w.tag.getLong("RareUntil")){w.tag.putLong("RareUntil",until);w.dirty();}
    }
    /** Called by a small optional Mixin at the existing planter's candidate-list insertion. */
    public static boolean allowRenewal(ServerLevel l,int cx,int cz,BlockState state){
        if(instance==null || !enabled(l))return true;
        BlockPos p=new BlockPos(cx*16+8,64,cz*16+8);
        PlantCatalog.Profile profile=PlantCatalog.profile(state);
        if(profile!=null && !inSeason(l,p,state,profile))return false;
        return !VegetationPolicy.RARE.contains(PlantCatalog.id(state)) || instance.rareAllowed(l,p,instance.work(l,p));
    }
    private boolean restore(ServerLevel l,Work w,String k){
        CompoundTag r=w.records.getCompound(k);BlockPos p=BlockPos.of(r.getLong("pos"));
        var id=ResourceLocation.tryParse(r.getCompound("state").getString("Name"));
        if(id==null || !BuiltInRegistries.BLOCK.containsKey(id))return false;
        BlockState original=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),r.getCompound("state"));
        var profile=PlantCatalog.profile(original);
        if(profile!=null && !inSeason(l,p,original,profile))return false;
        if(enabled(l) && VegetationPolicy.RARE.contains(PlantCatalog.id(original)) && !rareAllowed(l,p,w))return false;
        if(!wilderness.allowed(l,p))return false;
        boolean doubled=r.contains("upper",Tag.TAG_COMPOUND);
        // Changed soil, player replacements, and occupied upper halves cancel the seed bank entry.
        if(!replaceable(l.getBlockState(p)) || doubled && !replaceable(l.getBlockState(p.above()))
            || !soil(l,p) || !original.canSurvive(l,p)) {w.records.remove(k);w.dirty();return true;}
        if(doubled){
            var topId=ResourceLocation.tryParse(r.getCompound("upper").getString("Name"));
            if(topId==null || !BuiltInRegistries.BLOCK.containsKey(topId))return false;
            BlockState top=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),r.getCompound("upper"));
            l.setBlock(p,original,Block.UPDATE_CLIENTS);l.setBlock(p.above(),top,Block.UPDATE_CLIENTS);
        }else l.setBlock(p,original,Block.UPDATE_CLIENTS);
        w.records.remove(k);w.dirty();
        if(VegetationPolicy.RARE.contains(PlantCatalog.id(original)))rareSeen(l,w);
        return true;
    }
    private boolean hide(ServerLevel l,Work w,BlockPos p,BlockState state){
        var profile=PlantCatalog.profile(state);
        if(profile==null || upper(state) || waterlogged(state) || !soil(l,p) || w.protectedPos.contains(p.asLong())
            || !l.canSeeSky(p.above()) || inSeason(l,p,state,profile))return false;
        if(!wilderness.allowed(l,p) || w.records.size()>=32768)return false;
        boolean doubled=tall(state);BlockState top=l.getBlockState(p.above());
        if(doubled && (!top.is(state.getBlock()) || !upper(top) || waterlogged(top)))return false;
        CompoundTag record=new CompoundTag();record.putLong("pos",p.asLong());record.put("state",NbtUtils.writeBlockState(state));
        if(doubled)record.put("upper",NbtUtils.writeBlockState(top));
        w.records.put(key(p),record);w.dirty();
        if(doubled)l.setBlock(p.above(),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
        l.setBlock(p,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
        return true;
    }
    private int groundCount(ServerLevel l,Work w){
        int count=0;var c=w.chunk;
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            int y=c.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            for(int d=0;d<3;d++)if(PlantCatalog.profile(c.getBlockState(new BlockPos(c.getPos().getMinBlockX()+x,y-d,c.getPos().getMinBlockZ()+z)))!=null){count++;break;}
        }
        return count;
    }
    private boolean grow(ServerLevel l,Work w){
        if(!enabled(l) || !VegetationConfig.SPAWN.get() || groundCount(l,w)>=VegetationConfig.DENSITY.get())return false;
        var random=l.getRandom();
        for(int attempt=0;attempt<8;attempt++){
            int x=w.chunk.getPos().getMinBlockX()+random.nextInt(16),z=w.chunk.getPos().getMinBlockZ()+random.nextInt(16);
            BlockPos p=new BlockPos(x,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
            if(!l.getBlockState(p).isAir() || !soil(l,p) || !l.canSeeSky(p) || !wilderness.allowed(l,p)
                || w.protectedPos.contains(p.asLong()) || w.records.contains(key(p)))continue;
            var choices=new ArrayList<BlockState>();
            // Grass dominates; other flora spreads only from this chunk's observed native seed bank.
            var seeds=new HashSet<>(w.seeds);seeds.add("minecraft:grass");
            boolean flowers=random.nextInt(8)==0;
            for(String id:seeds){
                if(!flowers && !id.equals("minecraft:grass") && !id.equals("minecraft:fern"))continue;
                ResourceLocation name=ResourceLocation.tryParse(id);if(name==null || !BuiltInRegistries.BLOCK.containsKey(name))continue;
                BlockState s=BuiltInRegistries.BLOCK.get(name).defaultBlockState();var profile=PlantCatalog.profile(s);
                if(profile==null || profile.renewal() || tall(s) || waterlogged(s) || !inSeason(l,p,s,profile)
                    || !VegetationPolicy.climate(profile.climate(),l.getBiome(p).value().getBaseTemperature()) || !s.canSurvive(l,p))continue;
                choices.add(s);
            }
            if(!choices.isEmpty())return l.setBlock(p,choices.get(random.nextInt(choices.size())),Block.UPDATE_CLIENTS);
        }
        return false;
    }
    private void tick(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END || !(e.level instanceof ServerLevel l))return;
        var map=worlds.get(l);if(map==null || map.isEmpty())return;
        int scans=VegetationConfig.SCAN.get(),changes=VegetationConfig.CHANGES.get(),visits=map.size();
        while(visits-->0 && scans>0 && changes>0){
            var it=map.entrySet().iterator();var entry=it.next();Work w=entry.getValue();it.remove();map.put(entry.getKey(),w);
            if(l.getChunkSource().getChunkNow(w.chunk.getPos().x,w.chunk.getPos().z)!=w.chunk)continue;
            if(!l.getWorldBorder().isWithinBounds(w.chunk.getPos().getWorldPosition()))continue;
            if(w.restore==null)w.restore=new ArrayDeque<>(w.records.getAllKeys());
            while(!w.restore.isEmpty() && scans>0 && changes>0){String k=w.restore.remove();scans--;if(w.records.contains(k,Tag.TAG_COMPOUND) && restore(l,w,k))changes--;}
            if(!w.restore.isEmpty() || l.getGameTime()<w.nextScan)continue;
            var sections=w.chunk.getSections();
            while(w.cursor<sections.length*4096 && scans>0 && changes>0){
                int sectionIndex=w.cursor>>>12;var section=sections[sectionIndex];
                if(section.hasOnlyAir() || !section.maybeHas(s->PlantCatalog.profile(s)!=null)){w.cursor=(sectionIndex+1)*4096;scans--;continue;}
                int local=w.cursor++&4095;scans--;
                BlockState state=section.getBlockState(local&15,local>>>8,(local>>>4)&15);
                var profile=PlantCatalog.profile(state);if(profile==null || upper(state))continue;
                BlockPos p=new BlockPos(w.chunk.getPos().getMinBlockX()+(local&15),w.chunk.getMinBuildHeight()+sectionIndex*16+(local>>>8),w.chunk.getPos().getMinBlockZ()+((local>>>4)&15));
                if(!profile.renewal() && w.seeds.add(PlantCatalog.id(state)))w.dirty();
                if(VegetationPolicy.RARE.contains(PlantCatalog.id(state)))rareSeen(l,w);
                if(hide(l,w,p,state))changes--;
            }
            if(w.cursor>=sections.length*4096){
                if(changes>0 && grow(l,w))changes--;
                w.cursor=0;w.restore=null;w.nextScan=l.getGameTime()+VegetationConfig.DELAY.get();
            }
        }
    }
    private static final class Work {
        final LevelChunk chunk;final CompoundTag tag,records;
        final Set<Long> protectedPos=new HashSet<>();final Set<String> seeds=new HashSet<>();
        ArrayDeque<String> restore;int cursor;long nextScan;
        Work(LevelChunk chunk,CompoundTag tag){
            this.chunk=chunk;this.tag=tag;records=tag.getCompound("Dormant");tag.put("Dormant",records);
            for(long pos:tag.getLongArray("Protected"))protectedPos.add(pos);
            for(Tag value:tag.getList("Seeds",Tag.TAG_STRING))seeds.add(value.getAsString());
        }
        void dirty(){
            tag.putLongArray("Protected",protectedPos.stream().mapToLong(Long::longValue).toArray());
            var list=new ListTag();seeds.stream().sorted().forEach(s->list.add(StringTag.valueOf(s)));tag.put("Seeds",list);
            chunk.setUnsaved(true);
        }
    }
}
