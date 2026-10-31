package ua.empirecraft.leaves;
import net.minecraftforge.common.ForgeConfigSpec;
final class VegetationConfig {
    static ForgeConfigSpec.BooleanValue ENABLED,WILDERNESS,SPAWN,GROWTH;
    static ForgeConfigSpec.IntValue SCAN,CHANGES,DELAY,DENSITY,RARE_CHUNKS,RARE_DAYS,FAST_SCAN,FAST_CHANGES,FAST_DELAY,GROW_INTERVAL,GROW_DELAY,NATIVE_DELAY,TREE_DELAY,TREE_DISTANCE,TREE_AGE;
    static void define(ForgeConfigSpec.Builder b) {
        b.push("vegetation");
        GROWTH=b.comment("Global administrator switch for plant appearance, restoration and managed plant/tree growth. Saved by /plantgrowth on|off.").define("growthEnabled",true);
        ENABLED=b.define("enabled",true);
        WILDERNESS=b.comment("Require the existing World Rules Towny wilderness check. Unknown claims pause vegetation changes.").define("requireWilderness",true);
        SPAWN=b.define("regrowGroundCover",true);
        SCAN=b.defineInRange("positionsPerLevelTick",2048,64,65536);
        CHANGES=b.defineInRange("plantsPerLevelTick",4,1,128);
        DELAY=b.defineInRange("rescanDelayTicks",1200,20,72000);
        DENSITY=b.defineInRange("maxGroundCoverPerChunk",48,0,256);
        RARE_CHUNKS=b.comment("Combined wild hemp / wild coffee habitat: approximately one eligible chunk in N; maximum one live rare plant per chunk.").defineInRange("rareHabitatOneInChunks",128,1,65536);
        RARE_DAYS=b.comment("Minimum game days before rare plants can reappear after their last observation.").defineInRange("rareCooldownDays",7,0,365);
        FAST_SCAN=b.defineInRange("fastCleanupPositionsPerTick",32768,1024,65536);
        FAST_CHANGES=b.defineInRange("fastCleanupChangesPerTick",64,1,512);
        FAST_DELAY=b.defineInRange("fastCleanupRescanTicks",40,20,1200);
        GROW_INTERVAL=b.defineInRange("appearanceIntervalTicks",200,20,72000);
        GROW_DELAY=b.defineInRange("appearanceChunkDelayTicks",6000,200,720000);
        NATIVE_DELAY=b.defineInRange("nativePlantAttemptDelayTicks",2400,200,720000);
        TREE_DELAY=b.defineInRange("nativeTreeAttemptDelayTicks",12000,1200,720000);
        TREE_DISTANCE=b.defineInRange("naturalTreePlayerDistance",80,16,256);
        TREE_AGE=b.defineInRange("naturalTreeMinimumAgeTicks",24000,1200,720000);
        b.pop();
    }
    private VegetationConfig(){}
}
