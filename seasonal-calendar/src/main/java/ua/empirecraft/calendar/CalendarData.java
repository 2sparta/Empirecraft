package ua.empirecraft.calendar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class CalendarData extends SavedData {
    public boolean migrated;
    public RealTimeline wallClock;
    public boolean invalidClock;
    public static CalendarData load(CompoundTag tag) {
        var data = new CalendarData();
        data.migrated = tag.m_128471_("calendar_1");
        if (tag.m_128451_("wall_clock_schema") == 1) {
            try {
                data.wallClock = new RealTimeline(tag.m_128454_("wall_epoch"), tag.m_128454_("wall_season"),
                    tag.m_128454_("wall_sun"), tag.m_128454_("wall_sun_remainder"),
                    tag.m_128471_("wall_pause_season"), tag.m_128471_("wall_pause_sun"), tag.m_128454_("wall_observed"));
            } catch (IllegalArgumentException error) { data.invalidClock = true; }
        }
        if(tag.m_128441_("wall_clock_schema") && tag.m_128451_("wall_clock_schema")!=1)data.invalidClock=true;
        return data;
    }
    @Override public CompoundTag m_7176_(CompoundTag tag) {
        tag.m_128379_("calendar_1", migrated);
        if (wallClock != null) {
            tag.m_128405_("wall_clock_schema", 1);
            tag.m_128356_("wall_epoch", wallClock.epochMillis());
            tag.m_128356_("wall_season", wallClock.seasonMillis());
            tag.m_128356_("wall_sun", wallClock.sunTicks());
            tag.m_128356_("wall_sun_remainder", wallClock.sunRemainder());
            tag.m_128379_("wall_pause_season", wallClock.seasonPaused());
            tag.m_128379_("wall_pause_sun", wallClock.sunPaused());
            tag.m_128356_("wall_observed", wallClock.observedMillis());
        }
        return tag;
    }
}
