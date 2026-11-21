package ua.empirecraft.townymap.client.mixin;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.townymap.client.ShipReconClient;
/** Consume only a ship sighting key while scoping; vanilla L stays available everywhere else. */
@Mixin(value=KeyboardHandler.class,remap=false)public abstract class ShipSpyglassKeys {
 @Inject(method="m_90893_(JIIII)V",at=@At("HEAD"),cancellable=true)
 private void key(long window,int key,int scan,int action,int mods,CallbackInfo ci){if(ShipReconClient.key(key,scan,action,mods))ci.cancel();}
}
