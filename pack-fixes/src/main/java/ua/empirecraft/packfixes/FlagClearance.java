package ua.empirecraft.packfixes;

import com.quintonc.vs_sails.wind.*;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ServerShip;
import ua.empirecraft.engagement.*;

/** Preserve the hidden-ensign rule when the cloth swings out of its placed direction. */
public final class FlagClearance {
    private FlagClearance() {}
    public static boolean visible(ServerLevel level,ServerShip ship,NavalFlags.FlagEntity flag) {
        var pos=flag.getBlockPos();var world=ship.getShipToWorld().transformPosition(new Vector3d(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5));
        var center=new Vec3(world.x,world.y,world.z);double strength=WindManager.getWindStrength(level,BlockPos.containing(center));
        var right=flag.getBlockState().getValue(NavalFlags.FlagBlock.FACING).getClockWise();
        var direction=new Vector3d(right.getStepX(),0,right.getStepZ());
        if(Math.abs(strength)>1e-5){
            double angle=Math.toRadians(ServerWindManager.getWindDirectionAtPosition(level,center)+(strength<0?180:0));
            direction.set(Math.cos(angle),0,Math.sin(angle));ship.getWorldToShip().transformDirection(direction);
            double length=Math.hypot(direction.x,direction.z);if(length<1e-6)return false;direction.set(direction.x/length,0,direction.z/length);
        }
        var checked=new HashSet<BlockPos>();
        for(int h=0;h<=flag.width;h++)for(double s=.15;s<=flag.width+.15;s+=.5){
            var point=BlockPos.containing(pos.getX()+.5+direction.x*s,pos.getY()+.8-h*.5,pos.getZ()+.5+direction.z*s);
            if(point.getX()==pos.getX()&&point.getZ()==pos.getZ()||!checked.add(point))continue;
            if(!level.hasChunkAt(point)||!level.getBlockState(point).isAir())return false;
        }
        for(int h=0;h<=flag.width/2;h++){
            var start=worldPoint(ship,new Vec3(pos.getX()+.5,pos.getY()+.8-h,pos.getZ()+.5));
            var end=worldPoint(ship,new Vec3(pos.getX()+.5+direction.x*(flag.width+.15),pos.getY()+.8-h,pos.getZ()+.5+direction.z*(flag.width+.15)));
            if(CombatRaycast.firstIgnoring(level,null,start,end,true,s->false,ship.getId())!=null)return false;
        }
        return true;
    }
    private static Vec3 worldPoint(ServerShip ship,Vec3 point) {
        var world=ship.getShipToWorld().transformPosition(new Vector3d(point.x,point.y,point.z));
        return new Vec3(world.x,world.y,world.z);
    }
}
