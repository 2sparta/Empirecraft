package ua.empirecraft.potato;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(PotatoMod.ID)
public final class PotatoMod {
    public static final String ID = "empirecraft_potato";
    public PotatoMod() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ua.empirecraft.potato.client.PotatoClient::register);
    }
}
