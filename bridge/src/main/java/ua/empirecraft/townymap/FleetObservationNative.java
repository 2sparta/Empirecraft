package ua.empirecraft.townymap;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Server-only verification of a remembered position; it never returns a ship's unseen coordinates. */
public final class FleetObservationNative {
    private FleetObservationNative(){}
    private static String world(ServerLevel level)throws ReflectiveOperationException {
        Object bukkit=level.getClass().getMethod("getWorld").invoke(level);return bukkit.getClass().getMethod("getUID").invoke(bukkit).toString();
    }
    private static double[] fov(UUID person){
        try{
            var field=Class.forName("com.quintonc.vs_sails.protection.FleetNative").getDeclaredField("VIEWS");field.setAccessible(true);
            Object value=((Map<?,?>)field.get(null)).get(person);
            if(value instanceof double[] angles && angles.length==2)return angles;
        }catch(ReflectiveOperationException|LinkageError unavailable){/* A conservative cone until the client reports its FOV. */}
        return new double[]{60,45};
    }
    public static boolean empty(Map<String,Object> data)throws ReflectiveOperationException {
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null || !server.isSameThread())return false;
        var player=server.getPlayerList().getPlayer(UUID.fromString(data.get("player").toString()));
        if(player==null || !world(player.serverLevel()).equals(data.get("world").toString()))return false;
        double x=((Number)data.get("x")).doubleValue(),y=((Number)data.get("y")).doubleValue(),z=((Number)data.get("z")).doubleValue();
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return false;
        Vec3 point=new Vec3(x,y,z),eye=player.getEyePosition(),delta=point.subtract(eye);double distance=delta.length();
        if(distance>Math.min(48,server.getPlayerList().getViewDistance()*16.0))return false;
        var level=player.serverLevel();
        for(int step=0;step<=Math.ceil(distance);step+=4)if(!level.hasChunkAt(BlockPos.containing(eye.add(delta.scale(distance<.05?0:Math.min(step/distance,1))))))return false;
        if(!level.hasChunkAt(BlockPos.containing(point)))return false;
        // Presence is checked even when the hull itself is currently occluded or unloading.
        long id=((Number)data.get("ship")).longValue();
        var current=VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(id);
        if(current!=null && current.getChunkClaimDimension().equals(VSGameUtilsKt.getDimensionId(level))){
            var b=current.getWorldAABB();
            if(b!=null && x>=b.minX()-2 && x<=b.maxX()+2 && y>=b.minY()-2 && y<=b.maxY()+2 && z>=b.minZ()-2 && z<=b.maxZ()+2)return false;
        }
        if(distance<1)return true;
        Vec3 look=player.getViewVector(1),right=new Vec3(-look.z,0,look.x).normalize(),up=right.cross(look).normalize();
        double[] view=fov(player.getUUID());double forward=delta.dot(look);
        boolean arrived=delta.x*delta.x+delta.z*delta.z<=8*8 && Math.abs(delta.y)<=16;
        if(!arrived && (forward<=0 || Math.abs(Math.toDegrees(Math.atan2(delta.dot(right),forward)))>view[0]*.5 || Math.abs(Math.toDegrees(Math.atan2(delta.dot(up),forward)))>view[1]*.5))return false;
        var hit=org.valkyrienskies.mod.common.world.RaycastUtilsKt.clipIncludeShips(level,new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.MISS || hit.getLocation().distanceToSqr(point)<1;
    }
}
