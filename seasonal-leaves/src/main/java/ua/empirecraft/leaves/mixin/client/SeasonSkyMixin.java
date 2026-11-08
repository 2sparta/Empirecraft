package ua.empirecraft.leaves.mixin.client;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.leaves.client.ClientSeasons;

@Mixin(targets="net.minecraft.client.multiplayer.ClientLevel",remap=false)
public abstract class SeasonSkyMixin {
    @Inject(method="m_171660_",at=@At("RETURN"),cancellable=true)
    private void empirecraft$seasonSky(Vec3 camera,float partial,CallbackInfoReturnable<Vec3> ci) {
        Vec3 c=ci.getReturnValue();float[] changed=ClientSeasons.sky((float)c.x,(float)c.y,(float)c.z,0);
        ci.setReturnValue(new Vec3(changed[0],changed[1],changed[2]));
    }
}
