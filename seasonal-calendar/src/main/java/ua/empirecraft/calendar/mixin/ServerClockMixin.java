package ua.empirecraft.calendar.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import sereneseasons.init.ModConfig;
import ua.empirecraft.calendar.WallClockCalendar;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerClockMixin {
    @ModifyArg(method = "m_8809_", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_8615_(J)V"), index = 0, require = 1)
    private long empirecraft$daylight(long vanillaNextTime) {
        var level = (ServerLevel)(Object)this;
        if (!ModConfig.seasons.isDimensionWhitelisted(level.m_46472_())) return vanillaNextTime;
        return WallClockCalendar.sunTime(level, vanillaNextTime);
    }
}
