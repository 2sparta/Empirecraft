package ua.empirecraft.calendar;

/** UTC milliseconds are authoritative. Projection is independent of TPS and JVM lifetime. */
public record RealTimeline(long epochMillis, long seasonMillis, long sunTicks, long sunRemainder,
                           boolean seasonPaused, boolean sunPaused, long observedMillis) {
    public static final long DAY_MILLIS = 2_880_000L;
    public static final long MONTH_MILLIS = DAY_MILLIS * 30;
    public static final long YEAR_MILLIS = MONTH_MILLIS * 12;
    public static final String PROPERTY = "empirecraft.calendar.wallclock.v1";
    public RealTimeline {
        if (epochMillis <= 0 || observedMillis < epochMillis || sunRemainder < 0 || sunRemainder >= 120
                || seasonMillis < 0 || seasonMillis >= YEAR_MILLIS) throw new IllegalArgumentException("Invalid real calendar anchor");
    }
    public record Projection(long seasonMillis, int seasonTicks, long sunTicks, long sunRemainder, long now) {}
    public static RealTimeline start(long reference, int oldSeasonTicks, long oldSunTicks, boolean seasonPaused, boolean sunPaused) {
        long day = Math.floorMod(oldSeasonTicks, CalendarMath.YEAR_TICKS) / CalendarMath.DAY_TICKS;
        long phase = Math.floorMod(oldSunTicks, 24_000L) * 120;
        return new RealTimeline(reference, day * DAY_MILLIS + phase, oldSunTicks, 0, seasonPaused, sunPaused, reference);
    }
    public Projection project(long now) {
        long effective = Math.max(now, Math.max(epochMillis, observedMillis));
        long elapsed = effective - epochMillis;
        long season = Math.floorMod(seasonMillis + (seasonPaused ? 0 : elapsed), YEAR_MILLIS);
        long solarElapsed = sunRemainder + (sunPaused ? 0 : elapsed);
        return new Projection(season, (int)(season / 50), Math.addExact(sunTicks, solarElapsed / 120), solarElapsed % 120, effective);
    }
    public RealTimeline observe(long now) {
        return new RealTimeline(epochMillis, seasonMillis, sunTicks, sunRemainder, seasonPaused, sunPaused,
                Math.max(now, observedMillis));
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
        return new RealTimeline(current.now, season, sun, remainder, pauseSeason, pauseSun, current.now);
    }
    /** JDK-only service contract for the Bukkit plugin; no Forge/Bukkit class-loader crossing. */
    public String protocol() {
        return "1|" + epochMillis + "|" + seasonMillis + "|" + seasonPaused + "|" + observedMillis;
    }
}
