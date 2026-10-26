package ua.empirecraft.leaves;
import net.minecraftforge.common.ForgeConfigSpec;
final class VegetationConfig {
    static ForgeConfigSpec.BooleanValue ENABLED,WILDERNESS,SPAWN;
    static ForgeConfigSpec.IntValue SCAN,CHANGES,DELAY,DENSITY,RARE_CHUNKS,RARE_DAYS;
    static void define(ForgeConfigSpec.Builder b) {
        b.push("vegetation");
        ENABLED=b.define("enabled",true);
        WILDERNESS=b.comment("Require the existing World Rules Towny wilderness check. Unknown claims pause vegetation changes.").define("requireWilderness",true);
        SPAWN=b.define("regrowGroundCover",true);
        SCAN=b.defineInRange("positionsPerLevelTick",2048,64,65536);
        CHANGES=b.defineInRange("plantsPerLevelTick",4,1,128);
        DELAY=b.defineInRange("rescanDelayTicks",1200,20,72000);
        DENSITY=b.defineInRange("maxGroundCoverPerChunk",48,0,256);
        RARE_CHUNKS=b.comment("Combined wild hemp / wild coffee habitat: approximately one eligible chunk in N; maximum one live rare plant per chunk.").defineInRange("rareHabitatOneInChunks",128,1,65536);
        RARE_DAYS=b.comment("Minimum game days before rare plants can reappear after their last observation.").defineInRange("rareCooldownDays",7,0,365);
        b.pop();
    }
    private VegetationConfig(){}
}
