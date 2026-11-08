package ua.empirecraft.packfixes;

/** Minecraft rotates +X towards -Z for a positive Y rotation. */
public final class FlagWind {
    private FlagWind() {}
    public static double yaw(double x,double z,double fallback) {
        return !Double.isFinite(x)||!Double.isFinite(z)||x*x+z*z<1e-12?fallback:-Math.toDegrees(Math.atan2(z,x));
    }
    public static double wrap(double angle) {return ((angle+180)%360+360)%360-180;}
    public static final class Turn {
        private double yaw,time;private boolean initialized;
        public double update(double target,double now) {
            if(!initialized||now<time){initialized=true;yaw=target;time=now;return yaw;}
            double dt=Math.min(.25,Math.max(0,(now-time)/20));time=now;
            yaw=wrap(yaw+wrap(target-yaw)*(1-Math.exp(-8*dt)));
            return yaw;
        }
    }
}
