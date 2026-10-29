package ua.empirecraft.shipcannons;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.joml.Vector3d;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.config.CBCCfgMunitions;
import rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst;

public final class GrapeshotDamage {
    private static final GameProfile ACTOR=new GameProfile(UUID.fromString("f19dbfd2-80ce-40c6-b371-a9429faf9568"),"[EmpirecraftCannon]");
    private static final TagKey<net.minecraft.world.level.block.Block> LIGHT=TagKey.create(Registries.BLOCK,new ResourceLocation("empirecraft_ship_cannons","grapeshot_light"));
    private static final TagKey<net.minecraft.world.level.block.Block> RESISTANT=TagKey.create(Registries.BLOCK,new ResourceLocation("empirecraft_ship_cannons","grapeshot_resistant"));
    private GrapeshotDamage() {}
    public static boolean light(BlockState state) {
        if(state.is(RESISTANT) || state.hasBlockEntity() || state.isAir())return false;
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());String path=id.getPath();
        return state.is(LIGHT) || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.LEAVES)
            || id.getNamespace().equals("minecraft") && (path.endsWith("_wool") || path.endsWith("_carpet"))
            || id.getNamespace().equals("vs_sails") && (path.endsWith("_sail") || path.equals("sail_block") || path.equals("magma_sail_block"))
            || id.getNamespace().equals("create") && (path.equals("sail") || path.equals("sail_frame"));
    }
    public static <T extends ProjectileBurst> T spawn(Level level,EntityType<T> type,Vec3 point,Vec3 velocity,int requested,double spread,Projectile parent) {
        T burst=type.create(level);if(burst==null)return null;
        var ship=org.valkyrienskies.mod.common.VSGameUtilsKt.getShipManagingPos(level,point.x,point.y,point.z);
        if(ship!=null) {point=ShipRaycast.worldPoint(ship,point);velocity=ShipRaycast.worldDirection(ship,velocity);}
        if(ship==null && parent.getPersistentData().contains(CBCRecoil.SHIP))
            ship=org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(parent.getPersistentData().getLong(CBCRecoil.SHIP));
        Vector3d carrier=new Vector3d();
        if(ship!=null)carrier.set(ship.getAngularVelocity()).cross(new Vector3d(point.x,point.y,point.z).sub(ship.getTransform().getPositionInWorld())).add(ship.getVelocity()).div(20);
        burst.setPos(point);burst.setOwner(parent.getOwner());
        for(String key:new String[]{CBCRecoil.SOURCE,CBCRecoil.VEHICLE})
            if(parent.getPersistentData().hasUUID(key))burst.getPersistentData().putUUID(key,parent.getPersistentData().getUUID(key));
        int count=Math.max(1,Math.min(requested,CannonRecoil.maximumPellets()));
        double phase=level.random.nextDouble()*Math.PI*2;
        for(int i=0;i<count;i++) {
            Vector3d v=GrapeshotMath.pellet(new Vector3d(velocity.x,velocity.y,velocity.z),i,count,Math.max(spread,CannonRecoil.grapeshotSpread()),phase);
            burst.addSubProjectile(0,0,0,v.x+carrier.x,v.y+carrier.y,v.z+carrier.z);
        }
        level.addFreshEntity(burst);return burst;
    }
    /** Each pellet tears only cells on its flight path. Solid hulls and protected cloth stop it. */
    public static HitResult clip(ProjectileBurst burst,ProjectileBurst.SubProjectile pellet,Predicate<Entity> canHit) {
        return clip(burst,pellet,canHit,hit->damageAllowed() && canBreak((ServerLevel)burst.level(),hit));
    }
    public static HitResult clip(ProjectileBurst burst,ProjectileBurst.SubProjectile pellet,Predicate<Entity> canHit,Predicate<ShipRaycast.Hit> permission) {
        Vec3 start=burst.position().add(pellet.displacement()[0],pellet.displacement()[1],pellet.displacement()[2]);
        Vec3 velocity=new Vec3(pellet.velocity()[0],pellet.velocity()[1],pellet.velocity()[2]);
        Vec3 end=start.add(velocity),direction=velocity.normalize();
        if(!(burst.level() instanceof ServerLevel level) || velocity.lengthSqr()>256*256 || velocity.lengthSqr()<1e-12)
            return miss(start,end);
        // Find entities before tearing cloth, so a body can stop a pellet before a sail behind it.
        var entityHit=ProjectileUtil.getEntityHitResult(level,burst,start,end,new AABB(start,end).inflate(.4),e->canHit.test(e) && !source(burst,e),.15f);
        Vec3 limit=entityHit==null?end:entityHit.getLocation();
        var budget=(GrapeshotState)burst;
        for(int layer=0;layer<=CannonRecoil.lightLayers();layer++) {
            var hit=ShipRaycast.first(level,burst,start,limit,true,GrapeshotDamage::light);
            if(hit==null)return entityHit==null?miss(start,end):entityHit;
            if(hit.state()==null) {budget.empirecraft$stopAt(hit.pos());return hit.result();}
            if(!light(hit.state()) || hit.state().getDestroySpeed(level,hit.pos())<0 || layer==CannonRecoil.lightLayers()
                || budget.empirecraft$broken()>=CannonRecoil.maximumGrapeshotBlocks()
                || !permission.test(hit) || !level.destroyBlock(hit.pos(),false))return hit.result();
            budget.empirecraft$setBroken(budget.empirecraft$broken()+1);
            start=hit.location().add(direction.scale(.001));
            if(start.subtract(limit).dot(direction)>=0)return entityHit==null?miss(start,end):entityHit;
        }
        return miss(start,end);
    }
    private static boolean source(Entity burst,Entity target) {
        if(burst.tickCount>2)return false;
        for(String key:new String[]{CBCRecoil.SOURCE,CBCRecoil.VEHICLE})
            if(burst.getPersistentData().hasUUID(key) && burst.getPersistentData().getUUID(key).equals(target.getUUID()))return true;
        return false;
    }
    private static BlockHitResult miss(Vec3 start,Vec3 end) {
        Vec3 d=end.subtract(start);return BlockHitResult.miss(end,Direction.getNearest(d.x,d.y,d.z),BlockPos.containing(end));
    }
    public static boolean damageAllowed() {return CBCConfigs.server().munitions.damageRestriction.get()!=CBCCfgMunitions.GriefState.NO_DAMAGE;}
    public static boolean canBreak(ServerLevel level,ShipRaycast.Hit hit) {
        Vec3 world=Vec3.atCenterOf(hit.pos());if(hit.ship()!=null)world=ShipRaycast.worldPoint(hit.ship(),world);
        BlockPos physical=BlockPos.containing(world);
        if(!level.getWorldBorder().isWithinBounds(physical))return false;
        var actor=FakePlayerFactory.get(level,ACTOR);actor.setPos(world);
        if(level.getServer().isUnderSpawnProtection(level,physical,actor))return false;
        return ForgeHooks.onBlockBreakEvent(level,GameType.SURVIVAL,actor,hit.pos())>=0;
    }
}
