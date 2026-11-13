package ua.empirecraft.tfc;

import java.util.List;

/** Stable geology independent of ore RNG and chunk visitation order. */
public final class MaterialPalette {
    public static final List<String> ROCKS=List.of("granite","diorite","gabbro","shale","claystone","limestone",
        "conglomerate","dolomite","chert","chalk","rhyolite","basalt","andesite","dacite","quartzite",
        "slate","phyllite","schist","gneiss","marble");
    public static final List<String> SANDS=List.of("white","yellow","red","pink","brown","green","black");
    public static final List<String> SOILS=List.of("silt","loam","sandy_loam","silty_loam");
    public enum Surface { SOIL, CLAY, KAOLIN, PEAT }
    public record Plan(String upper,String middle,String lower,String sand,String soil,Surface surface,long seed) {}
    private MaterialPalette() {}
    public static long mix(long n){n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;n=(n^(n>>>27))*0x94d049bb133111ebL;return n^(n>>>31);}
    private static int index(long n,int size){return (int)Math.floorMod(n,(long)size);}
    public static Plan plan(long seed,int x,int z,float temperature,boolean wet){
        long region=mix(seed^((long)Math.floorDiv(x,4)*341873128712L)^((long)Math.floorDiv(z,4)*132897987541L));
        int rock=index(region,ROCKS.size());
        long local=mix(seed^((long)x*341873128712L)^((long)z*132897987541L)^0x4d4154455249414cL);
        int roll=index(mix(local),100);
        Surface surface=temperature>=0.4f&&roll<(temperature>=0.8f?12:4)?Surface.KAOLIN
            :wet&&roll>=12&&roll<28?Surface.PEAT
            :roll>=28&&roll<(wet?52:36)?Surface.CLAY:Surface.SOIL;
        return new Plan(ROCKS.get(rock),ROCKS.get((rock+7)%20),ROCKS.get((rock+14)%20),
            SANDS.get(index(mix(region^0x53414e44L),7)),SOILS.get(index(mix(local^0x534f494cL),4)),surface,local);
    }
}
