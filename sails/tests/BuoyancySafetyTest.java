import com.quintonc.vs_sails.ship.BuoyancySafety;

public final class BuoyancySafetyTest {
    static int checks;
    static void check(boolean value,String name) { checks++; if(!value)throw new AssertionError(name); }
    public static void main(String[] args) {
        for(int blocks:new int[]{1,16,256,1000000}) for(double density:new double[]{.1,100,500,20000}) {
            double mass=blocks*density;
            for(double speed:new double[]{-100,-2,0,1,2,4,1000}) {
                double f=BuoyancySafety.factor(blocks,0,.125,.0625,mass,blocks,1,speed);
                check(Double.isFinite(f) && f>=0 && f<=2,"finite bounded multiplier");
                double next=speed+(1000.0*blocks*f/mass-1)*10/60;
                check(next<=Math.max(2.0,speed)+1e-6,"worst-case fully submerged lift cannot accelerate past rise-speed limit");
                if(density<99)check(1000.0*blocks*f <= 1.5*mass+1e-6,"unmigrated near-zero buoy mass remains conservatively limited");
                check(BuoyancySafety.factor(blocks,0,.125,.0625,mass,blocks,0,speed)==0,"no air lift");
                if(speed>=4)check(f==0,"excess upward motion receives no additional lift");
            }
        }
        double low=BuoyancySafety.factor(20,0,.125,.0625,2000,20,1,0);
        double fast=BuoyancySafety.factor(20,0,.125,.0625,2000,20,1,3);
        check(fast<low,"rise feedback reduces lift");
        check(BuoyancySafety.factor(Integer.MAX_VALUE,Integer.MAX_VALUE,10,10,1,1,1,0)<=.0015,"huge or corrupt block counts cannot rocket a ship");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1}) {
            check(BuoyancySafety.factor(1,0,.125,0,bad,1,1,0)==0,"invalid mass fails closed");
            check(BuoyancySafety.factor(1,0,.125,0,1,bad,1,0)==0,"invalid or uncounted volume fails closed");
        }
        check(!BuoyancySafety.safeForFluidDrag(1.6,16),"legacy buoy mass disables unstable fluid drag until remass");
        check(BuoyancySafety.safeForFluidDrag(1600,16),"new buoy density restores native fluid drag");
        check(!BuoyancySafety.safeForFluidDrag(Double.NaN,16),"invalid density fails closed");
        // A stationary light hull keeps native partial-immersion lift instead of
        // being forced to sink until two-thirds of its displacement is underwater.
        check(BuoyancySafety.factor(0,0,.125,0,500,1,.5,0)==1,"normal wooden hull keeps its native waterline");
        check(BuoyancySafety.factor(0,0,.125,0,100,1,.1,0)==1,"light hull is not capped to 0.15 at rest");
        for(double dt:new double[]{1.0/20,1.0/60,1.0/120,1.0/240})
            for(double density:new double[]{.1,100,300,700,20000})for(double fraction:new double[]{.01,.1,.5,1})
                for(double velocity:new double[]{-20,0,1,2,4,20}) {
                    double factor=BuoyancySafety.factor(100,0,.125,0,density,1,1,velocity,dt);
                    double next=velocity+(1000*fraction*factor/density-1)*10*dt;
                    check(next<=Math.max(2,velocity)+1e-6,"speed bound for all immersion depths and physics rates");
                }
        System.out.println("BUOYANCY_SAFETY_PASS assertions="+checks);
    }
}
