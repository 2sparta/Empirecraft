package guardtest;

import ua.empirecraft.calendar.CalendarMath;
import ua.empirecraft.calendar.RealTimeline;
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
        long epoch=1_800_000_000_000L;
        var clock=RealTimeline.start(epoch,0,0,false,false);
        assert clock.project(epoch+2_880_000).sunTicks()==24_000;
        assert clock.project(epoch+86_400_000).seasonTicks()==CalendarMath.MONTH_TICKS;
        assert CalendarMath.monthIndex(clock.project(epoch+86_400_000).seasonTicks())==3;
        assert clock.project(epoch+RealTimeline.YEAR_MILLIS).seasonTicks()==0;
        // A restart loads the same anchor, and 48 minutes still pass with no simulation ticks at all.
        var restarted=new RealTimeline(clock.epochMillis(),clock.seasonMillis(),clock.sunTicks(),clock.sunRemainder(),false,false,epoch);
        assert restarted.project(epoch+4*86_400_000L).equals(clock.project(epoch+4*86_400_000L));
        var slow=clock;
        for(long time=epoch;time<epoch+2_880_000;time+=137)slow=slow.observe(time);
        assert slow.project(epoch+2_880_000).sunTicks()==24_000;
        assert slow.project(epoch-50_000).now()==slow.observedMillis();
        var paused=clock.rebase(epoch+2_880_000,true,true,null,null);
        assert paused.project(epoch+86_400_000).sunTicks()==24_000;
        assert paused.project(epoch+86_400_000).seasonTicks()==CalendarMath.DAY_TICKS;
        var resumed=paused.rebase(epoch+86_400_000,false,false,null,null);
        assert resumed.project(epoch+86_400_000+2_880_000).sunTicks()==48_000;
        var commanded=clock.rebase(epoch+86_400_000,false,false,6_000L,null);
        assert commanded.project(epoch+86_400_000).seasonTicks()/CalendarMath.DAY_TICKS==30;
        assert commanded.project(epoch+86_400_000).sunTicks()==6_000;
        var fractions=clock.rebase(epoch+53,false,false,null,null);
        assert fractions.project(epoch+120).sunTicks()==1;
        assert clock.protocol().startsWith("1|"+epoch+"|0|false|");
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
        System.out.println("PASS wall-clock downtime/restart/TPS/pauses/admin commands/fractions, exact 48-minute day, 24-hour month, month/year boundaries, migration, native Forge sleep API and fauna profiles");
    }
}
