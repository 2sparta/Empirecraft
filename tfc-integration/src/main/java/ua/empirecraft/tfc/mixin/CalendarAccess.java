package ua.empirecraft.tfc.mixin;
import net.dries007.tfc.util.calendar.Calendar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=Calendar.class,remap=false)
public interface CalendarAccess {
    @Accessor("calendarTicks") void empirecraft$calendarTicks(long ticks);
    @Accessor("daysInMonth") void empirecraft$monthDays(int days);
}
