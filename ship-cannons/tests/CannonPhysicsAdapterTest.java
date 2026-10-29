package ua.empirecraft.shipcannons.test;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import java.lang.Math;
import org.mockito.*;
import static org.mockito.Mockito.*;
import org.valkyrienskies.core.api.ships.*;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.*;
import rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst;
import ua.empirecraft.shipcannons.*;

/** Production adapters with vanilla shapes and simulated loaded storage; no native physics server. */
public final class CannonPhysicsAdapterTest {
    private static int checks;
    private static void check(boolean ok,String message) {checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        var out=System.out;var err=System.err;
        try {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();} finally {System.setOut(out);System.setErr(err);}
        var config=CommentedConfig.inMemory();CannonRecoil.SPEC.correct(config);CannonRecoil.SPEC.setConfig(config);
        recoil();grapeshot(false,false,false,false);grapeshot(true,false,false,false);
        grapeshot(false,true,false,false);grapeshot(false,false,true,false);grapeshot(false,false,false,true);
        carriage();landRouting();nativeCarriageMotion();System.out.println("CANNON_ADAPTER_PASS checks="+checks);
    }
    private static Vector3d simulate(double dt,int shots,boolean opposite,Quaterniond rotation) {
        var controller=new RecoilController(33);var body=mock(PhysShip.class);var transform=mock(ShipTransform.class);
        var inertia=new Matrix3d().scaling(100000,200000,300000);var omega=new Vector3d();var velocity=new Vector3d();
        var torque=new Vector3d();var force=new Vector3d();
        when(body.getId()).thenReturn(33L);when(body.getMass()).thenReturn(10000.0);when(body.getMomentOfInertia()).thenReturn(inertia);
        when(body.getAngularVelocity()).thenAnswer(a->new Vector3d(omega));when(body.getTransform()).thenReturn(transform);
        when(transform.getPositionInWorld()).thenReturn(new Vector3d());when(transform.getShipToWorldRotation()).thenReturn(rotation);
        when(body.getShipToWorld()).thenReturn(new Matrix4d().rotate(rotation));
        doAnswer(a->{torque.add((Vector3dc)a.getArgument(0));return null;}).when(body).applyInvariantTorque(any());
        doAnswer(a->{force.add((Vector3dc)a.getArgument(0));return null;}).when(body).applyInvariantForce(any());
        for(int i=0;i<shots;i++)controller.enqueue(new Vector3d(3,2,0),new Vector3d(-1000,0,0));
        if(opposite)for(int i=0;i<shots;i++)controller.enqueue(new Vector3d(-3,2,0),new Vector3d(1000,0,0));
        for(double t=0;t<.5;t+=dt) {
            torque.zero();force.zero();controller.physTick(body,null,dt);
            var local=rotation.transformInverse(new Vector3d(torque).mul(dt));
            omega.add(rotation.transform(RecoilMath.angularVelocity(inertia,local)));velocity.fma(dt/10000,force);
        }
        check(controller.applied==shots*(opposite?2:1),"Every shot applied once");
        check(omega.isFinite() && omega.length()<=RecoilMath.DEFAULT.angularSpeedLimit()+1e-9,"Salvo speed cap");
        if(!opposite)check(velocity.distance(rotation.transform(new Vector3d(-.1*shots,0,0)))<1e-8,"Linear impulse conserved");
        return omega;
    }
    private static void recoil() {
        var sixty=simulate(1.0/60,1,false,new Quaterniond());
        check(sixty.z>0,"Starboard shot heels away from muzzle");
        check(sixty.distance(simulate(1.0/30,1,false,new Quaterniond()))<1e-9,"30/60 Hz same impulse");
        check(sixty.distance(simulate(1.0/120,1,false,new Quaterniond()))<1e-9,"60/120 Hz same impulse");
        check(simulate(1.0/60,4,false,new Quaterniond()).z>sixty.z*3.99,"Broadside accumulates");
        check(simulate(1.0/60,60,true,new Quaterniond()).length()<1e-9,"Opposed salvos cancel before limiting");
        simulate(1.0/60,200,false,new Quaterniond());
        var q=new Quaterniond().rotateY(1.2).rotateX(.2).rotateZ(.3);
        check(simulate(1.0/60,1,false,q).distance(q.transform(new Vector3d(sixty)))<1e-8,"Heeled ship impulse rotates correctly");
        var controller=new RecoilController(33);var wrong=mock(PhysShip.class);when(wrong.getId()).thenReturn(34L);
        controller.enqueue(new Vector3d(),new Vector3d(1,0,0));controller.physTick(wrong,null,.05);verify(wrong,never()).applyInvariantForce(any());
        var fixed=mock(PhysShip.class);when(fixed.getId()).thenReturn(33L);when(fixed.isStatic()).thenReturn(true);
        controller.physTick(fixed,null,.05);verify(fixed,never()).applyInvariantForce(any());
        check(!controller.enqueue(new Vector3d(Double.NaN,0,0),new Vector3d(1,0,0)),"Invalid shot rejected");
        System.out.println("RECOIL_ADAPTER_PASS fixed,foreign,rotated,opposed,salvo,30/60/120Hz");
    }
    private static void grapeshot(boolean rotated,boolean wall,boolean denied,boolean budget) {
        var level=mock(ServerLevel.class,withSettings().extraInterfaces(DimensionIdProvider.class));var cache=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);
        var server=mock(net.minecraft.server.MinecraftServer.class,withSettings().extraInterfaces(org.valkyrienskies.mod.common.IShipObjectWorldServerProvider.class));
        var world=mock(org.valkyrienskies.core.internal.world.VsiServerShipWorld.class);var ships=mock(org.valkyrienskies.core.internal.ships.VsiQueryableShipData.class);
        when(level.getServer()).thenReturn(server);when(((org.valkyrienskies.mod.common.IShipObjectWorldServerProvider)server).getShipObjectWorld()).thenReturn(world);
        when(world.getAllShips()).thenReturn(ships);when(((DimensionIdProvider)level).getDimensionId()).thenReturn("fixture");
        var burst=mock(ProjectileBurst.class,withSettings().extraInterfaces(GrapeshotState.class));
        var count=new AtomicInteger(budget?63:0);var tears=new HashSet<BlockPos>();var checked=new AtomicInteger();
        when(level.getChunkSource()).thenReturn(cache);when(cache.getChunk(anyInt(),anyInt(),eq(ChunkStatus.FULL),eq(false))).thenReturn(chunk);
        int offset=rotated?-28672000:0;
        var q=rotated?new Quaterniond().rotateY(.7).rotateX(.15).rotateZ(-.2):new Quaterniond();
        var matrix=new Matrix4d().translate(4,0,7).rotate(q).translate(-offset,0,0);
        var ship=mock(LoadedServerShip.class);var transform=mock(ShipTransform.class);
        when(ship.getId()).thenReturn(33L);when(ship.getShipToWorld()).thenReturn(matrix);when(ship.getWorldToShip()).thenReturn(matrix.invert(new Matrix4d()));
        when(ship.getTransform()).thenReturn(transform);when(transform.getShipToWorldRotation()).thenReturn(q);
        when(chunk.getBlockState(any())).thenAnswer(a->{BlockPos p=a.getArgument(0);if(tears.contains(p))return Blocks.AIR.defaultBlockState();
            if(p.getY()>=50 && p.getY()<=80 && p.getX()>=offset-12 && p.getX()<=offset+12) {
                if(wall && p.getZ()==10)return Blocks.IRON_BLOCK.defaultBlockState();
                if(p.getZ()>=20 && p.getZ()<=22)return Blocks.WHITE_WOOL.defaultBlockState();
            }return Blocks.AIR.defaultBlockState();});
        when(level.getBlockState(any())).thenAnswer(a->chunk.getBlockState(a.getArgument(0)));
        when(level.destroyBlock(any(BlockPos.class),eq(false))).thenAnswer(a->tears.add(((BlockPos)a.getArgument(0)).immutable()));
        when(burst.level()).thenReturn(level);
        Vec3 origin=new Vec3(offset+.5,65.5,0);if(rotated)origin=ShipRaycast.worldPoint(ship,origin);
        when(burst.position()).thenReturn(origin);when(((GrapeshotState)burst).empirecraft$broken()).thenAnswer(a->count.get());
        doAnswer(a->{count.set(a.getArgument(0));return null;}).when((GrapeshotState)burst).empirecraft$setBroken(anyInt());
        when(ships.getIntersecting(any(org.joml.primitives.AABBdc.class),eq("fixture"))).thenReturn(rotated?List.of(ship):List.of());
        {
            for(int i=0;i<25;i++) {
                var v=GrapeshotMath.pellet(new Vector3d(0,0,20),i,25,.25,.7);if(rotated)q.transform(v);
                var pellet=new ProjectileBurst.SubProjectile(new double[3],new double[]{v.x,v.y,v.z});
                var hit=GrapeshotDamage.clip(burst,pellet,e->true,cell->{checked.incrementAndGet();return !denied;});
                if(wall || denied)check(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK,"Pellet stops at wall/denial");
            }
            if(wall || denied)check(tears.isEmpty(),"No damage through armor or protection");
            else if(budget)check(tears.size()==1 && count.get()==64,"Per-burst budget survives later pellets");
            else {
                check(tears.size()>=45,"Wide sail damage from separate pellets: "+tears.size());
                check(tears.stream().map(BlockPos::getX).distinct().count()>=6,"Broad horizontal spread");
                check(tears.stream().map(BlockPos::getY).distinct().count()>=6,"Broad vertical spread");
            }
            if(!wall && !denied)check(checked.get()==tears.size(),"Permissions checked for each torn voxel");
            verify(cache,never()).getChunk(anyInt(),anyInt(),any(),eq(true));
        }
        System.out.println("GRAPESHOT_ADAPTER_PASS rotated="+rotated+" wall="+wall+" protected="+denied+" exhausted="+budget+" broken="+tears.size());
    }
    private static void carriage() {
        var entity=mock(Entity.class,withSettings().extraInterfaces(IEntityDraggingInformationProvider.class));
        var info=new EntityDraggingInformation();when(((IEntityDraggingInformationProvider)entity).getDraggingInformation()).thenReturn(info);
        var ship=mock(LoadedServerShip.class);when(ship.getId()).thenReturn(33L);
        var hit=new ShipRaycast.Hit(BlockPos.ZERO,Blocks.OAK_PLANKS.defaultBlockState(),ship,Vec3.ZERO,new Vec3(0,1,0),null);
        CarriageDeck.refresh(entity,hit,null);check(info.getLastShipStoodOn()==33L && !info.getShouldImpulseMovement(),"Native drag attached without artificial acceleration");
        CarriageDeck.refresh(entity,null,33L);check(info.getLastShipStoodOn()==null,"No magnetic attachment after leaving deck");
        info.setLastShipStoodOn(34L);CarriageDeck.refresh(entity,null,33L);check(info.getLastShipStoodOn()==34L,"Another ship's contact retained");
        verify(entity,never()).setPos(anyDouble(),anyDouble(),anyDouble());
        System.out.println("CARRIAGE_ADAPTER_PASS native_motion_single_owner,detach,ship_switch");
        var anchored=mock(LoadedServerShip.class);when(anchored.isStatic()).thenReturn(true);when(anchored.getId()).thenReturn(33L);
        var wheeled=mock(Entity.class,withSettings().extraInterfaces(CarriageAccess.class));
        var scope=CBCRecoil.begin(null);
        try {
            CBCRecoil.launched(new CBCRecoil.Launch(anchored,Vec3.ZERO,new Vec3(8,0,0),25,wheeled));
            verify((CarriageAccess)wheeled).empirecraft$addRecoil(argThat(v->v.distanceTo(new Vec3(-.18,0,0))<1e-9));
            check(!RecoilScope.replacesNative(33L),"Anchored hull does not acquire a suppressed native-force marker");
            verify(anchored,never()).setAttachment(any(),any());
        } finally {CBCRecoil.end(scope);}
        System.out.println("ANCHORED_CARRIAGE_RECOIL_PASS wheel_kick_without_hull_force");
    }
    private static void landRouting() {
        var level=mock(ServerLevel.class);
        var cannon=mock(rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity.class);
        var projectile=mock(rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile.class);
        var tag=new CompoundTag();when(projectile.getPersistentData()).thenReturn(tag);
        when(cannon.getUUID()).thenReturn(UUID.randomUUID());when(cannon.getAnchorVec()).thenReturn(Vec3.ZERO);
        when(projectile.position()).thenReturn(new Vec3(1,2,3));when(projectile.getDeltaMovement()).thenReturn(new Vec3(8,2,0));
        var wheels=mock(Entity.class,withSettings().extraInterfaces(CarriageAccess.class));when(wheels.getUUID()).thenReturn(UUID.randomUUID());
        try(var vs=mockStatic(VSGameUtilsKt.class);var deck=mockStatic(CarriageDeck.class)) {
            var scope=CBCRecoil.begin(cannon);
            try {
                check(CBCRecoil.capture(level,projectile)==null,"Fixed land cannon has no recoil target");
                check(tag.hasUUID(CBCRecoil.SOURCE) && !tag.contains(CBCRecoil.SHIP),"Land source still tagged without a ship");
                when(cannon.getVehicle()).thenReturn(wheels);
                var land=CBCRecoil.capture(level,projectile);
                check(land!=null && land.ship()==null && land.carriage()==wheels,"Land wheels do not require a ship");
                CBCRecoil.launched(land);
                verify((CarriageAccess)wheels).empirecraft$addRecoil(argThat(v->v.x<0 && v.length()<=.1800001));
                check(!RecoilScope.replacesNative(33L),"Land shot does not suppress ship forces");
                var ship=mock(LoadedServerShip.class);when(ship.getId()).thenReturn(33L);
                when(cannon.getVehicle()).thenReturn(null);
                vs.when(()->VSGameUtilsKt.getLoadedShipManagingPos(level,0,0,0)).thenReturn(ship);
                var afloat=CBCRecoil.capture(level,projectile);check(afloat!=null && afloat.ship()==ship,"Mounted ship cannon routes only to its actual ship");
                try(var recoil=mockStatic(CannonRecoil.class,CALLS_REAL_METHODS)) {
                    recoil.when(()->CannonRecoil.fireWorld(eq(ship),any(),any(),anyDouble())).thenReturn(true);
                    CBCRecoil.launched(afloat);check(RecoilScope.replacesNative(33L),"Successful hull recoil has a native replacement marker");
                }
            } finally {CBCRecoil.end(scope);}
        }
        var initial=new Vec3(.1,-.03,.2);var kick=new Vec3(-.18,-.1,0);
        check(CarriageRecoil.landVelocity(initial,kick,true,false).distanceTo(new Vec3(-.08,-.03,.2))<1e-9,"Land recoil preserves gravity and adds opposite horizontal velocity");
        check(CarriageRecoil.landVelocity(initial,kick,false,false).equals(initial),"Airborne carriage does not retain a delayed ground kick");
        check(CarriageRecoil.landVelocity(initial,kick,true,true).equals(initial),"Swimming carriage keeps native water movement");
        check(CarriageRecoil.landVelocity(initial,Vec3.ZERO,true,false).equals(initial),"Kick consumed once, no continuous acceleration");
        System.out.println("LAND_RECOIL_ROUTING_PASS fixed_land,wheeled_land,ship_mount,source_tags,single_horizontal_kick,native_gravity");
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void nativeCarriageMotion() {
        var level=mock(ServerLevel.class,withSettings().extraInterfaces(DimensionIdProvider.class));
        var server=mock(net.minecraft.server.MinecraftServer.class,withSettings().extraInterfaces(org.valkyrienskies.mod.common.IShipObjectWorldServerProvider.class));
        var world=mock(org.valkyrienskies.core.internal.world.VsiServerShipWorld.class);var ships=mock(org.valkyrienskies.core.internal.ships.VsiQueryableShipData.class);
        when(level.getServer()).thenReturn(server);when(((org.valkyrienskies.mod.common.IShipObjectWorldServerProvider)server).getShipObjectWorld()).thenReturn(world);
        when(world.getAllShips()).thenReturn(ships);when(((DimensionIdProvider)level).getDimensionId()).thenReturn("fixture");
        var cache=mock(ServerChunkCache.class);var chunk=mock(LevelChunk.class);when(level.getChunkSource()).thenReturn(cache);
        when(cache.getChunk(anyInt(),anyInt(),eq(ChunkStatus.FULL),eq(false))).thenReturn(chunk);
        int offset=-28672000;
        when(chunk.getBlockState(any())).thenAnswer(a->{BlockPos p=a.getArgument(0);return p.getX()>=offset-32 && p.getX()<=offset+32 && p.getZ()>=-32 && p.getZ()<=32 && p.getY()==63?Blocks.OAK_PLANKS.defaultBlockState():Blocks.AIR.defaultBlockState();});
        var ship=mock(LoadedServerShip.class);var transform=mock(ShipTransform.class);var previous=mock(ShipTransform.class);
        when(ship.getId()).thenReturn(33L);when(ships.getById(33L)).thenReturn(ship);when(ships.getIntersecting(any(org.joml.primitives.AABBdc.class),eq("fixture"))).thenReturn(List.of(ship));
        var matrix=new AtomicReference<>(new Matrix4d().translate(-offset,0,0));var old=new AtomicReference<>(new Matrix4d(matrix.get()));
        var rotation=new AtomicReference<>(new Quaterniond());
        when(ship.getTransform()).thenReturn(transform);when(ship.getPrevTickTransform()).thenReturn(previous);
        when(transform.getShipToWorld()).thenAnswer(a->matrix.get());when(previous.getWorldToShip()).thenAnswer(a->old.get().invert(new Matrix4d()));
        when(transform.getShipToWorldRotation()).thenAnswer(a->rotation.get());
        when(ship.getShipToWorld()).thenAnswer(a->matrix.get());when(ship.getWorldToShip()).thenAnswer(a->matrix.get().invert(new Matrix4d()));
        var entity=mock(Entity.class,withSettings().extraInterfaces(IEntityDraggingInformationProvider.class));var provider=(IEntityDraggingInformationProvider)entity;
        var info=new EntityDraggingInformation();when(provider.getDraggingInformation()).thenReturn(info);when(provider.vs$shouldDrag()).thenReturn(true);
        doReturn(net.minecraft.world.entity.EntityType.MINECART).when(entity).getType();when(entity.level()).thenReturn(level);when(entity.getBbWidth()).thenReturn(1.5f);
        var local=new Vector3d(offset+.5,64,.5);var pos=new AtomicReference<>(new Vec3(.5,64,.5));var yaw=new float[1];
        when(entity.position()).thenAnswer(a->pos.get());when(entity.getX()).thenAnswer(a->pos.get().x);when(entity.getY()).thenAnswer(a->pos.get().y);when(entity.getZ()).thenAnswer(a->pos.get().z);
        when(entity.getBoundingBox()).thenAnswer(a->new AABB(pos.get().x-.75,pos.get().y,pos.get().z-.75,pos.get().x+.75,pos.get().y+1,pos.get().z+.75));
        when(entity.getYRot()).thenAnswer(a->yaw[0]);when(entity.getDeltaMovement()).thenReturn(Vec3.ZERO);
        doAnswer(a->{pos.set(new Vec3(a.getArgument(0),a.getArgument(1),a.getArgument(2)));return null;}).when(entity).setPos(anyDouble(),anyDouble(),anyDouble());
        doAnswer(a->{yaw[0]=a.getArgument(0);return null;}).when(entity).setYRot(anyFloat());
        info.setLastShipStoodOn(33L);info.setShouldImpulseMovement(false);
        for(int tick=1;tick<=120;tick++) {
            old.set(new Matrix4d(matrix.get()));
            rotation.set(new Quaterniond().rotateY(tick*.008).rotateX(Math.cos(tick*.08)*.06).rotateZ(Math.sin(tick*.06)*.2));
            matrix.set(new Matrix4d().translate(Math.sin(tick*.04)*5,0,tick*.12).rotate(rotation.get()).translate(-offset,0,0));
            EntityDragger.INSTANCE.dragEntitiesWithShips(List.of(entity),true);
            var ideal=matrix.get().transformPosition(new Vector3d(local));
            check(pos.get().distanceTo(new Vec3(ideal.x,ideal.y,ideal.z))<1e-7,"Native deck transport drift at tick "+tick);
            var contact=CarriageDeck.support(entity);check(contact!=null && contact.ship().getId()==33L,"Wheel support on rolling moving deck");
            CarriageDeck.refresh(entity,contact,33L);
            // Roll the gun along the deck while the ship accelerates and turns.
            local.x+=.03;var moved=matrix.get().transformPosition(new Vector3d(local));pos.set(new Vec3(moved.x,moved.y,moved.z));
        }
        verify(entity,never()).push(anyDouble(),anyDouble(),anyDouble());
        local.x=offset+40;var outside=matrix.get().transformPosition(new Vector3d(local));pos.set(new Vec3(outside.x,outside.y,outside.z));
        check(CarriageDeck.support(entity)==null,"A carriage can leave the edge of the deck");
        CarriageDeck.refresh(entity,null,33L);check(info.getLastShipStoodOn()==null,"Edge departure detaches the carriage");
        System.out.println("NATIVE_CARRIAGE_MOTION_PASS ticks=120 translation,acceleration,yaw,pitch,roll,driving,edge_departure");
    }
}
