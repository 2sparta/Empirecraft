package ua.empirecraft.engagement;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.joml.*;
import org.valkyrienskies.core.api.events.CollisionEvent;
import org.valkyrienskies.core.api.ships.*;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Physics-thread motion is captured without touching Towny; all permissions and tags run on the server. */
public final class RamCombat {
    record Motion(Vector3d center,Vector3d velocity,Vector3d omega){
        Vector3d at(Vector3dc point){return new Vector3d(omega).cross(new Vector3d(point).sub(center)).add(velocity);}
    }
    record Motions(long a,long b,Motion currentA,Motion currentB,Motion beforeA,Motion beforeB){}
    record Frame(MinecraftServer server,Object value,Motions motions){}
    record Work(ServerLevel level,ShotOrigins.Origin origin,Long victim,long until){}
    private static final ThreadLocal<Motions> CAPTURE=new ThreadLocal<>();
    private static final Map<Object,Motions> FRAMES=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,Work> WORK=new IdentityHashMap<>();
    private static final ThreadLocal<Frame> FRAME=new ThreadLocal<>();
    private static final ThreadLocal<Work> CURRENT=new ThreadLocal<>();
    private RamCombat(){}
    public static final class Capture implements AutoCloseable {
        private final Motions before=CAPTURE.get();
        private Capture(CollisionEvent event){
            var world=event.getPhysLevel();var a=world==null?null:world.getShipById(event.getShipIdA());var b=world==null?null:world.getShipById(event.getShipIdB());
            CAPTURE.set(new Motions(event.getShipIdA(),event.getShipIdB(),motion(a),motion(b),previous(event.getDimensionId(),event.getShipIdA(),a),previous(event.getDimensionId(),event.getShipIdB(),b)));
        }
        @Override public void close(){if(before==null)CAPTURE.remove();else CAPTURE.set(before);}
    }
    public static Capture capture(CollisionEvent event){return new Capture(event);}
    public static void frameCreated(Object frame){var motion=CAPTURE.get();if(motion!=null)FRAMES.put(frame,motion);}
    public static final class FrameScope implements AutoCloseable {
        private final Frame before=FRAME.get();
        FrameScope(MinecraftServer server,Object value){FRAME.set(new Frame(server,value,FRAMES.get(value)));}
        @Override public void close(){if(before==null)FRAME.remove();else FRAME.set(before);}
    }
    public static FrameScope frame(MinecraftServer server,Object value){return new FrameScope(server,value);}
    private static Motion motion(PhysShip body){return body==null?new Motion(new Vector3d(),new Vector3d(),new Vector3d()):new Motion(new Vector3d(body.getTransform().getPositionInWorld()),body.isStatic()?new Vector3d():new Vector3d(body.getVelocity()),body.isStatic()?new Vector3d():new Vector3d(body.getAngularVelocity()));}
    private static Motion previous(String dimension,long id,PhysShip body){
        try {
            Object old=Class.forName("ua.empirecraft.shipphysics.ShipDynamics").getMethod("recent",String.class,long.class).invoke(null,dimension,id);if(old==null)return motion(body);
            return new Motion(new Vector3d(number(old,"x"),number(old,"y"),number(old,"z")),new Vector3d(number(old,"vx"),number(old,"vy"),number(old,"vz")),new Vector3d(number(old,"wx"),number(old,"wy"),number(old,"wz")));
        }catch(ReflectiveOperationException error){return motion(body);}
    }
    private static Object call(Object value,String member)throws ReflectiveOperationException {var method=value.getClass().getDeclaredMethod(member);method.setAccessible(true);return method.invoke(value);}
    private static double number(Object value,String member)throws ReflectiveOperationException{return ((Number)call(value,member)).doubleValue();}
    public static void planned(Object work,ServerLevel level,LoadedServerShip target,Object targetCell){
        var frame=FRAME.get();if(frame==null||target==null)return;
        try {
            Object pair=call(frame.value,"pair");long a=((Number)call(pair,"a")).longValue(),b=((Number)call(pair,"b")).longValue();
            var world=VSGameUtilsKt.getShipObjectWorld(level);var left=world.getLoadedShips().getById(a);var right=world.getLoadedShips().getById(b);
            var other=target.getId()==a?right:left;var source=target;
            if(other!=null){
                BlockPos cell=(BlockPos)call(targetCell,"pos");var p=CombatRaycast.worldPoint(target,net.minecraft.world.phys.Vec3.atCenterOf(cell));Vector3d point=new Vector3d(p.x,p.y,p.z);
                Object contact=null;double distance=Double.POSITIVE_INFINITY;
                for(Object candidate:(List<?>)call(frame.value,"points")){double dx=number(candidate,"x")-p.x,dy=number(candidate,"y")-p.y,dz=number(candidate,"z")-p.z,d=dx*dx+dy*dy+dz*dz;if(d<distance){distance=d;contact=candidate;}}
                Vector3d normal=contact==null?new Vector3d(right.getTransform().getPositionInWorld()).sub(left.getTransform().getPositionInWorld()).normalize():new Vector3d(number(contact,"nx"),number(contact,"ny"),number(contact,"nz")).normalize();
                double approach;
                if(frame.motions!=null)approach=approach(frame.motions,a,other.getId(),point,normal);
                else {
                    // HullCollision can recover a contact outside capture(). Its frame has
                    // no thread-local snapshot; use genuine recent physics motion too.
                    String dimension=Objects.toString(call(pair,"dimension"));var ca=serverMotion(left);var cb=serverMotion(right);
                    var recovered=new Motions(a,b,ca,cb,previous(dimension,a,ca),previous(dimension,b,cb));
                    double closing=java.lang.Math.max(new Vector3d(ca.at(point)).sub(cb.at(point)).dot(normal),new Vector3d(recovered.beforeA.at(point)).sub(recovered.beforeB.at(point)).dot(normal));
                    if(closing<=1e-6&&contact!=null&&number(contact,"speed")>.1){WORK.put(work,new Work(level,null,target.getId(),EngagementRuntime.now(level)+2400));return;}
                    approach=approach(recovered,a,other.getId(),point,normal);
                }
                // A stationary protected vessel cannot be forced into PvP by an attacker breaking its own bow.
                if(Double.isFinite(approach)&&approach>.1)source=other;
            }
            var origin=ShotOrigins.of(Combatants.ship(level,source,null,null,null),level);if(WORK.size()>8192)WORK.entrySet().removeIf(e->e.getValue().until<EngagementRuntime.now(level));
            WORK.put(work,new Work(level,origin,target.getId(),EngagementRuntime.now(level)+2400));
        }catch(ReflectiveOperationException|RuntimeException error){EngagementRuntime.warn(error);WORK.put(work,new Work(level,null,target.getId(),EngagementRuntime.now(level)+2400));}
    }
    private static Motion serverMotion(LoadedServerShip body){return body==null?new Motion(new Vector3d(),new Vector3d(),new Vector3d()):new Motion(new Vector3d(body.getTransform().getPositionInWorld()),new Vector3d(body.getVelocity()),new Vector3d(body.getOmega()));}
    private static Motion previous(String dimension,long id,Motion fallback){
        try{Object old=Class.forName("ua.empirecraft.shipphysics.ShipDynamics").getMethod("recent",String.class,long.class).invoke(null,dimension,id);return old==null?fallback:new Motion(new Vector3d(number(old,"x"),number(old,"y"),number(old,"z")),new Vector3d(number(old,"vx"),number(old,"vy"),number(old,"vz")),new Vector3d(number(old,"wx"),number(old,"wy"),number(old,"wz")));}
        catch(ReflectiveOperationException error){return fallback;}
    }
    public static Work selected(){return CURRENT.get();}
    static double approach(Motions m,long sortedA,long other,Vector3dc point,Vector3dc normal){
        boolean aligned=m.a==sortedA;Motion ca=aligned?m.currentA:m.currentB,cb=aligned?m.currentB:m.currentA,pa=aligned?m.beforeA:m.beforeB,pb=aligned?m.beforeB:m.beforeA;
        double before=new Vector3d(pa.at(point)).sub(pb.at(point)).dot(normal),current=new Vector3d(ca.at(point)).sub(cb.at(point)).dot(normal);
        if(!Double.isFinite(before+current)||java.lang.Math.max(before,current)<=1e-6)return 0;
        boolean old=before>=current;Vector3d velocity=(other==sortedA?(old?pa:ca):(old?pb:cb)).at(point);
        return velocity.dot(other==sortedA?normal:new Vector3d(normal).negate());
    }
    public static void select(Object work){var info=WORK.get(work);if(info==null)CURRENT.remove();else CURRENT.set(info);}
    public static void clearSelected(){CURRENT.remove();}
    public static void finished(Object work){WORK.remove(work);}
    public static Boolean permitted(ServerLevel level,LoadedServerShip target){
        var work=CURRENT.get();if(work==null||target==null||!Objects.equals(work.victim,target.getId()))return null;
        if(work.origin==null)return false;
        return EngagementRuntime.decide(work.origin.party(),Combatants.ship(level,target,null,null,null),level,false).allow();
    }
    public static Boolean permitted(ServerLevel level,BlockPos pos){return permitted(level,com.quintonc.vs_sails.protection.ShipProtection.ship(level,pos));}
    public static void broken(ServerLevel level,BlockPos pos){
        var work=CURRENT.get();if(work==null||work.origin==null||work.victim==null||Objects.equals(work.origin.ship(),work.victim))return;
        var target=VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().getById(work.victim);if(target!=null)EngagementRuntime.decide(work.origin.party(),Combatants.ship(level,target,null,null,null),level,true);
    }
    public static void clear(){CAPTURE.remove();FRAME.remove();CURRENT.remove();FRAMES.clear();WORK.clear();}
}
