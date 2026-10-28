package ua.empirecraft.shipcannons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.grapeshot.GrapeshotBagProjectile;
public final class CBCRecoil {
    public static final String SOURCE="EmpirecraftGrapeshotSource",VEHICLE="EmpirecraftGrapeshotVehicle",SHIP="EmpirecraftGrapeshotShip";
    public static final class Shot {
        private final Shot parent;
        private final PitchOrientedContraptionEntity cannon;
        private final RecoilScope.Scope scope=RecoilScope.begin();
        private Shot(Shot parent,PitchOrientedContraptionEntity cannon) {this.parent=parent;this.cannon=cannon;}
    }
    private static final ThreadLocal<Shot> CURRENT=new ThreadLocal<>();
    private CBCRecoil() {}
    public static Shot begin(PitchOrientedContraptionEntity cannon) {Shot shot=new Shot(CURRENT.get(),cannon);CURRENT.set(shot);return shot;}
    public static void end(Shot shot) {RecoilScope.end(shot.scope);if(shot.parent==null)CURRENT.remove();else CURRENT.set(shot.parent);}
    public record Launch(LoadedServerShip ship,Vec3 point,Vec3 velocity,double mass,Entity carriage) {}
    public static Launch capture(ServerLevel level,Entity projectile) {
        Shot shot=CURRENT.get();if(shot==null || !(projectile instanceof AbstractCannonProjectile))return null;
        var cannon=shot.cannon;var vehicle=cannon.getVehicle();
        projectile.getPersistentData().putUUID(SOURCE,cannon.getUUID());
        if(vehicle!=null)projectile.getPersistentData().putUUID(VEHICLE,vehicle.getUUID());
        LoadedServerShip ship=VSGameUtilsKt.getLoadedShipManagingPos(level,cannon.getAnchorVec().x,cannon.getAnchorVec().y,cannon.getAnchorVec().z);
        if(ship==null && vehicle!=null) {
            var contact=CarriageDeck.support(vehicle);
            if(contact!=null && contact.ship() instanceof LoadedServerShip loaded)ship=loaded;
        }
        if(ship==null)return null;
        projectile.getPersistentData().putLong(SHIP,ship.getId());
        if(!CannonRecoil.cbcEnabled())return null;
        Vec3 point=projectile.position(),velocity=projectile.getDeltaMovement();
        if(VSGameUtilsKt.getLoadedShipManagingPos(level,point.x,point.y,point.z)!=null) {point=ShipRaycast.worldPoint(ship,point);velocity=ShipRaycast.worldDirection(ship,velocity);}
        return new Launch(ship,point,velocity,CannonRecoil.cbcMass(projectile instanceof AbstractAutocannonProjectile,projectile instanceof GrapeshotBagProjectile),vehicle);
    }
    public static void launched(Launch launch) {
        Shot shot=CURRENT.get();if(launch==null || shot==null)return;
        if(CannonRecoil.fireWorld(launch.ship,launch.point,launch.velocity,launch.mass)) {
            RecoilScope.handled(shot.scope,launch.ship.getId());
            if(launch.carriage instanceof CarriageAccess access && launch.velocity.lengthSqr()>1e-9)
                access.empirecraft$addRecoil(launch.velocity.normalize().scale(-Math.min(.18,CannonRecoil.impulse(launch.mass,launch.velocity.length())/40000)));
        }
    }
}
