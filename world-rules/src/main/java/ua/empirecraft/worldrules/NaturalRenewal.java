package ua.empirecraft.worldrules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

public final class NaturalRenewal {
    private record Plant(Block block, String climate, boolean aquatic) {}
    private final TownyWilderness towny = new TownyWilderness();
    private final List<Plant> plants = new ArrayList<>(), trees = new ArrayList<>();
    private final Set<Block> wild = new HashSet<>(), saplings = new HashSet<>();

    public void initialize() {
        plants.clear(); trees.clear(); wild.clear(); saplings.clear();
        register(RulesConfig.PLANTS.get(), plants, wild);
        register(RulesConfig.TREES.get(), trees, saplings);
        WorldRules.LOG.info("Natural renewal ready: " + plants.size() + " plants, " + trees.size() + " tree saplings");
    }
    private static void register(List<? extends String> definitions, List<Plant> target, Set<Block> blocks) {
        for (String definition : definitions) {
            String[] parts = definition.split("\\|");
            var id = new ResourceLocation(parts[0]);
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null || block.m_49966_().m_60795_()) {
                WorldRules.LOG.warning("Unknown renewal plant: " + id); continue;
            }
            if (blocks.add(block)) target.add(new Plant(block, parts[1], parts[0].equals("farmersdelight:wild_rice")));
        }
    }

    public void sample(ServerLevel level) {
        var players = level.m_6907_();
        if (players.isEmpty()) return;
        var random = level.m_213780_();
        var visited = new HashSet<Long>();
        for (int sample=0; sample<RulesConfig.CHUNK_SAMPLES.get(); sample++) {
            var player = players.get(random.m_188503_(players.size()));
            int radius = RulesConfig.RADIUS.get();
            int cx = (player.m_20183_().m_123341_() >> 4) + random.m_188503_(radius*2+1)-radius;
            int cz = (player.m_20183_().m_123343_() >> 4) + random.m_188503_(radius*2+1)-radius;
            long key = ChunkPos.m_45589_(cx,cz);
            if (!visited.add(key) || !neighborhoodLoaded(level,cx,cz) || !towny.allowed(level,center(cx,cz))) continue;
            RenewalData data = level.m_7726_().m_8483_().m_164861_(RenewalData::load, RenewalData::new, "empirecraft_natural_renewal");
            plant(level,cx,cz,key,data);
            tree(level,cx,cz,key,data);
        }
    }
    private static BlockPos center(int cx,int cz) { return new BlockPos(cx*16+8,64,cz*16+8); }
    private static boolean loaded(ServerLevel level,int x,int z) { return level.m_7726_().m_7131_(x,z) != null; }
    private static boolean neighborhoodLoaded(ServerLevel level,int cx,int cz) {
        for(int x=cx-1;x<=cx+1;x++) for(int z=cz-1;z<=cz+1;z++) if(!loaded(level,x,z))return false;
        return true;
    }
    private boolean treeNeighborhoodAllowed(ServerLevel level,int cx,int cz) {
        // A native tree's future crown can cross a chunk edge. Require a wilderness buffer at planting time.
        for(int x=cx-1;x<=cx+1;x++) for(int z=cz-1;z<=cz+1;z++) if(!towny.allowed(level,center(x,z)))return false;
        return true;
    }
    private static String id(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.m_60734_());
        return id == null ? "" : id.toString();
    }
    private static boolean water(ServerLevel level,BlockPos pos) {
        return id(level.m_8055_(pos)).equals("minecraft:water") && level.m_6425_(pos).m_76186_()==8;
    }
    private static boolean wet(ServerLevel level,BlockPos pos) {
        for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) for(int y=-1;y<=0;y++)
            if(water(level,pos.m_7918_(x,y,z)))return true;
        return false;
    }
    private int plantCount(ServerLevel level,int cx,int cz) {
        int count=0;
        for(int x=cx*16;x<cx*16+16;x++) for(int z=cz*16;z<cz*16+16;z++) {
            int top=level.m_6924_(Heightmap.Types.WORLD_SURFACE,x,z);
            // Include both halves of tall plants and the waterlogged base of rice, once per column.
            for(int y=top-1;y>=Math.max(top-3,level.m_141937_());y--)
                if(wild.contains(level.m_8055_(new BlockPos(x,y,z)).m_60734_())) { count++; break; }
        }
        return count;
    }
    private void plant(ServerLevel level,int cx,int cz,long key,RenewalData data) {
        int limit=RulesConfig.MAX_PLANTS.get();
        if(limit==0 || plants.isEmpty() || !data.plantReady(key,level.m_46467_(),RulesConfig.PLANT_COOLDOWN.get()))return;
        int available=Math.min(RulesConfig.PLANT_BATCH.get(),limit-plantCount(level,cx,cz));
        if(available<=0)return;
        var random=level.m_213780_();
        int placed=0;
        for(int attempt=0;attempt<24 && placed<available;attempt++) {
            int x=cx*16+random.m_188503_(16),z=cz*16+random.m_188503_(16);
            BlockPos pos=new BlockPos(x,level.m_6924_(Heightmap.Types.WORLD_SURFACE,x,z),z);
            // Rice occupies existing shallow source water, with its upper half above water.
            if(water(level,pos.m_7495_()))pos=pos.m_7495_();
            if(pos.m_123342_()<=level.m_141937_() || pos.m_123342_()+1>=level.m_151558_())continue;
            if(!RenewalPolicy.plantSubstrate(id(level.m_8055_(pos.m_7495_()))) || !towny.allowed(level,pos))continue;
            float temperature=level.m_204166_(pos).m_203334_().m_47554_();
            boolean air=level.m_46859_(pos),sourceWater=water(level,pos),upperAir=level.m_46859_(pos.m_7494_());
            var choices=new ArrayList<BlockState>();
            for(Plant plant:plants) {
                if(!RenewalPolicy.climate(plant.climate,temperature))continue;
                BlockState state=mature(plant.block.m_49966_());
                boolean tall=state.m_61138_(DoublePlantBlock.f_52858_);
                if(!RenewalPolicy.space(air,sourceWater,upperAir,plant.aquatic,tall))continue;
                if(plant.climate.equals("wet") && !wet(level,pos))continue;
                if(state.m_60710_(level,pos))choices.add(state);
            }
            if(choices.isEmpty())continue;
            BlockState state=choices.get(random.m_188503_(choices.size()));
            if(!towny.allowed(level,pos) || !towny.allowed(level,pos.m_7494_()))continue;
            if(state.m_61138_(DoublePlantBlock.f_52858_)) {
                // Place complete native two-block plants, including lemon bushes and waterlogged rice.
                DoublePlantBlock.m_153173_(level,state,pos,3);
                if(!level.m_8055_(pos).m_60713_(state.m_60734_()) || !level.m_8055_(pos.m_7494_()).m_60713_(state.m_60734_()))continue;
            } else if(!level.m_46597_(pos,state))continue;
            placed++;
        }
        if(placed>0)data.planted(key,level.m_46467_());
    }
    private static boolean log(BlockState state) { return id(state).endsWith("_log"); }
    private int treeCount(ServerLevel level,int cx,int cz) {
        int count=0;
        for(int x=cx*16;x<cx*16+16;x++) for(int z=cz*16;z<cz*16+16;z++) {
            int y=level.m_6924_(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            BlockPos pos=new BlockPos(x,y,z);
            if(saplings.contains(level.m_8055_(pos).m_60734_()) || saplings.contains(level.m_8055_(pos.m_7495_()).m_60734_())) { count++; continue; }
            // Count rooted trunks, rather than every leaf column of the same crown.
            pos=pos.m_7495_(); int depth=0;
            while(pos.m_123342_()>level.m_141937_() && log(level.m_8055_(pos)) && depth++<32)pos=pos.m_7495_();
            if(depth>0 && RenewalPolicy.naturalSoil(id(level.m_8055_(pos))))count++;
        }
        return count;
    }
    private static boolean treeSpace(ServerLevel level,BlockPos pos) {
        if(pos.m_123342_()+8>=level.m_151558_())return false;
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) for(int y=0;y<8;y++)
            if(!level.m_46859_(pos.m_7918_(x,y,z)))return false;
        return true;
    }
    private void tree(ServerLevel level,int cx,int cz,long key,RenewalData data) {
        int limit=RulesConfig.MAX_TREES.get();
        if(limit==0 || trees.isEmpty() || !data.treeReady(key,level.m_46467_(),RulesConfig.TREE_COOLDOWN.get())
            || treeCount(level,cx,cz)>=limit || !treeNeighborhoodAllowed(level,cx,cz))return;
        var random=level.m_213780_();
        for(int attempt=0;attempt<16;attempt++) {
            // Keep saplings away from chunk borders, in addition to the wilderness buffer.
            int x=cx*16+4+random.m_188503_(8),z=cz*16+4+random.m_188503_(8);
            BlockPos pos=new BlockPos(x,level.m_6924_(Heightmap.Types.WORLD_SURFACE,x,z),z);
            if(!RenewalPolicy.naturalSoil(id(level.m_8055_(pos.m_7495_()))) || !treeSpace(level,pos))continue;
            float temperature=level.m_204166_(pos).m_203334_().m_47554_();
            var common=new ArrayList<BlockState>();
            var rare=new ArrayList<BlockState>();
            String biome=level.m_204166_(pos).m_203543_().map(k -> k.m_135782_().toString()).orElse("");
            for(Plant tree:trees) {
                BlockState state=mature(tree.block.m_49966_());
                if(!RenewalPolicy.climate(tree.climate,temperature) || !state.m_60710_(level,pos))continue;
                String treeId=id(state);
                int weight=TreeRules.weight(treeId,biome,temperature);
                var pool=treeId.startsWith("minecraft:")?common:rare;
                for(int n=0;n<weight;n++)pool.add(state);
            }
            if((common.isEmpty() && rare.isEmpty()) || !towny.allowed(level,pos))continue;
            var choices=TreeRules.useRare(random.m_188500_(),common.isEmpty(),rare.isEmpty(),RulesConfig.RARE_TREE_CHANCE.get())?rare:common;
            if(level.m_46597_(pos,choices.get(random.m_188503_(choices.size())))) { data.treePlanted(key,level.m_46467_());return; }
        }
    }
    private static BlockState mature(BlockState state) {
        for(var property:state.m_61147_()) if(property instanceof IntegerProperty age
            && (age.m_61708_().equals("age") || age.m_61708_().equals("stage")))
            state=state.m_61124_(age,Collections.max(age.m_6908_()));
        return state;
    }
}
