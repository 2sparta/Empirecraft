package ua.empirecraft.worldrules;

import java.util.ArrayList;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

public final class NaturalRenewal {
    private record Plant(Block block, String climate) {}
    private final TownyWilderness towny = new TownyWilderness();
    private final List<Plant> plants = new ArrayList<>();
    private final Set<Block> wild = new HashSet<>();
    private Block salt;

    public void initialize() {
        plants.clear(); wild.clear();
        for (String definition : RulesConfig.PLANTS.get()) {
            String[] parts = definition.split("\\|");
            var id = new ResourceLocation(parts[0]);
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null || block.m_49966_().m_60795_()) { WorldRules.LOG.warning("Unknown renewal plant: " + id); continue; }
            plants.add(new Plant(block,parts[1])); wild.add(block);
        }
        salt = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("meadow:alpine_salt_ore"));
        WorldRules.LOG.info("Natural renewal ready: " + plants.size() + " plants; salt ore=" + (salt != null));
    }

    public void sample(ServerLevel level) {
        var players = level.m_6907_();
        if (players.isEmpty()) return;
        var random = level.m_213780_();
        var player = players.get(random.m_188503_(players.size()));
        int radius = RulesConfig.RADIUS.get();
        int cx = (player.m_20183_().m_123341_() >> 4) + random.m_188503_(radius*2+1)-radius;
        int cz = (player.m_20183_().m_123343_() >> 4) + random.m_188503_(radius*2+1)-radius;
        if (!loaded(level,cx,cz)) return;
        BlockPos center = new BlockPos(cx*16+8,64,cz*16+8);
        if (!towny.allowed(level,center)) return;
        RenewalData data = level.m_7726_().m_8483_().m_164861_(RenewalData::load, RenewalData::new, "empirecraft_natural_renewal");
        long key = ChunkPos.m_45589_(cx,cz);
        plant(level,cx,cz,key,data);
        ore(level,cx,cz,key,data);
    }

    private static boolean loaded(ServerLevel level, int x, int z) { return level.m_7726_().m_7131_(x,z) != null; }
    private static boolean neighborhoodLoaded(ServerLevel level, int cx, int cz) {
        for(int x=cx-1;x<=cx+1;x++) for(int z=cz-1;z<=cz+1;z++) if(!loaded(level,x,z))return false;
        return true;
    }
    private void plant(ServerLevel level,int cx,int cz,long key,RenewalData data) {
        int limit = RulesConfig.MAX_PLANTS.get();
        if (limit==0 || plants.isEmpty() || !data.plantReady(key,level.m_46467_(),RulesConfig.PLANT_COOLDOWN.get()) || !neighborhoodLoaded(level,cx,cz)) return;
        int count = 0;
        for(int x=cx*16;x<cx*16+16;x++) for(int z=cz*16;z<cz*16+16;z++) {
            int y=level.m_6924_(Heightmap.Types.WORLD_SURFACE,x,z)-1;
            if(wild.contains(level.m_8055_(new BlockPos(x,y,z)).m_60734_()) && ++count>=limit)return;
        }
        var random=level.m_213780_();
        for(int attempt=0;attempt<8;attempt++) {
            int x=cx*16+random.m_188503_(16),z=cz*16+random.m_188503_(16);
            BlockPos pos=new BlockPos(x,level.m_6924_(Heightmap.Types.WORLD_SURFACE,x,z),z);
            if(!level.m_46859_(pos) || !level.m_46859_(pos.m_7494_()) || !towny.allowed(level,pos))continue;
            BlockState ground=level.m_8055_(pos.m_7495_());
            ResourceLocation groundId=ForgeRegistries.BLOCKS.getKey(ground.m_60734_());
            if(groundId==null || !Set.of("minecraft:grass_block","minecraft:dirt","minecraft:coarse_dirt","minecraft:podzol","minecraft:mud").contains(groundId.toString()))continue;
            float temperature=level.m_204166_(pos).m_203334_().m_47554_();
            var choices=plants.stream().filter(p -> climate(p.climate,temperature)).toList();
            if(choices.isEmpty())continue;
            BlockState state=choices.get(random.m_188503_(choices.size())).block.m_49966_();
            if(!state.m_60710_(level,pos) || !towny.allowed(level,pos))continue;
            if(state.m_60734_() instanceof DoublePlantBlock) DoublePlantBlock.m_153173_(level,state,pos,3);
            else if(!level.m_46597_(pos,state))continue;
            data.planted(key,level.m_46467_());return;
        }
    }
    private static boolean climate(String climate,float temperature) {
        return switch(climate) { case "cold" -> temperature<.4f; case "hot" -> temperature>=.9f; case "warm" -> temperature>=.6f; case "temperate" -> temperature>=.2f && temperature<1; default -> true; };
    }
    private static boolean lapis(BlockState state) {
        ResourceLocation id=ForgeRegistries.BLOCKS.getKey(state.m_60734_());
        return id!=null && (id.toString().equals("minecraft:lapis_ore") || id.toString().equals("minecraft:deepslate_lapis_ore"));
    }
    private void ore(ServerLevel level,int cx,int cz,long key,RenewalData data) {
        if(salt==null || salt.m_49966_().m_60795_() || data.spent(key) || !RenewalPolicy.eligible(level.m_7328_(),cx,cz,RulesConfig.SALT_PERCENT.get()))return;
        int bottom=Math.max(level.m_141937_(),RulesConfig.ORE_MIN_Y.get());
        int top=Math.min(level.m_151558_()-1,RulesConfig.ORE_MAX_Y.get());
        if(top<bottom)return;
        var random=level.m_213780_();
        for(int i=0;i<RulesConfig.ORE_SAMPLES.get();i++) {
            BlockPos pos=new BlockPos(cx*16+random.m_188503_(16),bottom+random.m_188503_(top-bottom+1),cz*16+random.m_188503_(16));
            if(!lapis(level.m_8055_(pos)) || !towny.allowed(level,pos))continue;
            var point=new RenewalPolicy.Pos(pos.m_123341_(),pos.m_123342_(),pos.m_123343_());
            boolean reserve=RenewalPolicy.leavesReserve(point,
                p -> p.y()>=level.m_141937_() && p.y()<level.m_151558_() && loaded(level,p.x()>>4,p.z()>>4),
                p -> lapis(level.m_8055_(new BlockPos(p.x(),p.y(),p.z()))));
            if(reserve && !data.spent(key) && towny.allowed(level,pos) && level.m_46597_(pos,salt.m_49966_())) { data.spend(key); return; }
        }
    }
}
