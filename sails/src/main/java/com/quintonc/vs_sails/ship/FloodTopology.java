package com.quintonc.vs_sails.ship;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.primitives.AABBi;
import org.valkyrienskies.core.api.ships.LoadedServerShip;

/** An edit-coalesced, incremental topology job. Every world read is loaded-only. */
public final class FloodTopology {
    public static final int MAX_SCAN=1048576;
    public record Mouth(int node,Direction direction) {}
    public record Result(long[] cells,Map<Long,Integer> index,FloodModel model,List<Mouth> mouths) {}
    public static final class Job {
        private final AABBi b;
        private final int sx,sy,sz,size;
        private final Map<Integer,VoxelShape> shapes=new HashMap<>();
        private final BitSet blocked=new BitSet(),full=new BitSet();
        private final Map<Integer,Integer> initial=new HashMap<>();
        private final Map<Long,Integer> oldWater;
        private final Set<Long> previous=new HashSet<>();
        private final int[] minX,maxX,minZ,maxZ,minY;
        private final List<Long> cells=new ArrayList<>();
        private final Map<Long,Integer> index=new HashMap<>();
        private final List<int[]> edges=new ArrayList<>();
        private final List<Mouth> mouths=new ArrayList<>();
        private final ArrayDeque<Integer> queue=new ArrayDeque<>();
        private int cursor,stage,reserve,componentCount,componentCursor;
        private int[] components;
        public boolean waiting,failed;
        public Result result;

