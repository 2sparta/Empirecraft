import ua.empirecraft.leaves.SeasonCurve;
public final class SeasonCurveTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args) {
        int d=57600*30;
        check(SeasonCurve.bareFraction(0,d)==1);
        check(SeasonCurve.bareFraction(3*d,d)==0);
        check(SeasonCurve.bareFraction(6*d,d)==0);
        check(SeasonCurve.bareFraction(9*d,d)==1);
        check(SeasonCurve.bareFraction(11*d,d)==1);
        for(int t=0;t<3*d;t+=107) {
            double spring=SeasonCurve.bareFraction(t,d), autumn=SeasonCurve.bareFraction(t+6*d,d);
            check(Math.abs(spring+autumn-1)<1e-12);
        }
        int half=0;
        for(long p=0;p<100000;p++) {
            check(!SeasonCurve.bare(p,0));check(SeasonCurve.bare(p,1));
            check(SeasonCurve.threshold(p)>0&&SeasonCurve.threshold(p)<1);
            if(SeasonCurve.bare(p,.5))half++;
        }
        check(half>49000&&half<51000);
        System.out.println("SEASON_CURVE_PASS 100000 positions; seasonal boundaries, monotonicity, balanced gradual shedding");
    }
}
