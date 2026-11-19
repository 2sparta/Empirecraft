package ua.empirecraft.tfc;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.earth.EarthMod;

/** Bounded patches in loaded wilderness, never a terrain generator or an ore refill. */
public final class NaturalMaterials {
    public static final int VERSION=1,MAX_CHANGES=1152;
    private static final Set<Block> SOIL=Set.of(Blocks.DIRT,Blocks.GRASS_BLOCK,Blocks.COARSE_DIRT,Blocks.PODZOL,Blocks.CLAY,Blocks.MUD);
    private static final Set<Block> SAND=Set.of(Blocks.SAND,Blocks.RED_SAND);
    private static final Set<Block> GROUND=Set.of(Blocks.DIRT,Blocks.GRASS_BLOCK,Blocks.COARSE_DIRT,Blocks.PODZOL,
        Blocks.STONE,Blocks.GRANITE,Blocks.DIORITE,Blocks.ANDESITE);
    private final ServerLevel level;private final LevelChunk chunk;
    private final Predicate<BlockPos> protection;
    private final Map<Long,Boolean> columns=new HashMap<>();
    private final int ox,oz;private int changed;
    private NaturalMaterials(ServerLevel level,LevelChunk chunk,Predicate<BlockPos> protection){
        this.level=level;this.chunk=chunk;this.protection=protection;
        ox=chunk.getPos().getMinBlockX();oz=chunk.getPos().getMinBlockZ();
    }
    public static int generate(ServerLevel level,LevelChunk chunk,Predicate<BlockPos> protection){
        return new NaturalMaterials(level,chunk,protection).generate();
    }
    public static boolean wet(ServerLevel level,LevelChunk chunk){
        var biome=level.getBiome(chunk.getPos().getMiddleBlockPosition(64));
        if(biome.is(BiomeTags.IS_RIVER)||biome.is(BiomeTags.IS_JUNGLE)||biome.is(Biomes.SWAMP)||biome.is(Biomes.MANGROVE_SWAMP))return true;
        // Inspect only this chunk. Ocean shores count; dry deserts do not become peat fields.
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            int y=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z);
            if(!chunk.getFluidState(new BlockPos(chunk.getPos().getMinBlockX()+x,y+1,chunk.getPos().getMinBlockZ()+z)).isEmpty())return true;
        }
        return false;
    }
    private int generate(){
        long seed=level.getSeed()^((long)level.dimension().location().hashCode()<<32);
        float temperature=level.getBiome(chunk.getPos().getMiddleBlockPosition(64)).value().getBaseTemperature();
        var plan=MaterialPalette.plan(seed,chunk.getPos().x,chunk.getPos().z,temperature,wet(level,chunk));
        Random random=new Random(plan.seed());
        String[] rocks={plan.upper(),plan.middle(),plan.lower()};int[] depths={12,44,92};
        int floor=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,8,8);
        for(int layer=0;layer<3;layer++){
            int cy=floor-depths[layer];
            if(cy<level.getMinBuildHeight()+3)continue;
            BlockState rock=block("rock/raw/"+rocks[layer]);
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=-1;dy<=1;dy++){
                if((dx*dx+dz*dz)/16.0+dy*dy/2.25>1)continue;
                BlockPos pos=new BlockPos(ox+8+dx,cy+dy,oz+8+dz);
                if(pos.getY()>chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,8+dx,8+dz)-8)continue;
                if(OneTimeTerrain.isHost(chunk.getBlockState(pos)))put(pos,rock);
            }
        }
        // Seed special deposits before the separate regional pass replaces ordinary soil.
        if(plan.surface()!=MaterialPalette.Surface.SOIL){
            BlockPos special=find(random,SOIL);
            if(special!=null)patch(special,2,plan.surface()==MaterialPalette.Surface.KAOLIN?4:3,plan.soil(),plan.surface());
        }
        BlockState loose=block("rock/loose/"+plan.upper());
        for(int attempt=0,placed=0;attempt<32&&placed<4;attempt++){
            int x=1+random.nextInt(14),z=1+random.nextInt(14);
            BlockPos pos=new BlockPos(ox+x,chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1,oz+z);
            BlockState below=chunk.getBlockState(pos.below());
            ResourceLocation id=ForgeRegistries.BLOCKS.getKey(below.getBlock());
            boolean soilGround=id!=null&&id.getNamespace().equals("tfc")&&(id.getPath().startsWith("grass/")||id.getPath().startsWith("dirt/")||id.getPath().startsWith("rock/raw/"));
            if(chunk.getBlockState(pos).isAir()&&(GROUND.contains(below.getBlock())||soilGround)&&loose.canSurvive(level,pos)&&put(pos,loose))placed++;
        }
        generateSand(seed);
        return changed;
    }

    private void generateSand(long seed) {
        SandGeography geography = ModList.get().isLoaded("empirecraft_earth") ? EarthSand.lookup(level) : null;
        var sedimentStates = new EnumMap<SandPalette.Sediment, BlockState>(SandPalette.Sediment.class);
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = ox + lx, z = oz + lz;
            int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz);
            BlockPos top = new BlockPos(x, y, z);
            if (!SAND.contains(chunk.getBlockState(top).getBlock()) || !permitted(top)) continue;

            var biome = chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2);
            int depth = chunk.getFluidState(top.above()).is(FluidTags.WATER)
                ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) - y : 0;
            var terrain = new SandPalette.Terrain(y, biome.is(BiomeTags.IS_RIVER),
                biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_OCEAN), depth);
            var location = geography == null ? null : geography.at(x, z);
            var sediment = SandPalette.select(seed, x, z, terrain, location);
            if (sediment == SandPalette.Sediment.ORIGINAL) continue;
            BlockState replacement = sedimentStates.computeIfAbsent(sediment, kind -> kind == SandPalette.Sediment.RED
                ? Blocks.RED_SAND.defaultBlockState() : block("sand/" + kind.name().toLowerCase(Locale.ROOT)));
            int thickness = SandPalette.depth(seed, x, z);
            for (int d = 0; d < thickness; d++) {
                BlockPos pos = top.below(d);
                BlockState existing = chunk.getBlockState(pos);
                if (!SAND.contains(existing.getBlock())) break;
                if (existing != replacement) put(pos, replacement);
            }
        }
    }

    private interface SandGeography {
        SandPalette.Location at(int x, int z);
    }

    /** Kept behind the mod check so the integration also runs without Earth Geography. */
    private static final class EarthSand {
        static SandGeography lookup(ServerLevel level) {
            var geography = EarthMod.data;
            if (!geography.dimensions.contains(level.dimension().location().toString())) return null;
            return (x, z) -> {
                var point = geography.coordinates(x, z);
                var geology = geography.at("geology", point);
                return new SandPalette.Location(point.lon(), point.lat(), geography.seaLevel,
                    geology == null ? "" : geology.text("sand", ""));
            };
        }
    }
    private BlockPos find(Random random,Set<Block> hosts){
        for(int attempt=0;attempt<64;attempt++){
            int x=3+random.nextInt(10),z=3+random.nextInt(10);
            BlockPos pos=new BlockPos(ox+x,chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,x,z),oz+z);
            if(hosts.contains(chunk.getBlockState(pos).getBlock())&&permitted(pos))return pos;
        }
        return null;
    }
    private void patch(BlockPos center,int radius,int depth,String soil,MaterialPalette.Surface kind){
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(dx*dx+dz*dz>radius*radius)continue;
            int x=center.getX()+dx,z=center.getZ()+dz;if(!inside(x,z))continue;
            int y=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,x-ox,z-oz);
            BlockPos top=new BlockPos(x,y,z);BlockState before=chunk.getBlockState(top);
            if(Math.abs(y-center.getY())>2||!SOIL.contains(before.getBlock()))continue;
            // Grass caps remain grass; submerged clay/peat deposits do not grow underwater grass.
            boolean grass=before.is(Blocks.GRASS_BLOCK)&&chunk.getFluidState(top.above()).isEmpty();
            for(int d=0;d<depth;d++){
                BlockPos pos=top.below(d);BlockState old=chunk.getBlockState(pos);
                if(!SOIL.contains(old.getBlock()))break;
                String path=switch(kind){
                    case SOIL -> (d==0&&grass?"grass/":"dirt/")+soil;
                    case CLAY -> (d==0&&grass?"clay_grass/":"clay/")+soil;
                    case PEAT -> d==0&&grass?"peat_grass":"peat";
                    case KAOLIN -> d==0&&grass?"kaolin_clay_grass":d<=1?"red_kaolin_clay":d==2?"pink_kaolin_clay":"white_kaolin_clay";
                };
                put(pos,block(path));
            }
        }
    }
    private boolean inside(int x,int z){return x>=ox&&x<ox+16&&z>=oz&&z<oz+16;}
    private boolean permitted(BlockPos pos){
        if(!inside(pos.getX(),pos.getZ())||pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()
            ||!level.getWorldBorder().isWithinBounds(pos)||chunk.getBlockEntity(pos)!=null)return false;
        long key=BlockPos.asLong(pos.getX(),0,pos.getZ());
        return columns.computeIfAbsent(key,k->protection.test(new BlockPos(pos.getX(),64,pos.getZ())));
    }
    private boolean put(BlockPos pos,BlockState state){
        if(changed>=MAX_CHANGES||!permitted(pos))return false;
        if(level.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE)){changed++;return true;}return false;
    }
    private static BlockState block(String path){
        Block b=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("tfc",path));
        if(b==null||b==Blocks.AIR)throw new IllegalStateException("Missing natural material tfc:"+path);
        return b.defaultBlockState();
    }
}
