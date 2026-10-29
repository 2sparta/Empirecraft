package com.quintonc.vs_sails.wind;

/** Continuous shortest-arc weather response; all rates are per game tick. */
public final class SmoothWind {
    private SmoothWind() {}
    public static double angle(double current,double target) {
        if(!Double.isFinite(current)||!Double.isFinite(target))return 0;
        double delta=((target-current)%360+540)%360-180;
        return ((current+Math.max(-.05,Math.min(.05,delta/600)))%360+360)%360;
    }
    public static double strength(double current,double target) {
        if(!Double.isFinite(current)||!Double.isFinite(target))return 0;
        return current+Math.max(-.001,Math.min(.001,(target-current)/200));
    }
    public static double dailyDrift(long time) {
        return 24*Math.sin(2*Math.PI*Math.floorMod(time,24000L)/24000.0);
    }
}
