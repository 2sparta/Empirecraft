package com.quintonc.vs_sails.ship;

/** Physics-thread-only cloth filling and bounded changes in acceleration. */
public final class SailResponse {
    private double target, drive;
    public double target(double requested,double dt) {
        if(!Double.isFinite(requested)||requested<0||!valid(dt))return target;
        double delta=(requested-target)*(-Math.expm1(-dt/(requested>target?8:4)));
        target+=Math.max(-dt,Math.min(.6*dt,delta));return target;
    }
    public double drive(double requested,double dt) {
        if(!Double.isFinite(requested)||!valid(dt))return drive;
        drive+=Math.max(-.8*dt,Math.min(.4*dt,requested-drive));return drive;
    }
    public void reset(){target=0;drive=0;}
    private static boolean valid(double dt){return Double.isFinite(dt)&&dt>0&&dt<=.25;}
}
