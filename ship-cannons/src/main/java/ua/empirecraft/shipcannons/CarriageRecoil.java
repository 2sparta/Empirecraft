package ua.empirecraft.shipcannons;
import net.minecraft.world.phys.Vec3;
/** Land wheels receive one horizontal kick, then return to CBC's native movement. */
public final class CarriageRecoil {
    private CarriageRecoil() {}
    public static Vec3 landVelocity(Vec3 velocity,Vec3 kick,boolean grounded,boolean inWater) {
        return grounded && !inWater ? velocity.add(kick.x,0,kick.z) : velocity;
    }
}
