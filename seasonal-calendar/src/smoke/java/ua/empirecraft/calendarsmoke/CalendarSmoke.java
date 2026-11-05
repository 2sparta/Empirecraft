package ua.empirecraft.calendarsmoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import sereneseasons.api.season.SeasonHelper;
import ua.empirecraft.calendar.*;

/** A disposable two-boot contract check. This fixture is never shipped in the calendar. */
@Mod("empirecraft_calendar_smoke")
public final class CalendarSmoke {
    private MinecraftServer server;
    private ServerLevel level;
    private int ticks, step, checks;
    private long since;
    private boolean finished;
    private final int phase = Integer.getInteger("empirecraft.calendar.testPhase", 1);
    public CalendarSmoke() {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> { server = event.getServer(); level = server.m_129783_(); });
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }
    private void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private void command(String value) {
        check(server.m_129892_().m_230957_(server.m_129893_(), value) > 0, "command succeeds: " + value);
    }
    private void date(int day, int month, long year) {
        int cycle = SeasonHelper.getSeasonState(level).getSeasonCycleTicks();
        check(CalendarMath.dayOfMonth(cycle) == day, "day " + day);
        check(CalendarMath.monthIndex(cycle) + 1 == month, "month " + month);
        check(WallClockCalendar.year(level) == year, "year " + year);
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || level == null || finished || ++ticks < 40) return;
        try {
            if (phase == 1) first(); else second();
        } catch (Throwable error) {
            error.printStackTrace(); System.out.println("CALENDAR_NATIVE_FAIL phase=" + phase + " " + error);
            finished = true; server.m_7570_(false);
        }
    }
    private void first() throws Exception {
        if (step == 0) {
            var commands = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.SharedSuggestionProvider>();
            CalendarCommands.register(commands, source -> true, c -> 1, (source, edit) -> 1);
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                new net.minecraft.network.protocol.game.ClientboundCommandsPacket(commands.getRoot()).m_5779_(buffer);
                check(buffer.readableBytes() > 0, "calendar commands serialize for joining clients");
                new net.minecraft.network.protocol.game.ClientboundCommandsPacket(buffer);
                check(buffer.readableBytes() == 0, "calendar command packet deserializes completely");
            } finally { buffer.release(); }
            command("calendar set date 17 8 1420 12:34"); date(17, 8, 1420);
            check(Math.floorMod(level.m_46468_() + 6000, 24000) * 1440 / 24000 == 754, "requested HH:mm is exact");
            command("календар set day 9"); command("calendar set month 2"); command("calendar set year 2026");
            command("calendar set time 00:01"); date(9, 2, 2026);
            var previous = SeasonHelper.getSeasonState(level).getSeasonCycleTicks();
            check(server.m_129892_().m_230957_(server.m_129893_(), "calendar set date 31 1 2000") == 0, "invalid date rejected");
            check(SeasonHelper.getSeasonState(level).getSeasonCycleTicks() == previous, "invalid command leaves date intact");
            command("calendar set date 30 12 2026 23999"); since = System.currentTimeMillis(); step = 1;
        } else if (step == 1 && System.currentTimeMillis() - since >= 600) {
            date(1, 1, 2027);
            check(Component.Serializer.m_130703_(CalendarMod.date(level)).contains("2027"), "display component contains the synchronized year");
            command("time set 6000"); since = System.currentTimeMillis(); step = 2;
        } else if (step == 2 && System.currentTimeMillis() - since >= 1200) {
            date(1, 1, 2027);
            check(level.m_46468_() >= 6000 && level.m_46468_() < 6100, "native /time set continues at wall-clock rate");
            command("календар set date 9 2 1420 00:01");
            var marker = new Properties(); marker.setProperty("at", Long.toString(System.currentTimeMillis()));
            marker.setProperty("sun", Long.toString(level.m_46468_()));
            try (var out = Files.newOutputStream(Path.of("calendar-restart.properties"))) { marker.store(out, "Disposable calendar test"); }
            finish();
        }
    }
    private void second() throws Exception {
        if (step == 0) {
            date(9, 2, 1420);
            var marker = new Properties();
            try (var input = Files.newInputStream(Path.of("calendar-restart.properties"))) { marker.load(input); }
            long elapsed = System.currentTimeMillis() - Long.parseLong(marker.getProperty("at"));
            long expected = Long.parseLong(marker.getProperty("sun")) + elapsed / 120;
            check(Math.abs(level.m_46468_() - expected) <= 3, "restart includes real downtime without resetting the manual time");
            var data = level.m_8895_().m_164861_(CalendarData::load, CalendarData::new, "empirecraft_calendar");
            check(data.m_7176_(new CompoundTag()).m_128451_("wall_clock_schema") == 2, "persistent year schema is active");
            check(data.wallClock.year() == 1420, "manually selected year is persisted");
            command("calendar set year 1776"); since = System.currentTimeMillis(); step = 1;
        } else if (step == 1 && System.currentTimeMillis() - since >= 1200) {
            date(9, 2, 1776);
            check(System.getProperty(RealTimeline.PROPERTY).startsWith("1|"), "provision calendar protocol stays compatible");
            finish();
        }
    }
    private void finish() {
        System.out.println("CALENDAR_NATIVE_PASS phase=" + phase + " assertions=" + checks);
        finished = true; server.m_7570_(false);
    }
}
