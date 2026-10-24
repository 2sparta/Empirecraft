package ua.empirecraft.calendar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class CalendarData extends SavedData {
    public boolean migrated;
    public static CalendarData load(CompoundTag tag) {
        var data = new CalendarData();
        data.migrated = tag.m_128471_("calendar_1");
        return data;
    }
    @Override public CompoundTag m_7176_(CompoundTag tag) {
        tag.m_128379_("calendar_1", migrated);
        return tag;
    }
}
