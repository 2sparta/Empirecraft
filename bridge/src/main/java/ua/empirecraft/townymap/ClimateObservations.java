package ua.empirecraft.townymap;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.climate.KoppenClimateClassification;

/** Samples only the occupied, already loaded chunk, in the server thread. No world generation or map probing. */
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

    static List<ClaimKnowledge.Fragment> sample(ServerLevel level, ClaimKnowledge.Chunk chunk) {
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
            // Group by climate class; measured values are in a local card, not in overlay identity.
            var climate = new ClaimKnowledge.Label(MapMode.CLIMATE,"climate/"+classification.name(),name,chunk.dimension(),
                    COLORS[classification.ordinal()],16,"Клімат TFC · "+name,0);
            var rainfall = new ClaimKnowledge.Label(MapMode.RAINFALL,"rainfall/"+rainfallBand,rainName,chunk.dimension(),
                    new int[]{0xd6b46c,0xb8bd7b,0x87b7a1,0x529db5,0x3975a8}[rainfallBand],16,"Опади TFC · "+rainName+" · "
                    +List.of("менше 75 мм","75–150 мм","150–250 мм","250–350 мм","від 350 мм").get(rainfallBand),0);
            return List.of(new ClaimKnowledge.Fragment(climate,cells),new ClaimKnowledge.Fragment(rainfall,cells));
        } catch (LinkageError unavailable) { return List.of(); }
    }
}
