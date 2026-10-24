package ua.empirecraft.calendar.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import sereneseasons.init.ModConfig;
import ua.empirecraft.calendar.CalendarMath;

@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel", remap = false)
public abstract class ClientClockMixin {
    @ModifyArg(method = "m_104826_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_104746_(J)V"), index = 0, require = 1)
    private long empirecraft$clientDaylight(long vanillaNextTime) {
        var level = (Level)(Object)this;
        if (!ModConfig.seasons.isDimensionWhitelisted(level.m_46472_())) return vanillaNextTime;
        return vanillaNextTime - 1 + CalendarMath.daylightStep(level.m_46467_());
    }
}
