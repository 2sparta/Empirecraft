import ua.empirecraft.leaves.VegetationPolicy;
public final class VegetationPolicyTest {
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args){
        int d=57600*30;
        for(int mask=0;mask<16;mask++)for(int season=0;season<4;season++){
            double v=VegetationPolicy.visibility(mask,(season*3+1)*d,d);
            check(v==(((mask&(1<<season))!=0)?1:0),"season mask");
        }
        check(VegetationPolicy.visibility(7,10*d,d)==0,"winter grass absent");
        check(VegetationPolicy.visibility(7,d/2,d)==.5,"spring gradual return");
        check(VegetationPolicy.visibility(7,8*d+d/2,d)==.5,"autumn gradual disappearance");
        int rare=0,otherSeed=0;
        for(int x=-256;x<256;x++)for(int z=-256;z<256;z++){
            boolean a=VegetationPolicy.rareChunk(7331,x,z,128);
            check(a==VegetationPolicy.rareChunk(7331,x,z,128),"stable across restarts");
            if(a)rare++;
            if(a!=VegetationPolicy.rareChunk(7332,x,z,128))otherSeed++;
        }
        check(rare>1700 && rare<2400,"rare chunk frequency ~1/128");
        check(otherSeed>2000,"world seed affects habitats");
        check(!VegetationPolicy.SOILS.contains("minecraft:farmland"),"farms excluded");
        check(VegetationPolicy.RARE.size()==3,"one combined rare group");
        System.out.println("VEGETATION_POLICY_PASS 262144 chunks; rare eligible="+rare+"; seasonal masks, fade, climate and soil rules");
    }
}
