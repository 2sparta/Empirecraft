import com.quintonc.vs_sails.ship.WaterSupport;

public final class WaterSupportTest {
    static int checks;
    static void check(boolean value,String text) {checks++;if(!value)throw new AssertionError(text);}
    static void raft(int buoys,double extraMass,double dt,double start) {
        double mass=buoys*100+extraMass, area=buoys;
        double multiplier=WaterSupport.legacyMultiplier(buoys,0,.125,0,mass,1);
        double expected=Math.max(.35,(buoys*.1+extraMass)/(1000*area*(1+buoys*.125)));
        double depth=start,v=0,min=Double.POSITIVE_INFINITY,max=-min,maxUp=0,maxDown=0;
        for(int i=0;i<(int)(40/dt);i++) {
            double volume=area*Math.max(0,Math.min(1,depth));
            double force=WaterSupport.force(mass,volume,area,multiplier,v,dt);
            v+=(force/mass-10)*dt;depth-=v*dt;maxUp=Math.max(maxUp,v);maxDown=Math.max(maxDown,-v);
            check(Double.isFinite(depth)&&Double.isFinite(v),"finite dynamics");
            if(i>30/dt) {min=Math.min(min,depth);max=Math.max(max,depth);}
            check(depth>=-1e-6,"buoys never leap above the waterline");
        }
        check(Math.abs(depth-expected)<.001,"minimum visible equilibrium draft: "+depth+" / "+expected);
        check(max-min<.001,"no repeated surface oscillation");
        check(maxUp<=1.000001,"submerged rise speed is limited");
        check(maxDown<4,"settling into the deeper draft has bounded descent: "+maxDown+" buoys="+buoys+" dt="+dt+" mass="+mass+" start="+start);
    }
    public static void main(String[] args) {
        for(int buoys:new int[]{1,16,256})for(double dt:new double[]{1.0/20,1.0/60,1.0/120})
            for(double weight:new double[]{0,buoys*250.0})for(double depth:new double[]{.1,.9})raft(buoys,weight,dt,depth);
        for(double speed:new double[]{-50,-1,0,1,10})check(WaterSupport.force(1600,0,16,3000,speed,1.0/60)==0,"zero lift and damping outside water");
        double m=WaterSupport.legacyMultiplier(16,0,.125,0,1600,1);
        check(Math.abs(m-3000)<1e-6,"old 0.1 kg draft with stable 100 kg inertia");
        check(WaterSupport.legacyMultiplier(64,0,.125,0,6400,1)>m,"legacy buoy bonus has no new arbitrary twofold ceiling");
        check(WaterSupport.force(0,1,1,1,0,.05)==0,"invalid mass rejected");
        check(WaterSupport.force(1,1,1,1,0,Double.NaN)==0,"invalid timestep rejected");
        System.out.println("WATER_SUPPORT_PASS assertions="+checks);
    }
}
