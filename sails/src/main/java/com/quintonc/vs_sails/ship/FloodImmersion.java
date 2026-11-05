package com.quintonc.vs_sails.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;

/** Point and body queries use the same clipped water volume as the visual surface. */
public final class FloodImmersion {
    private FloodImmersion(){}
    private record Slice(double x,double y,double z,double[] depths) {}
    private static final ThreadLocal<Slice> LAST=new ThreadLocal<>();
    private static double[] depths(double x,double y,double z){
        Slice s=LAST.get();if(s==null || s.x!=x || s.y!=y || s.z!=z){s=new Slice(x,y,z,FloodGeometry.depths(x,y,z));LAST.set(s);}return s.depths;
    }
    public static boolean wet(Level level,Ship ship,double worldX,double worldY,double worldZ){
        Vector3d p=ship.getWorldToShip().transformPosition(new Vector3d(worldX,worldY,worldZ));
        BlockPos cell=BlockPos.containing(p.x,p.y,p.z);var fluid=level.getFluidState(cell);
        if(!fluid.is(FluidTags.WATER))return false;
        int q=fluid.getAmount();if(level instanceof net.minecraft.server.level.ServerLevel server)q=ShipFlooding.knownVolume(server,ship.getId(),cell,q);
        if(q<=0)return false;if(q>=8)return true;
        var t=ship.getShipToWorld();double x=t.m01(),y=t.m11(),z=t.m21();
        double plane=FloodGeometry.plane(depths(x,y,z)[q],x,y,z);
        return (p.x-cell.getX())*x+(p.y-cell.getY())*y+(p.z-cell.getZ())*z<plane;
    }

    /** Vertical rays through the body's centre and corners, intersected with actual rotated voxels. */
    public static double height(Level level,Ship ship,AABB body){
        var inverse=ship.getWorldToShip();var transform=ship.getShipToWorld();
        var local=new org.joml.primitives.AABBd(body.minX,body.minY,body.minZ,body.maxX,body.maxY,body.maxZ).transform(inverse);
        double ux=transform.m01(),uy=transform.m11(),uz=transform.m21();double[] depths=depths(ux,uy,uz);
        Vector3d direction=inverse.transformDirection(new Vector3d(0,1,0));double[] delta={direction.x,direction.y,direction.z};
        double[] xs={body.minX+1e-5,(body.minX+body.maxX)*.5,body.maxX-1e-5},zs={body.minZ+1e-5,(body.minZ+body.maxZ)*.5,body.maxZ-1e-5};
        double highest=0;int visits=0;
        for(BlockPos cell:BlockPos.betweenClosed(BlockPos.containing(local.minX,local.minY,local.minZ),BlockPos.containing(local.maxX,local.maxY,local.maxZ))){
            if(++visits>2048)break;if(!level.hasChunkAt(cell))continue;var fluid=level.getFluidState(cell);
            if(!fluid.is(FluidTags.WATER))continue;int q=fluid.getAmount();
            if(level instanceof net.minecraft.server.level.ServerLevel server)q=ShipFlooding.knownVolume(server,ship.getId(),cell,q);
            if(q<=0)continue;double plane=FloodGeometry.plane(depths[q],ux,uy,uz);
            for(double x:xs)for(double z:zs){
                Vector3d start=inverse.transformPosition(new Vector3d(x,body.minY,z));
                double[] offset={start.x-cell.getX(),start.y-cell.getY(),start.z-cell.getZ()};
                double low=0,high=body.getYsize();
                for(int axis=0;axis<3;axis++){
                    if(Math.abs(delta[axis])<1e-9){if(offset[axis]<0 || offset[axis]>1){high=-1;break;}}
                    else{double a=-offset[axis]/delta[axis],b=(1-offset[axis])/delta[axis];low=Math.max(low,Math.min(a,b));high=Math.min(high,Math.max(a,b));}
                }
                high=Math.min(high,plane-offset[0]*ux-offset[1]*uy-offset[2]*uz);
                if(high>low+1e-6)highest=Math.max(highest,high);
            }
        }
        return highest;
    }
}
