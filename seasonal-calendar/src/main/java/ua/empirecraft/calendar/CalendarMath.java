package ua.empirecraft.calendar;

/** Only daylight is slowed; simulation ticks, machines and animal aging remain at 20 TPS. */
public final class CalendarMath {
    public static final int DAY_TICKS = 57_600;
    public static final int MONTH_DAYS = 30;
    public static final int MONTH_TICKS = DAY_TICKS * MONTH_DAYS;
    public static final int YEAR_TICKS = MONTH_TICKS * 12;
    private CalendarMath() {}
    public static int daylightStep(long gameTime) {
        int phase = (int)Math.floorMod(gameTime, 12L);
        return ((phase + 1) * 5) / 12 - (phase * 5) / 12;
    }
    public static int monthIndex(int seasonTicks) {
        // Serene Seasons starts with March / EARLY_SPRING, whereas the civil year starts in January.
        return (Math.floorMod(seasonTicks, YEAR_TICKS) / MONTH_TICKS + 2) % 12;
    }
    public static int dayOfMonth(int seasonTicks) {
        return Math.floorMod(seasonTicks, MONTH_TICKS) / DAY_TICKS + 1;
    }
    public static int migrateOriginalCycle(int ticks) {
        // The repository's previous calendar was 8 days per sub-season, 24,000 ticks per day.
        return (int)((long)Math.floorMod(ticks, 8 * 24_000 * 12) * YEAR_TICKS / (8 * 24_000 * 12));
    }
}
