package com.quintonc.vs_sails.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quintonc.vs_sails.ship.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.primitives.AABBd;
import net.minecraft.client.multiplayer.ClientLevel;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Clip world ocean meshes against moving ship solids and managed compartments. */
public final class OceanWaterClip {
    private record Pose(Matrix4dc transform,AABBd bounds) {}
    private static final Map<Long,Pose> POSES=new HashMap<>();
    private static ClientLevel world;
    private OceanWaterClip(){}

    /** World meshes must follow the hull, including its old position after it moves away. */
    public static void refresh(ClientLevel level){
        if(world!=level){POSES.clear();world=level;}
        var player=Minecraft.getInstance().player;if(level==null || player==null)return;
        Set<Long> seen=new HashSet<>();
        var near=new AABBd(player.getX()-256,player.getY()-256,player.getZ()-256,player.getX()+256,player.getY()+256,player.getZ()+256);
        for(var ship:VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getIntersecting(near,VSGameUtilsKt.getDimensionId(level))){
            seen.add(ship.getId());Pose old=POSES.get(ship.getId());
            var transform=ship.getShipToWorld();var bounds=ship.getWorldAABB();
            if(old!=null && old.transform.equals(transform,.01) && old.bounds.equals(bounds))continue;
            if(old!=null)FloodWaterRenderer.dirtyWorld(old.bounds);
            FloodWaterRenderer.dirtyWorld(bounds);
            POSES.put(ship.getId(),new Pose(new Matrix4d(transform),new AABBd(bounds)));
        }
        for(var it=POSES.entrySet().iterator();it.hasNext();){var entry=it.next();if(!seen.contains(entry.getKey())){FloodWaterRenderer.dirtyWorld(entry.getValue().bounds);it.remove();}}
    }
    public static void invalidate(long ship){var pose=POSES.remove(ship);if(pose!=null)FloodWaterRenderer.dirtyWorld(pose.bounds);}

