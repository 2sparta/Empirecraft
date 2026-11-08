import java.util.*;
import ua.empirecraft.leaves.*;

public final class SeasonEffectsTest {
    static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        int base=0x48a332;Set<Integer> crown=new HashSet<>(),species=new HashSet<>();
        int yellowFallen=0,yellowRetained=0,brown=0,orange=0;
        for(int x=-25;x<25;x++)for(int z=-25;z<25;z++)for(int y=64;y<72;y++) {
            int c=SeasonVisualPolicy.foliage(base,x,y,z,119,7.9);
            check(c==SeasonVisualPolicy.foliage(base,x,y,z,119,7.9),"colour must not flicker");
            check(SeasonVisualPolicy.foliage(base,x,y,z,119,4)==base,"summer keeps original tint");
            for(double phase:new double[]{0,1,2,3,5.5})check(SeasonVisualPolicy.foliage(base,x,y,z,119,phase)==base,"spring regrowth keeps its native tint");
            int red=(c>>>16)&255,green=(c>>>8)&255,blue=c&255;
            boolean yellow=green>115&&red>green&&green>blue*2;
            if(yellow&&SeasonCurve.leafBare(new netlessPosition(x,y,z).packed(),.5,4))yellowFallen++;
            if(yellow&&!SeasonCurve.leafBare(new netlessPosition(x,y,z).packed(),.5,1))yellowRetained++;
            if(red<150&&green<100)brown++;if(red>150&&green<145)orange++;
            if(x>=0&&x<7&&z>=0&&z<7)crown.add(c);
        }
        for(int type=0;type<20;type++)species.add(SeasonVisualPolicy.foliage(base,4,67,4,type,7.9));
        check(crown.size()>30,"one crown must have visibly varied leaves");
        check(species.size()>10,"species influence the palette");
        check(yellowFallen>100&&yellowRetained>100&&brown>100&&orange>100,"palette and leaf fall must be independent");
        int day=57600;Random random=new Random(103);
        for(int i=0;i<100000;i++) {
            long seed=random.nextLong();int winter=WeatherPolicy.duration(10,true,day,seed);
            check(winter>=2*day&&winter<=5*day,"winter precipitation must last 2-5 calendar days");
            check(WeatherPolicy.duration(4,true,day,seed)<day/3,"summer rain must be shorter");
            check(WeatherPolicy.duration(4,false,day,seed)>=day,"summer has longer dry intervals");
            float r=random.nextFloat(),g=random.nextFloat(),b=random.nextFloat();
            var c=SeasonVisualPolicy.sky(r,g,b,10,1);
            for(float f:c)check(Float.isFinite(f)&&f>=0&&f<=1,"sky RGB bounds");
        }
        var night=SeasonVisualPolicy.sky(.01f,.015f,.025f,10,1);
        check(night[0]<.04&&night[1]<.04&&night[2]<.04,"winter night must remain dark");
        var summer=SeasonVisualPolicy.sky(.2f,.4f,.8f,4,0);
        check(summer[0]==.2f&&summer[1]==.4f&&summer[2]==.8f,"summer native sky remains intact");
        check(SeasonVisualPolicy.phase(-1,100)>11.9&&SeasonVisualPolicy.phase(1200,100)==0,"cycle wrapping");
        System.out.println("SEASON_EFFECTS_PASS mixed crowns/species, stable colours, yellow-leaf shedding independent of colour; 100000 weather durations; dark winter nights");
    }
    // Same Minecraft BlockPos packing, with no runtime dependency in the pure policy test.
    record netlessPosition(int x,int y,int z) {long packed(){return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|((long)y&0xfffL);}}
}
