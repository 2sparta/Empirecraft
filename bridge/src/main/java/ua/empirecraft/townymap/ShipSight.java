package ua.empirecraft.townymap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
/** A world segment tested against loaded world/ship voxels in their own frames. */
public final class ShipSight {
    public record Hit(BlockPos pos,BlockState state,Ship ship,Vec3 location,Vec3 normal,BlockHitResult result) {}
    private ShipSight() {}
    public static Vec3 worldPoint(Ship ship,Vec3 p) {var v=ship.getShipToWorld().transformPosition(new Vector3d(p.x,p.y,p.z));return new Vec3(v.x,v.y,v.z);}
    public static Vec3 localPoint(Ship ship,Vec3 p) {var v=ship.getWorldToShip().transformPosition(new Vector3d(p.x,p.y,p.z));return new Vec3(v.x,v.y,v.z);}
    public static Vec3 worldDirection(Ship ship,Vec3 p) {var v=ship.getTransform().getShipToWorldRotation().transform(new Vector3d(p.x,p.y,p.z));return new Vec3(v.x,v.y,v.z);}
    public static Hit first(Level level,Entity entity,Vec3 from,Vec3 to,boolean terrain,Predicate<BlockState> fullVoxel) {
        if(!finite(from) || !finite(to) || from.distanceToSqr(to)>512*512)return null;
        Hit best=terrain?inFrame(level,entity,from,to,null,fullVoxel):null;int count=0;
        for(Ship ship:VSGameUtilsKt.getShipsIntersecting(level,new AABB(from,to).inflate(.01))) {
            if(++count>64)return unknown(from,BlockPos.containing(from),null);
            Hit hit=inFrame(level,entity,from,to,ship,fullVoxel);
            if(hit!=null && (best==null || from.distanceToSqr(hit.location)<from.distanceToSqr(best.location)))best=hit;
        }
        return best;
    }
    private static Hit unknown(Vec3 point,BlockPos pos,Ship ship) {return new Hit(pos,null,ship,point,Vec3.ZERO,new BlockHitResult(point,Direction.UP,pos,false));}
    private static Hit inFrame(Level level,Entity entity,Vec3 from,Vec3 to,Ship ship,Predicate<BlockState> fullVoxel) {
        Vec3 a=ship==null?from:localPoint(ship,from),b=ship==null?to:localPoint(ship,to);
        if(!finite(a) || !finite(b) || a.distanceToSqr(b)>1024*1024)return unknown(from,BlockPos.containing(a),ship);
        int[] visited={0};
        return BlockGetter.traverseBlocks(a,b,entity,(ignored,mutable)->{
            if(++visited[0]>2048)return unknown(from,mutable.immutable(),ship);
            var chunk=level instanceof net.minecraft.server.level.ServerLevel server?server.getChunkSource().getChunkNow(mutable.getX()>>4,mutable.getZ()>>4):level.getChunkSource().getChunk(mutable.getX()>>4,mutable.getZ()>>4,ChunkStatus.FULL,false);
            if(chunk==null)return unknown(from,mutable.immutable(),ship);
            BlockState state=chunk.getBlockState(mutable);if(state.isAir())return null;
            var shape=fullVoxel.test(state)?Shapes.block():state.getCollisionShape(level,mutable,entity==null?CollisionContext.empty():CollisionContext.of(entity));
            BlockHitResult hit=shape.clip(a,b,mutable);if(hit==null)return null;
            Vec3 position=ship==null?hit.getLocation():worldPoint(ship,hit.getLocation());
            Vec3 normal=Vec3.atLowerCornerOf(hit.getDirection().getNormal());if(ship!=null)normal=worldDirection(ship,normal);
            return new Hit(mutable.immutable(),state,ship,position,normal,new BlockHitResult(position,hit.getDirection(),mutable.immutable(),hit.isInside()));
        },ignored->null);
    }
    private static boolean finite(Vec3 p) {return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);}
}
