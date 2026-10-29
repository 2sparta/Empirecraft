package ua.empirecraft.shipcannons.mixin;

import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.GameToPhysicsAdapter;
import ua.empirecraft.shipcannons.RecoilScope;

@Mixin(value=GameToPhysicsAdapter.class,remap=false)
public abstract class NativeRecoilMixin {
    /** Replace only the native CBC force for this successful shot; never suppress unrelated forces. */
    @Inject(method="applyWorldForceToBodyPos",at=@At("HEAD"),cancellable=true)
    private void empirecraft$oneRecoil(long shipId,Vector3dc force,Vector3dc point,CallbackInfo ci) {
        if(RecoilScope.replacesNative(shipId) && RecoilScope.nativeCbcCall())ci.cancel();
    }
}
