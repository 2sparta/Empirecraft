package ua.empirecraft.calendar;

/** Null fields preserve the current date/time; validation happens before changing any world state. */
public record CalendarEdit(Integer day, Integer month, Long year, Integer timeOfDay) {
    public CalendarEdit {
        if (day != null && (day < 1 || day > 30)) throw new IllegalArgumentException("Day must be 1-30");
        if (month != null && (month < 1 || month > 12)) throw new IllegalArgumentException("Month must be 1-12");
        if (year != null && year < 1) throw new IllegalArgumentException("Year must be positive");
        if (timeOfDay != null && (timeOfDay < 0 || timeOfDay >= 24_000)) throw new IllegalArgumentException("Time must be 0-23999 ticks");
    }
}
