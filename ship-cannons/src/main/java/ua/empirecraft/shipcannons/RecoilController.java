package ua.empirecraft.shipcannons;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.ShipPhysicsListener;
import org.valkyrienskies.core.api.world.PhysLevel;
import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;

/** Stable attachment key from 1.1.0; only the owner is saved by VS. */
public final class RecoilController implements ShipPhysicsListener {
    public long owner;
    private record Shot(Vector3d point,Vector3d impulse,RecoilMath.Settings settings,long time) {}
    private static final class Pulse {
        final Vector3d impulse,angular;final RecoilMath.Settings settings;final long time;double remaining;
        Pulse(Vector3d impulse,Vector3d angular,RecoilMath.Settings settings,long time) {this.impulse=impulse;this.angular=angular;this.settings=settings;this.time=time;remaining=settings.pulseSeconds();}
    }
    @JsonIgnore private final ArrayBlockingQueue<Shot> shots=new ArrayBlockingQueue<>(256);
    @JsonIgnore private final ArrayDeque<Pulse> active=new ArrayDeque<>();
    @JsonIgnore public volatile long applied,dropped;
    public RecoilController() {}
    public RecoilController(long owner) {this.owner=owner;}
    public static double momentum(double mass,double speed,double multiplier,double cap) {return RecoilMath.momentum(mass,speed,multiplier,cap);}
    public boolean enqueue(Vector3d point,Vector3d impulse) {return enqueue(point,impulse,RecoilMath.DEFAULT);}
    public boolean enqueue(Vector3dc point,Vector3dc impulse,RecoilMath.Settings settings) {
        if(settings==null || !point.isFinite() || !impulse.isFinite() || impulse.lengthSquared()<1e-16)return false;
        boolean accepted=shots.offer(new Shot(new Vector3d(point),new Vector3d(impulse),settings,System.nanoTime()));
        if(!accepted)dropped++;return accepted;
    }
    @Override public void physTick(PhysShip body,PhysLevel world) {physTick(body,world,1.0/60);}
    @Override public void physTick(PhysShip body,PhysLevel world,double dt) {
        if(body.getId()!=owner || !Double.isFinite(dt) || dt<=0)return;
        if(body.isStatic() || !Double.isFinite(body.getMass()) || body.getMass()<=0) {shots.clear();active.clear();return;}
        var rotation=body.getTransform().getShipToWorldRotation();
        for(int i=0;i<256;i++) {
            Shot shot=shots.poll();if(shot==null)break;
            if(System.nanoTime()-shot.time>2_000_000_000L || active.size()>=256) {dropped++;continue;}
            Vector3d j=new Vector3d(shot.impulse);
            double limit=body.getMass()*shot.settings.perShotLinearKick();if(j.length()>limit)j.mul(limit/j.length());
            Vector3d worldJ=rotation.transform(new Vector3d(j));
            Vector3d arm=body.getShipToWorld().transformPosition(new Vector3d(shot.point)).sub(body.getTransform().getPositionInWorld());
            Vector3d up=rotation.transform(new Vector3d(0,1,0));
            Vector3d localAngular=rotation.transformInverse(RecoilMath.angularImpulse(arm,worldJ,up,shot.settings));
            Vector3d kick=RecoilMath.angularVelocity(body.getMomentOfInertia(),localAngular);
            if(kick.length()>shot.settings.perShotAngularKick())localAngular.mul(shot.settings.perShotAngularKick()/kick.length());
            if(j.isFinite() && localAngular.isFinite())active.addLast(new Pulse(j,localAngular,shot.settings,shot.time));
        }
        Vector3d totalImpulse=new Vector3d(),totalAngular=new Vector3d();double angularLimit=Double.POSITIVE_INFINITY;
        var iterator=active.iterator();
        while(iterator.hasNext()) {
            Pulse pulse=iterator.next();
            if(System.nanoTime()-pulse.time>2_000_000_000L) {iterator.remove();dropped++;continue;}
            double slice=Math.min(dt,pulse.remaining),fraction=slice/pulse.settings.pulseSeconds();
            Vector3d j=rotation.transform(new Vector3d(pulse.impulse).mul(fraction));
            Vector3d localAngular=new Vector3d(pulse.angular).mul(fraction);
            totalImpulse.add(j);totalAngular.add(localAngular);angularLimit=Math.min(angularLimit,pulse.settings.angularSpeedLimit());
            pulse.remaining-=slice;
            if(pulse.remaining<=1e-9) {iterator.remove();applied++;}
        }
        if(totalImpulse.lengthSquared()>0)body.applyInvariantForce(totalImpulse.div(dt));
        if(totalAngular.lengthSquared()>0) {
            Vector3d kick=rotation.transform(RecoilMath.angularVelocity(body.getMomentOfInertia(),totalAngular));
            double scale=RecoilMath.rateScale(body.getAngularVelocity(),kick,angularLimit);
            body.applyInvariantTorque(rotation.transform(totalAngular).mul(scale/dt));
        }
    }
}
