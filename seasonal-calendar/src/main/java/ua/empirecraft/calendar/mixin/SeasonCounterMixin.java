package ua.empirecraft.calendar.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "sereneseasons.season.SeasonHandler", remap = false)
public abstract class SeasonCounterMixin {
    // 9.1.0.3 normally follows daylight deltas. Count simulation ticks so a 57,600-tick day
    // stays in sync with the native client counter and /time commands cannot skip months.
    @Redirect(method = "onLevelTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_46468_()J"), require = 1)
    private static long empirecraft$calendarTime(Level level) { return level.m_46467_(); }
}
