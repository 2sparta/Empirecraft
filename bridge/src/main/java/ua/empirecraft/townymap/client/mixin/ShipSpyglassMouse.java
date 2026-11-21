package ua.empirecraft.townymap.client.mixin;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.townymap.client.ShipReconClient;
@Mixin(value=MouseHandler.class,remap=false)public abstract class ShipSpyglassMouse {
 @Inject(method="m_91530_(JIII)V",at=@At("HEAD"),cancellable=true)
 private void key(long window,int button,int action,int mods,CallbackInfo ci){if(ShipReconClient.mouse(button,action,mods))ci.cancel();}
}
