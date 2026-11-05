package ua.empirecraft.calendar;

/** UTC milliseconds are authoritative. Projection is independent of TPS and JVM lifetime. */
public record RealTimeline(long epochMillis, long seasonMillis, long sunTicks, long sunRemainder,
                           boolean seasonPaused, boolean sunPaused, long observedMillis, long year) {
    public static final long DAY_MILLIS = 2_880_000L;
    public static final long MONTH_MILLIS = DAY_MILLIS * 30;
    public static final long YEAR_MILLIS = MONTH_MILLIS * 12;
    public static final String PROPERTY = "empirecraft.calendar.wallclock.v1";
    public RealTimeline {
        if (epochMillis <= 0 || observedMillis < epochMillis || sunRemainder < 0 || sunRemainder >= 120
                || seasonMillis < 0 || seasonMillis >= YEAR_MILLIS || year < 1) throw new IllegalArgumentException("Invalid real calendar anchor");
    }
    /** Version 1 anchors did not store a year. Keep their date and start their year count at 1. */
    public RealTimeline(long epochMillis, long seasonMillis, long sunTicks, long sunRemainder,
                        boolean seasonPaused, boolean sunPaused, long observedMillis) {
        this(epochMillis, seasonMillis, sunTicks, sunRemainder, seasonPaused, sunPaused, observedMillis, 1);
    }
    public record Projection(long seasonMillis, int seasonTicks, long sunTicks, long sunRemainder, long now, long year) {}
    public static RealTimeline start(long reference, int oldSeasonTicks, long oldSunTicks, boolean seasonPaused, boolean sunPaused) {
        long day = Math.floorMod(oldSeasonTicks, CalendarMath.YEAR_TICKS) / CalendarMath.DAY_TICKS;
        long phase = Math.floorMod(oldSunTicks, 24_000L) * 120;
        return new RealTimeline(reference, day * DAY_MILLIS + phase, oldSunTicks, 0, seasonPaused, sunPaused, reference);
    }
    public Projection project(long now) {
        long effective = Math.max(now, Math.max(epochMillis, observedMillis));
        long elapsed = effective - epochMillis;
        long seasonElapsed = seasonPaused ? 0 : elapsed;
        long season = (seasonMillis + seasonElapsed % YEAR_MILLIS) % YEAR_MILLIS;
        // Serene Seasons wraps in March; the displayed year advances from December to January.
        long civilPhase = (seasonMillis + 2 * MONTH_MILLIS) % YEAR_MILLIS;
        long years = seasonElapsed / YEAR_MILLIS + (civilPhase + seasonElapsed % YEAR_MILLIS) / YEAR_MILLIS;
        long solarElapsed = sunPaused ? 0 : elapsed;
        long fraction = sunRemainder + solarElapsed % 120;
        return new Projection(season, (int)(season / 50), Math.addExact(sunTicks, solarElapsed / 120 + fraction / 120),
                fraction % 120, effective, Math.addExact(year, years));
    }
    public RealTimeline observe(long now) {
        return new RealTimeline(epochMillis, seasonMillis, sunTicks, sunRemainder, seasonPaused, sunPaused,
                Math.max(now, observedMillis), year);
    }
    public RealTimeline rebase(long now, boolean pauseSeason, boolean pauseSun, Long requestedSun, Integer requestedSeason) {
        var current = project(now);
        long sun = requestedSun == null ? current.sunTicks : requestedSun;
        long remainder = requestedSun == null ? current.sunRemainder : 0;
        long season = current.seasonMillis;
        if (requestedSeason != null || requestedSun != null) {
            long day = requestedSeason == null ? season / DAY_MILLIS
                    : Math.floorMod(requestedSeason, CalendarMath.YEAR_TICKS) / CalendarMath.DAY_TICKS;
            season = day * DAY_MILLIS + Math.floorMod(sun, 24_000L) * 120 + remainder;
        }
        return new RealTimeline(current.now, season, sun, remainder, pauseSeason, pauseSun, current.now, current.year);
    }
    /** An administrator changes the anchor, so subsequent real elapsed time starts from the new date. */
    public RealTimeline edit(long now, CalendarEdit edit) {
        var current = project(now);
        int month = edit.month() == null ? CalendarMath.monthIndex(current.seasonTicks) + 1 : edit.month();
        int day = edit.day() == null ? CalendarMath.dayOfMonth(current.seasonTicks) : edit.day();
        long nextYear = edit.year() == null ? current.year : edit.year();
        long sun = current.sunTicks, remainder = current.sunRemainder;
        long phase = current.seasonMillis % DAY_MILLIS;
        if (edit.timeOfDay() != null) {
            sun = Math.addExact(Math.multiplyExact(Math.floorDiv(sun, 24_000L), 24_000L), edit.timeOfDay());
            remainder = 0;
            phase = edit.timeOfDay() * 120L;
        }
        long season = Math.floorMod(month - 3, 12) * MONTH_MILLIS + (day - 1) * DAY_MILLIS + phase;
        return new RealTimeline(current.now, season, sun, remainder, seasonPaused, sunPaused, current.now, nextYear);
    }
    /** JDK-only service contract for the Bukkit plugin; no Forge/Bukkit class-loader crossing. */
    public String protocol() {
        return "1|" + epochMillis + "|" + seasonMillis + "|" + seasonPaused + "|" + observedMillis;
    }
}
