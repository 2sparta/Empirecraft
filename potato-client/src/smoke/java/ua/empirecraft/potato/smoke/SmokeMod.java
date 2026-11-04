package ua.empirecraft.potato.smoke;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;

@Mod("empirecraft_potato_smoke")
public final class SmokeMod {
    public SmokeMod() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientProbe::register);
        DistExecutor.safeRunWhenOn(Dist.DEDICATED_SERVER, () -> ServerProbe::register);
    }
}
