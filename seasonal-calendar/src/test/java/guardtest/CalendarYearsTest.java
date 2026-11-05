package guardtest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import ua.empirecraft.calendar.*;

public final class CalendarYearsTest {
    private static final long EPOCH = 1_800_000_000_000L;
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Calendar assertion " + checks); }
    private static void date(RealTimeline clock, long now, int day, int month, long year) {
        var p = clock.project(now);
        check(CalendarMath.dayOfMonth(p.seasonTicks()) == day);
        check(CalendarMath.monthIndex(p.seasonTicks()) + 1 == month);
        check(p.year() == year);
    }
    private static final class Source {
        final boolean admin;
        RealTimeline clock = RealTimeline.start(EPOCH, 0, 0, false, false);
        int edits;
        Source(boolean admin) { this.admin = admin; }
    }
    private static void rejected(CommandDispatcher<Source> dispatcher, Source source, String command) throws Exception {
        var previous = source.clock; int edits = source.edits;
        try { dispatcher.execute(command, source); throw new AssertionError("Accepted: " + command); }
        catch (CommandSyntaxException expected) { check(source.clock.equals(previous) && source.edits == edits); }
    }
    public static void main(String[] args) throws Exception {
        var clock = RealTimeline.start(EPOCH, 0, 0, false, false);
        date(clock, EPOCH, 1, 3, 1);
        date(clock, EPOCH + 10 * RealTimeline.MONTH_MILLIS, 1, 1, 2);
        date(clock, EPOCH + RealTimeline.YEAR_MILLIS, 1, 3, 2);
        var december = clock.edit(EPOCH, new CalendarEdit(30, 12, 2026L, 23999));
        date(december, EPOCH + 119, 30, 12, 2026);
        date(december, EPOCH + 120, 1, 1, 2027);
        var february = clock.edit(EPOCH, new CalendarEdit(30, 2, 42L, 23999));
        date(february, EPOCH + 120, 1, 3, 42);
        var earlier = december.edit(EPOCH, new CalendarEdit(4, 8, 7L, 6000));
        date(earlier, EPOCH + 2 * RealTimeline.YEAR_MILLIS, 4, 8, 9);
        check(earlier.project(EPOCH + 120).sunTicks() == 6001);
        check(earlier.protocol().startsWith("1|" + EPOCH + "|"));
        var paused = earlier.rebase(EPOCH, true, true, null, null);
        date(paused, EPOCH + 5 * RealTimeline.YEAR_MILLIS, 4, 8, 7);
        var resumed = paused.rebase(EPOCH + 5 * RealTimeline.YEAR_MILLIS, false, false, null, null);
        date(resumed, EPOCH + 6 * RealTimeline.YEAR_MILLIS, 4, 8, 8);
        // Existing /time and /season rebasing must retain the selected year.
        var vanilla = earlier.rebase(EPOCH, false, false, 18000L, CalendarMath.MONTH_TICKS);
        date(vanilla, EPOCH, 1, 4, 7);
        check(vanilla.project(EPOCH + 120).sunTicks() == 18001);
        var observed = earlier.observe(EPOCH + 10000);
        check(observed.edit(EPOCH - 1000, new CalendarEdit(null, null, 3L, null)).epochMillis() == EPOCH + 10000);

        var dispatcher = new CommandDispatcher<Source>();
        CalendarCommands.register(dispatcher, source -> source.admin, c -> 1, (source, edit) -> {
            source.clock = source.clock.edit(EPOCH, edit); source.edits++; return 1;
        });
        var admin = new Source(true); var player = new Source(false);
        check(dispatcher.execute("calendar", player) == 1);
        check(dispatcher.execute("календар", player) == 1);
        dispatcher.execute("calendar set date 30 12 2026 23:59", admin);
        date(admin.clock, EPOCH, 30, 12, 2026);
        dispatcher.execute("календар set day 5", admin);
        dispatcher.execute("calendar set month 2", admin);
        dispatcher.execute("calendar set year 1234", admin);
        dispatcher.execute("календар set time 00:01", admin);
        date(admin.clock, EPOCH, 5, 2, 1234);
        check(Math.floorMod(admin.clock.sunTicks() + 6000, 24000) * 1440 / 24000 == 1);
        dispatcher.execute("calendar set time 6000", admin);
        check(Math.floorMod(admin.clock.sunTicks(), 24000) == 6000);
        for (String command : new String[]{"calendar set day 1", "календар set year 42", "calendar set date 1 1 42 12:00"})
            rejected(dispatcher, player, command);
        for (String command : new String[]{"calendar set day 0", "calendar set day 31", "calendar set month 13",
                "calendar set year 0", "calendar set year -1", "calendar set time 24:00", "calendar set time 12:60",
                "calendar set time -1", "calendar set time 24000", "calendar set time 12:00 extra", "calendar set date 31 1 3 12:00"})
            rejected(dispatcher, admin, command);
        // Every displayed minute round-trips exactly despite Minecraft's fractional ticks per minute.
        var time = new CalendarCommands.TimeArgument();
        for (int minute = 0; minute < 1440; minute++) {
            int ticks = time.parse(new StringReader(String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)));
            check(Math.floorMod(ticks + 6000, 24000) * 1440 / 24000 == minute);
        }
        var packet = new CalendarNetwork.YearSync(new ResourceLocation("minecraft", "overworld"), 1234567890123L);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CalendarNetwork.YearSync.encode(packet, buffer);
            check(CalendarNetwork.YearSync.decode(buffer).equals(packet));
            check(buffer.readableBytes() == 0);
        } finally { buffer.release(); }
        System.out.println("PASS calendar years, real command tree/permissions/validation, manual offset continuity, all clock minutes and native year packet: " + checks);
    }
}
