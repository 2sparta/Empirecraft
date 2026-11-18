package ua.empirecraft.townymap;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.earth.EarthMod;
import ua.empirecraft.earth.Geography;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.climate.KoppenClimateClassification;

/** Samples one already loaded chunk from the server-thread local survey. */
final class ClimateObservations {
    private static final Map<String,String> NAMES = Map.ofEntries(
        Map.entry("ARCTIC","Арктичний"),Map.entry("TUNDRA","Тундра"),Map.entry("SUBARCTIC","Субарктичний"),
        Map.entry("COLD_DESERT","Холодна пустеля"),Map.entry("HOT_DESERT","Спекотна пустеля"),
        Map.entry("TEMPERATE","Помірний"),Map.entry("SUBTROPICAL","Субтропічний"),
        Map.entry("HUMID_SUBTROPICAL","Вологий субтропічний"),Map.entry("HUMID_OCEANIC","Вологий океанічний"),
        Map.entry("HUMID_SUBARCTIC","Вологий субарктичний"),Map.entry("TROPICAL_SAVANNA","Тропічна савана"),
        Map.entry("TROPICAL_RAINFOREST","Тропічний дощовий ліс"));
    private static final int[] COLORS = {0xcce8f4,0xa7c7bd,0x86a79e,0xbaa98b,0xe7c16f,0x8eae77,
        0xb3bc63,0x64ab77,0x699eb1,0x789eaa,0xc6b45f,0x32866c};

    static Set<String> earthDimensions() {
        return ModList.get().isLoaded("empirecraft_earth") ? EarthLayers.dimensions() : Set.of();
    }

    static boolean displayable(ClaimKnowledge.Label label,Set<String> earthDimensions) {
        return !label.mode().environmental() || !earthDimensions.contains(label.dimension())
            || label.id().startsWith("earth/") || label.id().startsWith("sea/");
    }

    static List<ClaimKnowledge.Fragment> sample(ServerLevel level, LevelChunk loaded, ClaimKnowledge.Chunk chunk) {
        if (ModList.get().isLoaded("empirecraft_earth")) {
            var result = EarthLayers.sample(level,loaded,chunk);
            if (result != null) return result;
        }
        return sampleTfc(level,chunk);
    }

    private static List<ClaimKnowledge.Fragment> sampleTfc(ServerLevel level, ClaimKnowledge.Chunk chunk) {
        BlockPos position = new BlockPos(chunk.x()*16+8,64,chunk.z()*16+8);
        if (!level.hasChunkAt(position)) return List.of();
        try {
            float temperature = Climate.getAverageTemperature(level,position), rain = Climate.getRainfall(level,position);
            if (!Float.isFinite(temperature) || !Float.isFinite(rain)) return List.of();
            var classification = KoppenClimateClassification.classify(temperature,rain);
            String name = NAMES.getOrDefault(classification.name(),classification.name());
            int rainfallBand = rain < 75 ? 0 : rain < 150 ? 1 : rain < 250 ? 2 : rain < 350 ? 3 : 4;
            String rainName = List.of("Посушлива зона","Суха зона","Помірні опади","Волога зона","Дуже волога зона").get(rainfallBand);
            var cells = Set.of(new ClaimGeometry.Cell(chunk.x(),chunk.z()));
            // Group by climate class so nearby chunks form continuous overlays.
            var climate = new ClaimKnowledge.Label(MapMode.CLIMATE,"climate/"+classification.name(),name,chunk.dimension(),
                    COLORS[classification.ordinal()],16,"Клімат TFC · "+name,0);
            var rainfall = new ClaimKnowledge.Label(MapMode.RAINFALL,"rainfall/"+rainfallBand,rainName,chunk.dimension(),
                    new int[]{0xd6b46c,0xb8bd7b,0x87b7a1,0x529db5,0x3975a8}[rainfallBand],16,"Опади TFC · "+rainName+" · "
                    +List.of("індекс до 75","індекс 75–150","індекс 150–250","індекс 250–350","індекс від 350").get(rainfallBand),0);
            return List.of(new ClaimKnowledge.Fragment(climate,cells),new ClaimKnowledge.Fragment(rainfall,cells));
        } catch (LinkageError unavailable) { return List.of(); }
    }
    private static final class EarthLayers {
        private static final int SIZE=8;
        private static final String[] RAIN_NAMES={"Посушлива зона","Суха зона","Помірні опади","Волога зона","Дуже волога зона"};
        private static final String[] RAIN_DETAILS={"до 250 мм/рік","250–500 мм/рік","500–900 мм/рік",
            "900–1600 мм/рік","від 1600 мм/рік"};
        private static final int[] RAIN_COLORS={0xd6b46c,0xb8bd7b,0x87b7a1,0x529db5,0x3975a8};

