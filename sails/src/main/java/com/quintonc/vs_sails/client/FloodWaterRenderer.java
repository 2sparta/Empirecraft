package com.quintonc.vs_sails.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quintonc.vs_sails.ship.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/** Ordinary chunk water meshes, clipped against world gravity instead of ship-local Y. */
@Mod.EventBusSubscriber(modid="vs_sails",value=Dist.CLIENT)
public final class FloodWaterRenderer {
    private record Frame(double x,double y,double z,double[] depths,List<List<FloodGeometry.Face>> meshes) {
        static Frame of(double x,double y,double z){
            double[] depths=FloodGeometry.depths(x,y,z);List<List<FloodGeometry.Face>> meshes=new ArrayList<>();
            for(int q=0;q<=8;q++)meshes.add(FloodGeometry.mesh(x,y,z,depths[q]));return new Frame(x,y,z,depths,List.copyOf(meshes));
        }
    }
    private static final Map<Long,Frame> FRAMES=new ConcurrentHashMap<>();
    private static final Map<Long,Set<Long>> SECTIONS=new ConcurrentHashMap<>();
    private static final Map<Long,FloodZones.Region> MASKS=new ConcurrentHashMap<>();
    private static final Set<Long> DIRTY=ConcurrentHashMap.newKeySet();
    private static Object world;
    private static int ticks;
    private FloodWaterRenderer(){}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(world!=mc.level){FRAMES.clear();SECTIONS.clear();MASKS.clear();DIRTY.clear();world=mc.level;}
        if(mc.level==null || ++ticks%4!=0)return;
        for(var entry:FRAMES.entrySet()){
            var ship=VSGameUtilsKt.getShipObjectWorld(mc.level).getAllShips().getById(entry.getKey());
            var position=ship==null?null:ship.getTransform().getPositionInWorld();
            if(ship==null || mc.player==null || mc.player.distanceToSqr(position.x(),position.y(),position.z())>256*256){FRAMES.remove(entry.getKey());SECTIONS.remove(entry.getKey());MASKS.remove(entry.getKey());continue;}
            var t=ship.getShipToWorld();var old=entry.getValue();
            if(Math.abs(t.m01()-old.x)+Math.abs(t.m11()-old.y)+Math.abs(t.m21()-old.z)>.005){
                FRAMES.put(ship.getId(),Frame.of(t.m01(),t.m11(),t.m21()));DIRTY.addAll(SECTIONS.getOrDefault(ship.getId(),Set.of()));
            }
        }
        int budget=128;
        for(var it=DIRTY.iterator();it.hasNext() && budget-->0;){long packed=it.next();it.remove();
            mc.levelRenderer.setSectionDirty(SectionPos.x(packed),SectionPos.y(packed),SectionPos.z(packed));
        }
    }

    public static void mask(long ship,FloodZones.Region region){
        var level=Minecraft.getInstance().level;
        if(world!=level){FRAMES.clear();SECTIONS.clear();MASKS.clear();DIRTY.clear();world=level;}
        if(region==null){DIRTY.addAll(SECTIONS.getOrDefault(ship,Set.of()));FRAMES.remove(ship);SECTIONS.remove(ship);MASKS.remove(ship);return;}
        var old=MASKS.put(ship,region);
        if(old!=null && old.x==region.x && old.y==region.y && old.z==region.z && old.sx==region.sx && old.sy==region.sy && old.sz==region.sz && Arrays.equals(old.bytes(),region.bytes()))return;
        // The first mask can arrive after vanilla built a settled water chunk.
        // Rebuild it once even when no block volumes change afterwards.
        BitSet bits=BitSet.valueOf(region.bytes());
        for(int at=bits.nextSetBit(0);at>=0;at=bits.nextSetBit(at+1)){
            int x=region.x+at/(region.sy*region.sz),y=region.y+at/region.sz%region.sy,z=region.z+at%region.sz;
            DIRTY.add(SectionPos.asLong(x>>4,y>>4,z>>4));
        }
    }
    public static void volumes(Map<Long,Integer> previous,Map<Long,Integer> replacement){
        Set<Long> positions=new HashSet<>(previous.keySet());positions.addAll(replacement.keySet());
        for(long packed:positions)if(!Objects.equals(previous.get(packed),replacement.get(packed)))DIRTY.add(SectionPos.of(BlockPos.of(packed)).asLong());
    }

    /** Called only for finite water belonging to a managed ship, never ordinary ocean meshes. */
    public static boolean managed(BlockPos pos,FluidState fluid){
        var client=Minecraft.getInstance().level;if(client==null || !fluid.is(FluidTags.WATER))return false;
        return VSGameUtilsKt.getShipManagingPos(client,pos)!=null;
    }
    public static boolean render(BlockAndTintGetter level,BlockPos pos,VertexConsumer consumer,BlockState state,FluidState fluid){
        if(!managed(pos,fluid))return false;var client=Minecraft.getInstance().level;
        var ship=VSGameUtilsKt.getShipManagingPos(client,pos);
        var t=ship.getShipToWorld();Frame frame=FRAMES.computeIfAbsent(ship.getId(),id->Frame.of(t.m01(),t.m11(),t.m21()));
        SECTIONS.computeIfAbsent(ship.getId(),id->ConcurrentHashMap.newKeySet()).add(SectionPos.of(pos).asLong());
        int q=FloodZones.volume(client,ship.getId(),pos,Math.min(8,fluid.getAmount()));if(q<=0)return true;
        var sprite=ForgeHooksClient.getFluidSprites(level,pos,fluid)[0];
        int color=IClientFluidTypeExtensions.of(fluid).getTintColor(fluid,level,pos),light=LevelRenderer.getLightColor(level,pos);
        float red=((color>>16)&255)/255f,green=((color>>8)&255)/255f,blue=(color&255)/255f,alpha=((color>>>24)&255)/255f;
        for(var face:frame.meshes.get(q)){
            List<FloodGeometry.Point> polygon=face.points();
            if(face.direction()>=0){
                Direction direction=Direction.values()[face.direction()];var neighbor=level.getFluidState(pos.relative(direction));
                if(neighbor.is(FluidTags.WATER)){
                    int other=FloodZones.volume(client,ship.getId(),pos.relative(direction),Math.min(8,neighbor.getAmount()));
                    double boundary=FloodGeometry.plane(frame.depths[other],frame.x,frame.y,frame.z)
                            +direction.getStepX()*frame.x+direction.getStepY()*frame.y+direction.getStepZ()*frame.z;
                    polygon=FloodGeometry.clip(polygon,-frame.x,-frame.y,-frame.z,-boundary-1e-6);
                }else if(level.getBlockState(pos.relative(direction)).isSolidRender(level,pos.relative(direction)))continue;
            }
            if(polygon.size()<3)continue;
            float nx=face.direction()<0?(float)frame.x:Direction.values()[face.direction()].getStepX();
            float ny=face.direction()<0?(float)frame.y:Direction.values()[face.direction()].getStepY();
            float nz=face.direction()<0?(float)frame.z:Direction.values()[face.direction()].getStepZ();
            float shade=face.direction()<0?1:face.direction()==0?.5f:.85f;
            // Minecraft's translucent buffer uses quads. A repeated fourth
            // vertex represents each triangle of the convex clipped polygon.
            for(int i=1;i+1<polygon.size();i++)for(int side=0;side<2;side++){
                int[] order=side==0?new int[]{0,i,i+1,i+1}:new int[]{i+1,i,0,0};
                for(int index:order){var p=polygon.get(index);
                    float u=sprite.getU((face.direction()==4||face.direction()==5?p.z():p.x())*16);
                    float v=sprite.getV((face.direction()==0||face.direction()==1||face.direction()<0?p.z():1-p.y())*16);
                    consumer.vertex((pos.getX()&15)+p.x(),(pos.getY()&15)+p.y(),(pos.getZ()&15)+p.z())
                            .color(red*shade,green*shade,blue*shade,alpha).uv(u,v).uv2(light).normal(nx,ny,nz).endVertex();
                }
            }
        }
        return true;
    }
}
