package ua.empirecraft.kitchen.terrain;

import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.dries007.tfc.common.blocks.SandstoneBlockType;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.world.chunkdata.RockData;
import net.dries007.tfc.world.settings.RockSettings;
import ua.empirecraft.tfc.MaterialPalette;

/** Native stone regrowth uses the existing terrain pass's geology in worlds without TFC's generator. */
public final class RockQueries {
    private static final Map<Rock,RockSettings> SETTINGS=new ConcurrentHashMap<>();
    private RockQueries() { }
    public static RockSettings rock(RockData data,BlockPos pos,ServerLevel level) {
        if (((RockGeneratorState)data).empirecraft$hasRockGenerator()) return data.getRock(pos);
        long seed=level.getSeed()^((long)level.dimension().location().hashCode()<<32);
        // Moisture/temperature choose soil and clay, never the plan's upper rock.
        var plan=MaterialPalette.plan(seed,pos.getX()>>4,pos.getZ()>>4,0,false);
        Rock rock=Rock.valueOf(plan.upper().toUpperCase(Locale.ROOT));
        return SETTINGS.computeIfAbsent(rock,RockQueries::settings);
    }
    private static RockSettings settings(Rock rock) {
        var blocks=TFCBlocks.ROCK_BLOCKS.get(rock);
        return new RockSettings(blocks.get(Rock.BlockType.RAW).get(),blocks.get(Rock.BlockType.HARDENED).get(),
            blocks.get(Rock.BlockType.GRAVEL).get(),blocks.get(Rock.BlockType.COBBLE).get(),
            TFCBlocks.SAND.get(rock.getSandType()).get(),TFCBlocks.SANDSTONE.get(rock.getSandType()).get(SandstoneBlockType.RAW).get(),
            Optional.of(blocks.get(Rock.BlockType.SPIKE).get()),Optional.of(blocks.get(Rock.BlockType.LOOSE).get()),Optional.of(blocks.get(Rock.BlockType.MOSSY_LOOSE).get()));
    }
}