        public Job(LoadedServerShip ship,FloodState state){
            b=new AABBi(ship.getShipAABB());
            for(long packed:state.interior)include(BlockPos.of(packed));
            for(long packed:state.water.keySet())include(BlockPos.of(packed));
            sx=b.maxX-b.minX;sy=b.maxY-b.minY;sz=b.maxZ-b.minZ;
            long volume=(long)sx*sy*sz;
            if(sx<=0 || sy<=0 || sz<=0 || volume>MAX_SCAN)throw new IllegalArgumentException("Flood scan bounds exceed budget");
            size=(int)volume;oldWater=state.water;reserve=state.reserve;
            for(long p:state.interior)previous.add(p);
            minX=new int[sy*sz];maxX=new int[sy*sz];minZ=new int[sx*sy];maxZ=new int[sx*sy];minY=new int[sx*sz];
            Arrays.fill(minX,Integer.MAX_VALUE);Arrays.fill(minZ,Integer.MAX_VALUE);Arrays.fill(minY,Integer.MAX_VALUE);
            Arrays.fill(maxX,Integer.MIN_VALUE);Arrays.fill(maxZ,Integer.MIN_VALUE);
        }
        private void include(BlockPos p){
            b.minX=Math.min(b.minX,p.getX());b.minY=Math.min(b.minY,p.getY());b.minZ=Math.min(b.minZ,p.getZ());
            b.maxX=Math.max(b.maxX,p.getX()+1);b.maxY=Math.max(b.maxY,p.getY()+1);b.maxZ=Math.max(b.maxZ,p.getZ()+1);
        }
        public int work(ServerLevel level,LoadedServerShip ship,int budget){
            waiting=false;int used=0;
            while(used<budget && result==null && !failed){
                if(stage==0){
                    if(cursor>=size){stage=1;cursor=0;continue;}
                    BlockPos p=position(cursor);var chunk=level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4);
                    if(chunk==null){waiting=true;break;}
                    BlockState state=chunk.getBlockState(p);
                    VoxelShape shape=state.getCollisionShape(level,p);
                    boolean fillable=DoorWater.canContain(state);
                    if(!fillable){blocked.set(cursor);shape=Shapes.block();}
                    if(!shape.isEmpty()){
                        if(shape==Shapes.block())full.set(cursor);else shapes.put(cursor,shape);
                        int x=p.getX()-b.minX,y=p.getY()-b.minY,z=p.getZ()-b.minZ;
                        int yz=y*sz+z,xy=x*sy+y,xz=x*sz+z;
                        minX[yz]=Math.min(minX[yz],x);maxX[yz]=Math.max(maxX[yz],x);
                        minZ[xy]=Math.min(minZ[xy],z);maxZ[xy]=Math.max(maxZ[xy],z);minY[xz]=Math.min(minY[xz],y);
                    }
                    int old=oldWater.getOrDefault(p.asLong(),0);
                    if(fillable && (old>0 || state.getFluidState().is(FluidTags.WATER)))
                        initial.put(cursor,old>0?old:Math.max(1,Math.min(8,state.getFluidState().getAmount())));
                    else if(!fillable && old>0)reserve+=old;
                    cursor++;used++;
                }else if(stage==1){
                    if(cursor>=size){stage=2;cursor=0;continue;}
                    int at=cursor++;used++;
                    if(blocked.get(at))continue;
                    BlockPos p=position(at);
                    if(full.get(at) && !initial.containsKey(at) && !previous.contains(p.asLong()))continue;
                    int x=p.getX()-b.minX,y=p.getY()-b.minY,z=p.getZ()-b.minZ;
                    int walls=(x>minX[y*sz+z]?1:0)+(x<maxX[y*sz+z]?1:0)
                            +(z>minZ[x*sy+y]?1:0)+(z<maxZ[x*sy+y]?1:0);
                    boolean enclosed=walls==4 || walls>=3 && y>minY[x*sz+z];
                    if(!enclosed && !previous.contains(p.asLong()) && !initial.containsKey(at))continue;
                    if(cells.size()>=FloodModel.MAX_CELLS){failed=true;break;}
                    index.put(p.asLong(),cells.size());cells.add(p.asLong());
                }else if(stage==2){
                    if(cursor>=cells.size()){
                        components=new int[cells.size()];Arrays.fill(components,-1);cursor=0;stage=3;continue;
                    }
                    int node=cursor++;BlockPos p=BlockPos.of(cells.get(node));List<Integer> connected=new ArrayList<>();
                    VoxelShape from=shape(p);
                    for(Direction d:Direction.values()){
                        BlockPos to=p.relative(d);
                        if(Shapes.mergedFaceOccludes(from,shape(to),d))continue;
                        Integer next=index.get(to.asLong());
                        if(next==null)mouths.add(new Mouth(node,d));else connected.add(next);
                    }
                    edges.add(connected.stream().mapToInt(Integer::intValue).toArray());used++;
                }else if(stage==3){
                    if(queue.isEmpty()){
                        while(componentCursor<components.length && components[componentCursor]>=0)componentCursor++;
                        if(componentCursor==components.length){stage=4;continue;}
                        components[componentCursor]=componentCount++;queue.add(componentCursor);
                    }
                    int node=queue.removeFirst();int group=components[node];
                    for(int next:edges.get(node))if(components[next]<0){components[next]=group;queue.addLast(next);}
                    used++;
                }else{
                    long[] positions=cells.stream().mapToLong(Long::longValue).toArray();int n=positions.length;
                    int[] volumes=new int[n];double[] floor=new double[n],height=new double[n],localX=new double[n],localZ=new double[n];
                    for(int i=0;i<n;i++){
                        BlockPos p=BlockPos.of(positions[i]);volumes[i]=initial.getOrDefault(linear(p),0);
                        floor[i]=p.getY()-ship.getTransform().getPositionInShip().y();height[i]=1;
                        localX[i]=p.getX()+.5-ship.getTransform().getPositionInShip().x();
                        localZ[i]=p.getZ()+.5-ship.getTransform().getPositionInShip().z();
                    }
                    var graph=new FloodModel.Graph(edges.toArray(int[][]::new),components,floor,height,componentCount).coordinates(localX,localZ);
                    var model=new FloodModel(graph,volumes,reserve);
                    result=new Result(positions,Map.copyOf(index),model,List.copyOf(mouths));
                }
            }
            return used;
        }
        private int linear(BlockPos p){return ((p.getX()-b.minX)*sy+p.getY()-b.minY)*sz+p.getZ()-b.minZ;}
        private BlockPos position(int i){return new BlockPos(b.minX+i/(sy*sz),b.minY+i/sz%sy,b.minZ+i%sz);}
        private VoxelShape shape(BlockPos p){
            if(p.getX()<b.minX || p.getX()>=b.maxX || p.getY()<b.minY || p.getY()>=b.maxY || p.getZ()<b.minZ || p.getZ()>=b.maxZ)return Shapes.empty();
            int at=linear(p);return full.get(at)?Shapes.block():shapes.getOrDefault(at,Shapes.empty());
        }
    }
    private FloodTopology(){}
}
