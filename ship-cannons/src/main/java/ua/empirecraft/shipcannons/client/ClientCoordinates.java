package ua.empirecraft.shipcannons.client;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
public final class ClientCoordinates {
    private ClientCoordinates() {}
    public static Vec3 world(Level level,Vec3 pos) {
        var ship=VSGameUtilsKt.getShipManagingPos(level,pos.x,pos.y,pos.z);
        if(ship instanceof ClientShip client) {
            var p=client.getRenderTransform().getShipToWorld().transformPosition(new Vector3d(pos.x,pos.y,pos.z));
            return new Vec3(p.x,p.y,p.z);
        }
        return pos;
    }
    public static Vec3 center(Level level,BlockPos pos){return world(level,pos.getCenter());}
}
