package ua.empirecraft.leaves;

/** Allocation-free colour policy shared by chunk workers; no world or clock access. */
public final class SeasonVisualPolicy {
    private SeasonVisualPolicy() {}
    public static double phase(int cycle,int duration) {
        return duration<=0?3:Math.floorMod((long)cycle,12L*duration)/(double)duration;
    }
    private static long mix(long x) {
        x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;
        x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);
    }
    private static double random(long x) {return (mix(x)>>>11)*0x1.0p-53;}
    private static long position(int x,int y,int z) {return x*0x632be59bd9b4e019L^y*0x9e3779b97f4a7c15L^z*0x85157af5d66d3e7bL;}
    public static double autumn(double phase) {
        // Late-summer onset, a mixed autumn, then retain muted autumn colour in winter.
        if(phase<5.6)return 0;
        return Math.min(1,(phase-5.6)/1.4);
    }
    public static int foliage(int base,int x,int y,int z,int species,double phase) {
        long local=position(x,y,z)^((long)species*0xd6e8feb86659fd93L);
        long patch=position(Math.floorDiv(x,3),Math.floorDiv(y,2),Math.floorDiv(z,3))^species;
        double start=5.6+0.95*random(patch^0x8f3e287dL)+0.38*random(local^0x1db5a9e1L);
        double amount=Math.max(0,Math.min(1,(phase-start)/0.75));
        // Fresh spring leaves keep their native green; autumn colour must not leak into regrowth.
        if(phase<5.6)amount=0;
        // Palette choice and onset use different salts from leaf-fall thresholds.
        // A yellow leaf can fall before its orange or brown neighbour.
        double choice=0.55*random(patch^0x46b82f19L)+0.45*random(local^0xb34126adL);
        int target=choice<0.28?0xeac447:choice<0.49?0xd99a2b:choice<0.70?0xd16a26:choice<0.86?0x995229:0x654126;
        double shade=0.86+0.14*random(local^0xf33a47b9L);
        target=rgb((int)(((target>>>16)&255)*shade),(int)(((target>>>8)&255)*shade),(int)((target&255)*shade));
        return blend(base<0?0xffffff:base,target,amount);
    }
    public static int blend(int a,int b,double t) {
        t=Math.max(0,Math.min(1,t));
        return rgb((int)(((a>>>16)&255)*(1-t)+((b>>>16)&255)*t),(int)(((a>>>8)&255)*(1-t)+((b>>>8)&255)*t),(int)((a&255)*(1-t)+(b&255)*t));
    }
    public static int rgb(int r,int g,int b) {return (Math.max(0,Math.min(255,r))<<16)|(Math.max(0,Math.min(255,g))<<8)|Math.max(0,Math.min(255,b));}
    public static double winter(double phase) {
        if(phase<1)return 1-phase;
        return Math.max(0,Math.min(1,(phase-8)/1.0));
    }
    public static float[] sky(float r,float g,float b,double phase,float rain) {
        double winter=winter(phase),autumn=phase>=6&&phase<9?0.16:0;
        float light=0.28f*r+0.57f*g+0.15f*b;
        float pale=light*(1.12f-0.12f*rain);
        float t=(float)Math.min(0.86,winter*(0.65+0.20*rain)+autumn);
        // Preserve luminosity so a winter night never becomes a bright grey day.
        return new float[]{clamp(r+(pale-r)*t),clamp(g+(pale*1.015f-g)*t),clamp(b+(pale*1.035f-b)*t)};
    }
    private static float clamp(float v) {return Math.max(0,Math.min(1,v));}
}
