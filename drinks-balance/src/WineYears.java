package net.satisfy.vinery.core.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.function.LongSupplier;

/** Native Vinery replacement. Epoch timestamps survive chunk unloads and server downtime. */
public class WineYears {
    public static final int YEARS_START=0, MAX_LEVEL=1, START_DURATION=1600;
    public static final int DURATION_PER_YEAR=12800, DAYS_PER_YEAR=30, YEARS_PER_EFFECT_LEVEL=1, MAX_DURATION=14400;
    public static final String TAG_YEAR="Year", TAG_EFFECT_LEVEL="EffectAmplifier", TAG_EFFECT_DURATION="EffectDuration";
    public static final String BOTTLED_AT="EmpirecraftBottledAtMs", AGE="EmpirecraftWineAgeMs";
    public static final long DAY_MS=86_400_000L, MONTH_MS=30L*DAY_MS;
    static LongSupplier clock=System::currentTimeMillis;

    public static int getYear(Level level) { return (int)(clock.getAsLong()/MONTH_MS); }
    public static int getDays(Level level) { return (int)(clock.getAsLong()/DAY_MS); }
    public static int getWineYear(ItemStack stack) { return stack.m_41784_().m_128451_(TAG_YEAR); }
    public static boolean hasWineYear(ItemStack stack) { return stack.m_41784_().m_128425_(BOTTLED_AT,4); }

    /** Never infer real age from the old game-time Year field or restart an existing bottle. */
    public static void setWineYear(ItemStack stack, Level level) {
        if (level==null || level.f_46443_) return;
        CompoundTag tag=stack.m_41784_();
        if (!hasWineYear(stack)) {
            tag.m_128356_(BOTTLED_AT,clock.getAsLong());
            tag.m_128356_(AGE,0L);
            tag.m_128405_(TAG_YEAR,getYear(level));
        }
        refreshCached(stack,level);
    }

    public static long elapsed(long bottledAt,long lastAge,long now) {
        // Bounds also reject corrupt/future timestamps and avoid overflow.
        long delta=now>=bottledAt && bottledAt>=0 ? now-bottledAt : 0;
        return Math.min(MONTH_MS,Math.max(Math.max(0L,lastAge),delta));
    }
    public static int amplifier(long age) { return age>=MONTH_MS ? 1 : 0; }
    public static int duration(long age) {
        long bounded=Math.max(0L,Math.min(MONTH_MS,age));
        return START_DURATION+(int)(bounded*(MAX_DURATION-START_DURATION)/MONTH_MS);
    }
    public static long ageMillis(ItemStack stack, Level level) {
        CompoundTag tag=stack.m_41784_();
        if (level==null || level.f_46443_) return Math.max(0L,Math.min(MONTH_MS,tag.m_128454_(AGE)));
        if (!hasWineYear(stack)) setWineYear(stack,level);
        long age=elapsed(tag.m_128454_(BOTTLED_AT),tag.m_128454_(AGE),clock.getAsLong());
        tag.m_128356_(AGE,age);
        return age;
    }
    public static int getWineAge(ItemStack stack, Level level) { return (int)(ageMillis(stack,level)/MONTH_MS); }
    public static int getWineAgeDays(ItemStack stack, Level level) { return (int)(ageMillis(stack,level)/DAY_MS); }
    public static int getEffectLevel(ItemStack stack, Level level) { return amplifier(ageMillis(stack,level)); }
    public static int getEffectDuration(ItemStack stack, Level level) { return duration(ageMillis(stack,level)); }
    public static void refreshCached(ItemStack stack, Level level) {
        if (level==null || level.f_46443_) return;
        long age=ageMillis(stack,level);
        CompoundTag tag=stack.m_41784_();
        tag.m_128405_(TAG_EFFECT_LEVEL,amplifier(age));
        tag.m_128405_(TAG_EFFECT_DURATION,duration(age));
    }
    public static CompoundTag getShareTag(ItemStack stack) {
        CompoundTag tag=stack.m_41783_();
        return tag==null ? null : tag.m_6426_();
    }
    public static void readShareTag(ItemStack stack, CompoundTag tag) {
        stack.m_41751_(tag==null ? null : tag.m_6426_());
    }
}
