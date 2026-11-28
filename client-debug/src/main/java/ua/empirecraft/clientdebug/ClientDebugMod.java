package ua.empirecraft.clientdebug;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(ClientDebugMod.ID)
public final class ClientDebugMod {
    static final String ID = "empirecraft_client_debug";

    public ClientDebugMod() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::register);
    }
}
