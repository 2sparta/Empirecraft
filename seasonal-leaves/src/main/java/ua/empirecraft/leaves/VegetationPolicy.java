package ua.empirecraft.leaves;
import java.util.Set;
/** Pure policy shared by seasonal ground cover and the existing wilderness planter. */
public final class VegetationPolicy {
    public static final Set<String> RARE = Set.of("nirvana:wild_hemp", "farmersrespite:wild_coffee_bush", "rusticdelight:wild_coffee");
    public static final Set<String> SOILS = Set.of("minecraft:grass_block","minecraft:dirt","minecraft:coarse_dirt","minecraft:podzol","minecraft:rooted_dirt","minecraft:moss_block","minecraft:mycelium","minecraft:mud","minecraft:sand","minecraft:red_sand");
    public static double visibility(int mask, int cycleTicks, int duration) {
        if(duration <= 0) return 1;
        double phase=Math.floorMod((long)cycleTicks,12L*duration)/(double)duration;
        int season=(int)phase/3;
        if((mask & (1<<season))==0)return 0;
        double within=phase-season*3;
        if(within<1 && (mask & (1<<((season+3)%4)))==0)return within;
        if(within>2 && (mask & (1<<((season+1)%4)))==0)return 3-within;
        return 1;
    }
    public static boolean rareChunk(long seed,int x,int z,int oneIn) {
        return SeasonCurve.threshold(seed ^ (((long)x)<<32) ^ (z & 0xffffffffL) ^ 0x504c414e54534cL) < 1.0/Math.max(1,oneIn);
    }
    public static boolean climate(String climate,float temperature) {
        return switch(climate) {
            case "cold" -> temperature<.4f;
            case "hot" -> temperature>=.9f;
            case "warm" -> temperature>=.6f;
            case "temperate" -> temperature>=.2f && temperature<1;
            default -> true;
        };
    }
    private VegetationPolicy(){}
}
