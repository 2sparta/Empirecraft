package ua.empirecraft.launcherclient.mixin;
import ua.empirecraft.launcherclient.LauncherClient;
import ua.empirecraft.launcherclient.EmpireHome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public final class ConnectionGuard {
    // Exact 1.20.1 production SRG entry point; required mixin fails closed on mismatch.
    @Inject(method="m_278792_", at=@At("HEAD"), cancellable=true, remap=false)
    private static void empirecraft$onlyServer(Screen parent, Minecraft mc, ServerAddress address, ServerData data, boolean quick, CallbackInfo ci) {
        if (!LauncherClient.connectionRequested() || !LauncherClient.allowed(address.getHost(), address.getPort()) || !LauncherClient.SERVER.equalsIgnoreCase(data.ip)) {
            ci.cancel();
            mc.setScreen(new EmpireHome());
        }
    }
}
