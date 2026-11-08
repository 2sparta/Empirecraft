package com.quintonc.vs_sails.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quintonc.vs_sails.ship.WaterOcclusion;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.*;

/** Exercise the production vertex consumer without starting graphics or Minecraft. */
public final class OceanWaterClipTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Recording implements VertexConsumer {
        final List<double[]> vertices=new ArrayList<>();double[] current=new double[9];
        public VertexConsumer vertex(double x,double y,double z){current[0]=x;current[1]=y;current[2]=z;return this;}
        public VertexConsumer color(int r,int g,int b,int a){current[3]=r;current[4]=a;return this;}
        public VertexConsumer uv(float u,float v){current[5]=u;current[6]=v;return this;}
        public VertexConsumer overlayCoords(int u,int v){return this;}
        public VertexConsumer uv2(int u,int v){current[7]=u;current[8]=v;return this;}
        public VertexConsumer normal(float x,float y,float z){return this;}
        public void endVertex(){vertices.add(current.clone());}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
    }
    @SuppressWarnings("unchecked")
    static List<WaterOcclusion.Plane> planes(Matrix4dc t,BlockPos pos,AABB box)throws Exception{
        var method=OceanWaterClip.class.getDeclaredMethod("planes",Matrix4dc.class,BlockPos.class,AABB.class);method.setAccessible(true);
        return (List<WaterOcclusion.Plane>)method.invoke(null,t,pos,box);
    }
    static Recording render(List<WaterOcclusion.Plane> volume)throws Exception{
        var type=Class.forName("com.quintonc.vs_sails.client.OceanWaterClip$Clipped");
        var ctor=type.getDeclaredConstructor(VertexConsumer.class,List.class);ctor.setAccessible(true);
        Recording result=new Recording();VertexConsumer consumer=(VertexConsumer)ctor.newInstance(result,List.of(volume));
        for(double[] v:new double[][]{{0,0},{1,0},{1,1},{0,1}})
            consumer.vertex(v[0],.5,v[1]).color((int)(v[0]*200),100,150,128).uv((float)v[0],(float)v[1]).uv2(240,128).normal(0,1,0).endVertex();
        return result;
    }
    static double area(Recording recorded){
        double sum=0;check(recorded.vertices.size()%4==0,"clipped faces remain valid Minecraft quads");
        for(int i=0;i<recorded.vertices.size();i+=4){
            var a=recorded.vertices.get(i);var b=recorded.vertices.get(i+1);var c=recorded.vertices.get(i+2);
            sum+=java.lang.Math.abs((b[0]-a[0])*(c[2]-a[2])-(b[2]-a[2])*(c[0]-a[0]))*.5;
        }return sum;
    }
    public static void main(String[] args)throws Exception{
        var pos=new BlockPos(32,64,48);var t=new Matrix4d().translation(-32,-64,-48);
        var clipped=render(planes(t,pos,new AABB(.25,0,.25,.75,1,.75)));
        check(java.lang.Math.abs(area(clipped)-.75)<1e-8,"actual vertex wrapper removes only the compartment's ocean area");
        for(var v:clipped.vertices){
            check(java.lang.Math.abs(v[5]-v[0])<1e-6 && java.lang.Math.abs(v[6]-v[2])<1e-6,"actual consumer keeps interpolated texture coordinates");
            check(java.lang.Math.abs(v[3]-v[0]*200)<=.5 && v[4]==128 && v[7]==240 && v[8]==128,"actual consumer keeps tint, alpha and packed lighting");
        }
        check(render(planes(t,pos,new AABB(-1,0,-1,2,1,2))).vertices.isEmpty(),"whole ocean face inside the hull emits no vertices");
        var yaw=new Matrix4d().rotationY(java.lang.Math.PI/4).translate(-32,-64,-48);
        var diagonal=render(planes(yaw,pos,new AABB(-2,0,-2,java.lang.Math.sqrt(.5),1,2)));
        check(java.lang.Math.abs(area(diagonal)-.5)<1e-7,"section-relative planes correctly clip a yawed ship");
        System.out.println("OCEAN_VERTEX_CLIP_PASS assertions="+checks);
    }
}
