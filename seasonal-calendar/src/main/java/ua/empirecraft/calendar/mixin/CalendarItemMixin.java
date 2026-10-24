package ua.empirecraft.calendar.mixin;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sereneseasons.init.ModConfig;
import ua.empirecraft.calendar.CalendarMod;

@Mixin(targets = "sereneseasons.item.CalendarItem", remap = false)
public abstract class CalendarItemMixin {
    @Inject(method = "m_7373_", at = @At("HEAD"), cancellable = true, require = 1)
    private void empirecraft$calendar(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        if (level == null || !ModConfig.seasons.isDimensionWhitelisted(level.m_46472_())) return;
        tooltip.add(CalendarMod.date(level));
        tooltip.add(Component.m_237115_("calendar.empirecraft.duration"));
        ci.cancel();
    }
}