    public static boolean intersects(BlockPos pos){return !volumes(pos).isEmpty();}
    public static VertexConsumer wrap(BlockPos pos,VertexConsumer output){
        var volumes=volumes(pos);return volumes.isEmpty()?output:new Clipped(output,volumes);
    }
    private static List<List<WaterOcclusion.Plane>> volumes(BlockPos pos){
        var level=Minecraft.getInstance().level;
        if(level==null || VSGameUtilsKt.getShipManagingPos(level,pos)!=null)return List.of();
        var bounds=new AABBd(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+1,pos.getZ()+1);
        List<List<WaterOcclusion.Plane>> result=new ArrayList<>();
        boolean checkedFaces=false;
        for(var ship:VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getIntersecting(bounds,VSGameUtilsKt.getDimensionId(level))){
            if(!checkedFaces){
                checkedFaces=true;boolean buried=true;
                for(Direction d:Direction.values())if(!level.getFluidState(pos.relative(d)).is(FluidTags.WATER)){buried=false;break;}
                // Six wet neighbours already hide every vanilla ocean face.
                // Do not allocate clipping planes for invisible subsurface water.
                if(buried)return List.of();
            }
            var occupied=ship.getShipAABB();if(occupied==null)continue;
            Matrix4dc transform=new Matrix4d(ship.getWorldToShip());
            var local=new AABBd(bounds).transform(transform);
            int minX=Math.max(occupied.minX(),(int)Math.floor(local.minX)),maxX=Math.min(occupied.maxX()-1,(int)Math.floor(local.maxX));
            int minY=Math.max(occupied.minY(),(int)Math.floor(local.minY)),maxY=Math.min(occupied.maxY()-1,(int)Math.floor(local.maxY));
            int minZ=Math.max(occupied.minZ(),(int)Math.floor(local.minZ)),maxZ=Math.min(occupied.maxZ()-1,(int)Math.floor(local.maxZ));
            var region=FloodZones.region(level,ship.getId());
            for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++){
                var cell=new BlockPos(x,y,z);if(!level.hasChunkAt(cell))continue;
                if(region!=null && region.contains(cell))result.add(planes(transform,pos,new AABB(x,y,z,x+1,y+1,z+1)));
                else for(var box:level.getBlockState(cell).getCollisionShape(level,cell).toAabbs())
                    result.add(planes(transform,pos,box.move(x,y,z)));
            }
        }
        return result;
    }
    private static List<WaterOcclusion.Plane> planes(Matrix4dc t,BlockPos pos,AABB b){
        // LiquidBlockRenderer emits section-relative coordinates. Transform the
        // clipping planes once, rather than transforming/interpolating UVs later.
        double x=pos.getX()&~15,y=pos.getY()&~15,z=pos.getZ()&~15;
        double ox=t.m00()*x+t.m10()*y+t.m20()*z+t.m30();
        double oy=t.m01()*x+t.m11()*y+t.m21()*z+t.m31();
        double oz=t.m02()*x+t.m12()*y+t.m22()*z+t.m32();
        return List.of(new WaterOcclusion.Plane(t.m00(),t.m10(),t.m20(),b.maxX-ox),
                new WaterOcclusion.Plane(-t.m00(),-t.m10(),-t.m20(),ox-b.minX),
                new WaterOcclusion.Plane(t.m01(),t.m11(),t.m21(),b.maxY-oy),
                new WaterOcclusion.Plane(-t.m01(),-t.m11(),-t.m21(),oy-b.minY),
                new WaterOcclusion.Plane(t.m02(),t.m12(),t.m22(),b.maxZ-oz),
                new WaterOcclusion.Plane(-t.m02(),-t.m12(),-t.m22(),oz-b.minZ));
    }
    private static final class Clipped implements VertexConsumer {
        final VertexConsumer output;final List<List<WaterOcclusion.Plane>> volumes;
        final List<WaterOcclusion.Vertex> quad=new ArrayList<>(4);
        double x,y,z;double[] data={255,255,255,255,0,0,0,0,0,0,0,1,0};
        Clipped(VertexConsumer output,List<List<WaterOcclusion.Plane>> volumes){this.output=output;this.volumes=volumes;}
        @Override public VertexConsumer vertex(double x,double y,double z){this.x=x;this.y=y;this.z=z;return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){data[0]=r;data[1]=g;data[2]=b;data[3]=a;return this;}
        @Override public VertexConsumer uv(float u,float v){data[4]=u;data[5]=v;return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){data[6]=u;data[7]=v;return this;}
        @Override public VertexConsumer uv2(int u,int v){data[8]=u;data[9]=v;return this;}
        @Override public VertexConsumer normal(float x,float y,float z){data[10]=x;data[11]=y;data[12]=z;return this;}
        @Override public void defaultColor(int r,int g,int b,int a){color(r,g,b,a);}
        @Override public void unsetDefaultColor(){}
        @Override public void endVertex(){
            quad.add(new WaterOcclusion.Vertex(x,y,z,data.clone()));if(quad.size()<4)return;
            // Split non-planar/sloping vanilla water quads before clipping.
            for(var triangle:List.of(List.of(quad.get(0),quad.get(1),quad.get(2)),List.of(quad.get(0),quad.get(2),quad.get(3)))){
                List<List<WaterOcclusion.Vertex>> polygons=List.of(triangle);
                for(var volume:volumes){
                    List<List<WaterOcclusion.Vertex>> next=new ArrayList<>();
                    for(var polygon:polygons)next.addAll(WaterOcclusion.subtract(polygon,volume));
                    polygons=next;if(polygons.isEmpty())break;
                }
                for(var polygon:polygons)for(int i=1;i+1<polygon.size();i++){
                    emit(polygon.get(0));emit(polygon.get(i));emit(polygon.get(i+1));emit(polygon.get(i+1));
                }
            }
            quad.clear();
        }
        void emit(WaterOcclusion.Vertex v){
            double[] a=v.data();
            output.vertex(v.x(),v.y(),v.z()).color((int)Math.round(a[0]),(int)Math.round(a[1]),(int)Math.round(a[2]),(int)Math.round(a[3]))
                    .uv((float)a[4],(float)a[5]).overlayCoords((int)Math.round(a[6]),(int)Math.round(a[7]))
                    .uv2((int)Math.round(a[8]),(int)Math.round(a[9])).normal((float)a[10],(float)a[11],(float)a[12]).endVertex();
        }
    }
}
