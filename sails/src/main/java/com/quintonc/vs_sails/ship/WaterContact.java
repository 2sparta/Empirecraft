package com.quintonc.vs_sails.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import org.joml.Vector3d;
import org.joml.Matrix4dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.impl.game.ships.PhysShipImpl;
import java.util.*;

/** All world/fluid reads run on the server thread. Physics consumes only an immutable snapshot. */
final class WaterContact {
    private static final double MINIMUM_IMMERSION=Double.parseDouble(
            com.quintonc.vs_sails.config.ConfigUtils.config.getOrDefault("minimum-hull-immersion","0.35"));
    record Voxel(BlockPos pos,double volume) {}
    record Point(double x,double y,double z,double surface,double weight) {}
    record Frame(long shipId,List<Point> points) {}
    static double immersion(Frame frame,PhysShipImpl body) {
        if(frame==null || frame.shipId()!=body.getId())return 0;
        Matrix4dc transform=body.getTransform().getShipToWorld();
        double height=Math.abs(transform.m01())+Math.abs(transform.m11())+Math.abs(transform.m21());
        if(!Double.isFinite(height)||height<=0)return 0;
        double total=0,wet=0;Vector3d centre=new Vector3d();
        for(Point p:frame.points()) {
            total+=p.weight;
            if(!Double.isFinite(p.surface))continue;
            transform.transformPosition(centre.set(p.x,p.y,p.z));
            wet+=p.weight*Math.max(0,Math.min(1,(p.surface-centre.y+height*.5)/height));
        }
        return total>0?wet/total:0;
    }
    static Frame sample(ServerLevel level,LoadedServerShip ship,List<Voxel> blocks) {
        long[] rooms=ShipFlooding.displacementCells(level,ship.getId());
        int total=blocks.size()+rooms.length;
        List<Point> points=new ArrayList<>();int count=Math.min(128,total);
        Matrix4dc transform=ship.getTransform().getShipToWorld();
        double halfHeight=(Math.abs(transform.m01())+Math.abs(transform.m11())+Math.abs(transform.m21()))*.5;
        for(int i=0;i<count;i++) {
            int index=(int)((long)i*total/count);
            Voxel voxel=index<blocks.size()?blocks.get(index):new Voxel(BlockPos.of(rooms[index-blocks.size()]),1);BlockPos p=voxel.pos;
            Vector3d centre=transform.transformPosition(new Vector3d(p.getX()+.5,p.getY()+.5,p.getZ()+.5));
            double surface=surface(level,centre.x,centre.y-halfHeight,centre.y+halfHeight,centre.z);
            points.add(new Point(p.getX()+.5,p.getY()+.5,p.getZ()+.5,surface,(double)total/count*voxel.volume));
        }
        return new Frame(ship.getId(),List.copyOf(points));
    }
    private static double surface(ServerLevel level,double x,double bottom,double top,double z) {
        // A buoy resting on the bed can have its lower face a few micrometres inside
        // terrain. Its centre/top still touches water and must not be classified as dry.
        for(double y:new double[]{bottom,(bottom+top)*.5,top-1e-6}) {
            double found=columnSurface(level,BlockPos.containing(x,y,z));
            if(Double.isFinite(found))return found;
        }
        return columnSurface(level,BlockPos.containing(x,bottom,z).below(1));
    }
    private static double columnSurface(ServerLevel level,BlockPos start) {
        // Looking below the voxel discovers an approaching waterline without applying force in air.
        for(int down=0;down<=2;down++) {
            BlockPos p=start.below(down);var fluid=fluid(level,p);
            if(fluid==null || !fluid.is(FluidTags.WATER))continue;
            for(int up=0;up<32;up++) {
                BlockPos next=p.above();var above=fluid(level,next);
                if(above==null || !above.is(FluidTags.WATER))return p.getY()+fluid.getHeight(level,p);
                p=next;fluid=above;
            }
            return p.getY()+1; // Still fully immersed; exact distant surface is unnecessary.
        }
        return Double.NaN;
    }
    private static net.minecraft.world.level.material.FluidState fluid(ServerLevel level,BlockPos pos) {
        if(pos.getY()<level.getMinBuildHeight() || pos.getY()>=level.getMaxBuildHeight())return null;
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        return chunk==null?null:chunk.getFluidState(pos);
    }
    static void apply(Frame frame,PhysShipImpl body,int buoys,double copiedBuoys,int ballast,double buoyStrength,double ballastStrength,double dt) {
        apply(frame,body,buoys,copiedBuoys,ballast,buoyStrength,ballastStrength,dt,0);
    }
    static void apply(Frame frame,PhysShipImpl body,int buoys,double copiedBuoys,int ballast,double buoyStrength,double ballastStrength,double dt,double waterMass) {
        if(frame==null || frame.shipId()!=body.getId())return;
        Matrix4dc transform=body.getTransform().getShipToWorld();
        double height=Math.abs(transform.m01())+Math.abs(transform.m11())+Math.abs(transform.m21());
        var scale=body.getTransform().getShipToWorldScaling();double volumeScale=Math.abs(scale.x()*scale.y()*scale.z());
        if(!Double.isFinite(height)||height<=0||!Double.isFinite(volumeScale)||volumeScale<=0)return;
        double volume=0,area=0;Vector3d centre=new Vector3d(),buoyCentre=new Vector3d();
        for(Point p:frame.points()) {
            if(!Double.isFinite(p.surface))continue;
            transform.transformPosition(centre.set(p.x,p.y,p.z));
            double fraction=Math.max(0,Math.min(1,(p.surface-centre.y+height*.5)/height));
            double wet=fraction*p.weight*volumeScale;
            volume+=wet;area+=p.weight*volumeScale/height;buoyCentre.add(p.x*wet,p.y*wet,p.z*wet);
        }
        double supportedMass=body.getMass()+Math.max(0,Math.min(waterMass,body.getMass()*4));
        double multiplier=WaterSupport.legacyMultiplier(buoys,ballast,buoyStrength,ballastStrength,supportedMass,volumeScale)
                * (1+Math.max(0,copiedBuoys)*Math.max(0,buoyStrength)/(1+Math.max(0,buoys)*Math.max(0,buoyStrength)));
        double force=WaterSupport.force(supportedMass,volume,area,multiplier,body.getVelocity().y(),dt,MINIMUM_IMMERSION);
        if(!Double.isFinite(force)||volume<=0)return;
        if(force>0)body.applyWorldForceToModelPos(new Vector3d(0,force,0),buoyCentre.div(volume));
        else body.applyInvariantForce(new Vector3d(0,force,0));
        // Retain roll/pitch water damping after replacing native fluid drag.
        var omega=body.getAngularVelocity();var rotation=body.getTransform().getShipToWorldRotation();
        Vector3d angular=new Vector3d(-omega.x()/(dt+.2),0,-omega.z()/(dt+.2));
        Vector3d torque=rotation.transform(body.getMomentOfInertia().transform(rotation.transformInverse(angular)));
        if(torque.isFinite())body.applyInvariantTorque(torque);
    }
}

