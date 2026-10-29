package ua.empirecraft.shipcannons;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
public final class CarriageDeck {
    private CarriageDeck() {}
    /** A stale last-ship ID alone must not attach an airborne gun. */
    public static ShipRaycast.Hit support(Entity carriage) {
        if(!CannonRecoil.carryEnabled() || carriage.isPassenger() || carriage.isInWater())return null;
        double w=Math.min(.65,carriage.getBbWidth()*.35);
        double[][] feet={{0,0},{w,w},{w,-w},{-w,w},{-w,-w}};
        for(double[] foot:feet) {
            Vec3 p=carriage.position().add(foot[0],.15,foot[1]);
            var hit=ShipRaycast.first(carriage.level(),carriage,p,p.add(0,-.5,0),false,state->false);
            if(hit!=null && hit.state()!=null && hit.normal().y>.35)return hit;
        }
        return null;
    }
    public static void refresh(Entity carriage,ShipRaycast.Hit contact,Long previousShip) {
        if(!(carriage instanceof IEntityDraggingInformationProvider provider))return;
        var drag=provider.getDraggingInformation();
        if(contact!=null) {
            drag.setLastShipStoodOn(contact.ship().getId());drag.setShouldImpulseMovement(false);
        } else if(previousShip!=null && previousShip.equals(drag.getLastShipStoodOn()))drag.setLastShipStoodOn(null);
    }
}
