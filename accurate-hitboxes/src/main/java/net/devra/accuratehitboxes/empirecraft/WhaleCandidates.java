package net.devra.accuratehitboxes.empirecraft;

import java.util.*;
import java.util.function.Predicate;
import net.devra.accuratehitboxes.config.ConfigManager;
import net.devra.accuratehitboxes.network.*;
import net.devra.accuratehitboxes.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/** Broader candidate discovery, followed by the existing precise model tests. */
public final class WhaleCandidates {
    public static final double MODEL_OVERHANG=64;
    private WhaleCandidates(){}
    public static boolean supported(Entity entity){
        var id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id!=null && id.getNamespace().equals("wonderoussea") && entity instanceof LivingEntity && !ConfigManager.isBlacklisted(entity);
    }
    public static List<Entity> query(Level level,Entity source,AABB search,Predicate<? super Entity> predicate){
        List<Entity> result=level.getEntities(source,search,predicate);
        List<Entity> extra=level.getEntities(source,search.inflate(MODEL_OVERHANG),e->supported(e)&&predicate.test(e));
        if(extra.isEmpty())return result;
        var all=new ArrayList<>(result);var seen=new HashSet<>(result);
        for(var entity:extra)if(seen.add(entity))all.add(entity);
        return all;
    }
    public static List<OrientedBoundingBox> clientParts(Entity entity){
        if(!supported(entity)||ConfigManager.isEffectivelyBlacklisted(entity)||!(entity instanceof IAccurateEntity accurate))return List.of();
        return accurate.accuratehitboxes$getHitboxes();
    }
    public static ModelBox model(OrientedBoundingBox box){
        var b=box.localBox;var m=new Matrix4d(box.transform);
        Vector3d center=m.transformPosition(new Vector3d((b.minX+b.maxX)*.5,(b.minY+b.maxY)*.5,(b.minZ+b.maxZ)*.5))
                .add(box.offset.x,box.offset.y,box.offset.z);
        // A model fin can be a zero-thickness plane. A sub-pixel tolerance keeps
        // it representable for SAT without replacing it with the whale's hull.
        double x=Math.max(1e-5,(b.maxX-b.minX)*.5),y=Math.max(1e-5,(b.maxY-b.minY)*.5),z=Math.max(1e-5,(b.maxZ-b.minZ)*.5);
        return new ModelBox(center,m.transformDirection(new Vector3d(x,0,0)),m.transformDirection(new Vector3d(0,y,0)),m.transformDirection(new Vector3d(0,0,z)));
    }
    public static boolean clear(Level level,Entity actor,Vec3 origin,Vec3 point){
        return level.clip(new ClipContext(origin,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor)).getType()!=HitResult.Type.BLOCK;
    }
    /** Null preserves Forge's normal reach implementation when no model is available. */
    public static Boolean inReach(Player player,Entity target,double distance){
        if(!supported(target)||player.level().isClientSide)return null;
        var boxes=serverParts(player,target);if(boxes==null||boxes.isEmpty())return null;
        if(!Double.isFinite(distance)||distance<0)return false;
        Vec3 eye=player.getEyePosition();Vector3d origin=new Vector3d(eye.x,eye.y,eye.z);
        for(var part:boxes){
            if(part.offset.distanceToSqr(target.position())>64)continue;
            try{
                var frame=model(part);var nearest=frame.closest(origin);
                if(nearest.distanceSquared(origin)>distance*distance+1e-6)continue;
                if(clear(player.level(),player,eye,new Vec3(nearest.x,nearest.y,nearest.z)))return true;
                if(distance>0){
                    var reach=new ModelBox(origin,new Vector3d(distance,0,0),new Vector3d(0,distance,0),new Vector3d(0,0,distance));
                    for(var contact:frame.contacts(reach,origin))if(contact.distanceSquared(origin)<=distance*distance+1e-6
                            && clear(player.level(),player,eye,new Vec3(contact.x,contact.y,contact.z)))return true;
                }
            }catch(IllegalArgumentException invalid){/* Retain other valid model parts. */}
        }
        return false;
    }
    private static List<OrientedBoundingBox> serverParts(Player player,Entity target){
        var shared=SharedMemoryBridge.get(target.getUUID());if(shared!=null&&!shared.isEmpty())return shared;
        var dynamic=DynamicHitboxManager.get(player.getUUID(),target.getUUID(),player.level().getGameTime());
        if(dynamic!=null&&!dynamic.isEmpty())return dynamic;
        var blueprint=BlueprintManager.serverBlueprints.get(BlueprintPose.getCacheKey(target));
        if(blueprint==null||blueprint.isEmpty())blueprint=BlueprintManager.serverBlueprints.get(BlueprintPose.getBaseCacheKey(target));
        if(blueprint==null||blueprint.isEmpty())return List.of();
        float yaw=target instanceof LivingEntity living?living.yBodyRot:target.getYRot();
        Matrix4f rotation=BlueprintPose.createWorldTransform(target,yaw);
        List<OrientedBoundingBox> transformed=new ArrayList<>(blueprint.size());
        for(var part:blueprint)transformed.add(new OrientedBoundingBox(part.localBox,new Matrix4f(rotation).mul(part.transform),target.position()));
        return transformed;
    }
}
