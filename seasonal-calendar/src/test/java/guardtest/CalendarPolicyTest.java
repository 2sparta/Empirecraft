package guardtest;

import ua.empirecraft.calendar.CalendarMath;
import ua.empirecraft.calendar.FaunaRules;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;

public final class CalendarPolicyTest {
    public static void main(String[] args) {
        try {
            var dispatcher = new com.mojang.brigadier.CommandDispatcher<Object>();
            var node = dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("calendar").executes(context -> 7));
            dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("календар").executes(node.getCommand()));
            assert dispatcher.execute("calendar", new Object()) == 7;
            assert dispatcher.execute("календар", new Object()) == 7;
        } catch (Exception error) { throw new AssertionError("Calendar aliases", error); }
        for (long offset : new long[] {0, 1, 11, 12, 57_599, 987_654_321, -100}) {
            long sun = 0;
            for (long tick = offset; tick < offset + CalendarMath.DAY_TICKS; tick++) sun += CalendarMath.daylightStep(tick);
            assert sun == 24_000 : "Wrong solar day at " + offset;
        }
        long sun = 0;
        for (int tick = 0; tick < CalendarMath.MONTH_TICKS; tick++) sun += CalendarMath.daylightStep(tick);
        assert sun == 24_000L * 30;
        assert CalendarMath.MONTH_TICKS / 20 == 86_400;
        assert CalendarMath.YEAR_TICKS / 20 == 12 * 86_400;
        assert CalendarMath.dayOfMonth(0) == 1;
        assert CalendarMath.dayOfMonth(57_599) == 1;
        assert CalendarMath.dayOfMonth(57_600) == 2;
        assert CalendarMath.dayOfMonth(CalendarMath.MONTH_TICKS - 1) == 30;
        assert CalendarMath.dayOfMonth(CalendarMath.MONTH_TICKS) == 1;
        assert CalendarMath.monthIndex(0) == 2; // March is EARLY_SPRING.
        assert CalendarMath.monthIndex(CalendarMath.MONTH_TICKS * 9) == 11;
        assert CalendarMath.monthIndex(CalendarMath.MONTH_TICKS * 10) == 0;
        assert CalendarMath.monthIndex(CalendarMath.MONTH_TICKS * 11) == 1;
        assert CalendarMath.monthIndex(CalendarMath.YEAR_TICKS) == 2;
        for (int month = 0; month < 12; month++) {
            int old = month * 8 * 24_000 + 4 * 24_000;
            int migrated = CalendarMath.migrateOriginalCycle(old);
            assert migrated / CalendarMath.MONTH_TICKS == month;
            assert CalendarMath.dayOfMonth(migrated) == 16;
        }
        var sleep = new SleepFinishedTimeEvent(null, 120_000, 108_000);
        assert !sleep.setTimeAddition(0) : "Forge does not accept a time delta here";
        assert sleep.setTimeAddition(108_000);
        assert sleep.getNewTime() == 108_000 : "Sleep must not advance the day";
        for (int month = 0; month < 12; month++) {
            double bear = FaunaRules.factor("naturalist:bear", "CREATURE", month);
            assert (month == 1 || month == 2) ? bear == 3 : bear < 3;
            assert FaunaRules.factor("minecraft:zombie", "MONSTER", month) == 1;
            assert FaunaRules.factor("minecraft:cow", "CREATURE", month) > 0;
            assert FaunaRules.factor("future:animal", "CREATURE", month) > 0;
        }
        assert FaunaRules.factor("naturalist:butterfly", "CREATURE", 0) == 0;
        assert FaunaRules.factor("naturalist:butterfly", "CREATURE", 5) > 1;
        assert FaunaRules.factor("naturalist:cardinal", "CREATURE", 0) > FaunaRules.factor("naturalist:cardinal", "CREATURE", 5);
        System.out.println("PASS exact 48-minute day, 24-hour month, month/year boundaries, migration, native Forge sleep API and fauna profiles");
    }
}