        static Set<String> dimensions() { return EarthMod.data.dimensions; }

        static List<ClaimKnowledge.Fragment> sample(ServerLevel level,LevelChunk loaded,ClaimKnowledge.Chunk chunk) {
            Geography data=EarthMod.data;
            if (!data.dimensions.contains(chunk.dimension())) return null;
            Map<ClaimKnowledge.Label, Set<ClaimGeometry.Cell>> grouped=new LinkedHashMap<>();
            for (int dx=0; dx<16; dx+=SIZE) for (int dz=0; dz<16; dz+=SIZE) {
                int x=chunk.x()*16+dx+SIZE/2, z=chunk.z()*16+dz+SIZE/2;
                var point=data.coordinates(x,z);
                if (!data.inside(point)) continue;
                int floor=loaded.getHeight(Heightmap.Types.OCEAN_FLOOR,x&15,z&15);
                int surface=loaded.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x&15,z&15);
                if (surface<level.getMinBuildHeight()) continue;
                var water=findSurfaceWater(loaded,x,surface,z);
                EarthMapClimate.Band sea=null;
                if (water!=null) {
                    var biome=loaded.getNoiseBiome(x>>2,water.getY()>>2,z>>2);
                    String biomeId=biome.unwrapKey().map(key->key.location().getPath()).orElse("");
                    String fluid=String.valueOf(ForgeRegistries.FLUIDS.getKey(loaded.getFluidState(water).getType()));
                    sea=EarthMapClimate.sea(data,point,true,biome.is(BiomeTags.IS_OCEAN),
                        fluid.equals("tfc:salt_water"),biome.is(BiomeTags.IS_RIVER)||biomeId.contains("lake"));
                }
                var cell=new ClaimGeometry.Cell(Math.floorDiv(x,SIZE),Math.floorDiv(z,SIZE));
                if (sea!=null) {
                    label(grouped,cell,chunk,MapMode.CLIMATE,"sea/"+sea.id(),sea.name(),sea.color(),"Морська акваторія");
                    label(grouped,cell,chunk,MapMode.RAINFALL,"sea/"+sea.id(),sea.name(),sea.color(),"Морська акваторія");
                    continue;
                }
                var land=EarthMapClimate.land(data,point,Math.max(floor,level.getMinBuildHeight()));
                var climate=land.climate();
                label(grouped,cell,chunk,MapMode.CLIMATE,"earth/"+climate.id(),climate.name(),climate.color(),"Клімат за географією Earth");
                double rain=land.annualRain();
                int band=rain<250?0:rain<500?1:rain<900?2:rain<1600?3:4;
                label(grouped,cell,chunk,MapMode.RAINFALL,"earth/rain/"+band,RAIN_NAMES[band],RAIN_COLORS[band],RAIN_DETAILS[band]);
            }
            return grouped.entrySet().stream().map(entry->new ClaimKnowledge.Fragment(entry.getKey(),entry.getValue())).toList();
        }

        private static BlockPos findSurfaceWater(LevelChunk chunk,int x,int surface,int z) {
            BlockPos top=new BlockPos(x,surface,z);
            if (chunk.getFluidState(top).is(FluidTags.WATER)) return top;
            var below=new BlockPos(x,surface-1,z);
            // A surface ice block still belongs to the water immediately below it.
            var block=chunk.getBlockState(top).getBlock();
            var id=ForgeRegistries.BLOCKS.getKey(block);
            return id!=null && id.getPath().contains("ice") && chunk.getFluidState(below).is(FluidTags.WATER) ? below : null;
        }

        private static void label(Map<ClaimKnowledge.Label, Set<ClaimGeometry.Cell>> grouped,ClaimGeometry.Cell cell,
                                  ClaimKnowledge.Chunk chunk,MapMode mode,String id,String name,int color,String detail) {
            var label=new ClaimKnowledge.Label(mode,id,name,chunk.dimension(),color,SIZE,detail,0);
            grouped.computeIfAbsent(label,ignored->new LinkedHashSet<>()).add(cell);
        }
    }
}
