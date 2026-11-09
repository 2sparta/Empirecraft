package ua.empirecraft.calendar;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.CommandEvent;
import sereneseasons.api.SSGameRules;
import sereneseasons.init.ModConfig;
import sereneseasons.season.SeasonHandler;

public final class WallClockCalendar {
    private static final Map<ServerLevel, Entry> CLOCKS = new IdentityHashMap<>();
    private static ServerLevel primary;
    private static final java.util.Set<String> PUBLISHED = new java.util.HashSet<>();
    private static long lastPlayed;
    private static final class Entry {
        final CalendarData data;
        boolean commandSun, commandSeason;
        long lastSync;
        String worldKey, worldNameKey;
        Entry(CalendarData data) { this.data = data; }
    }
    private WallClockCalendar() {}
    public static void beforeStart(MinecraftServer server) {
        CLOCKS.clear(); primary = null; lastPlayed = 0;
        clearPublished();
        try {
            var file = server.m_129843_(LevelResource.f_78178_).toFile();
            if (file.isFile()) lastPlayed = NbtIo.m_128937_(file).m_128469_("Data").m_128454_("LastPlayed");
        } catch (Exception error) { System.getLogger("EmpirecraftCalendar").log(System.Logger.Level.WARNING, "Cannot read the previous LastPlayed timestamp", error); }
    }
    private static boolean pauseSeason(ServerLevel level) {
        return !level.m_46469_().m_46207_(SSGameRules.RULE_DOSEASONCYCLE)
            || !ModConfig.seasons.progressSeasonWhileOffline && level.m_7654_().m_6846_().m_11309_() == 0;
    }
    private static boolean pauseSun(ServerLevel level) { return !level.m_46469_().m_46207_(GameRules.f_46140_); }
    public static void initialize(ServerLevel level, CalendarData data, boolean hadOldCalendar) {
        if (data.invalidClock) throw new IllegalStateException("Corrupt Empirecraft wall-clock anchor; restore its world data before resuming the calendar");
        long now = System.currentTimeMillis();
        if (data.wallClock == null) {
            // Only an already-installed 1.0 calendar has a trustworthy old real-time reference.
            long reference = hadOldCalendar && lastPlayed > 0 ? lastPlayed : now;
            data.wallClock = RealTimeline.start(reference, SeasonHandler.getSeasonSavedData(level).seasonCycleTicks,
                    level.m_46468_(), pauseSeason(level), pauseSun(level));
        }
        var entry = new Entry(data);
        // The Bukkit and Forge worlds share a UUID on Mohist. Keep the wire value JDK-only.
        try {
            Object world = level.getClass().getMethod("getWorld").invoke(level);
            Object uuid = Class.forName("org.bukkit.World").getMethod("getUID").invoke(world);
            entry.worldKey = RealTimeline.PROPERTY + ".world." + uuid;
            entry.worldNameKey = RealTimeline.PROPERTY + ".world." + Class.forName("org.bukkit.World").getMethod("getName").invoke(world);
        } catch (ReflectiveOperationException absent) { /* Dedicated Forge has no Bukkit consumer. */ }
        CLOCKS.put(level, entry);
        if (primary == null || level.m_46472_().equals(Level.f_46428_)) primary = level;
        tick(level, now, true);
        level.m_8895_().m_78151_(); // Persist the epoch before the first game tick, including an unclean restart.
    }
    public static void tick(ServerLevel level, long now, boolean forceSync) {
        var entry = CLOCKS.get(level);
        if (entry == null) return;
        var clock = entry.data.wallClock;
        boolean seasonPaused = pauseSeason(level), sunPaused = pauseSun(level);
        if (clock.seasonPaused() != seasonPaused || clock.sunPaused() != sunPaused || entry.commandSun || entry.commandSeason) {
            clock = clock.rebase(now, seasonPaused, sunPaused, entry.commandSun ? level.m_46468_() : null,
                entry.commandSeason ? SeasonHandler.getSeasonSavedData(level).seasonCycleTicks : null);
            entry.commandSun = false; entry.commandSeason = false;
            forceSync = true;
        }
        clock = clock.observe(now);
        entry.data.wallClock = clock; entry.data.m_77762_();
        var projected = clock.project(now);
        var season = SeasonHandler.getSeasonSavedData(level);
        season.seasonCycleTicks = projected.seasonTicks(); season.m_77762_();
        level.m_8615_(projected.sunTicks());
        if (forceSync || projected.now() - entry.lastSync >= 1000) {
            SeasonHandler.sendSeasonUpdate(level);
            // Vanilla sends solar time every 20 simulation ticks; keep it timely even at low TPS.
            var timePacket=new ClientboundSetTimePacket(level.m_46467_(),projected.sunTicks(),!sunPaused);
            for(var player:level.m_6907_())player.f_8906_.m_9829_(timePacket);
            entry.lastSync = projected.now();
            CalendarNetwork.sync(level, projected.year());
            publish(RealTimeline.PROPERTY + ".dimension." + level.m_46472_().m_135782_(), clock.protocol());
            if (entry.worldKey != null) publish(entry.worldKey, clock.protocol());
            if (entry.worldNameKey != null) publish(entry.worldNameKey, clock.protocol());
            if (level == primary) publish(RealTimeline.PROPERTY, clock.protocol());
        }
        if (forceSync) level.m_8895_().m_78151_();
    }
    public static long sunTime(ServerLevel level, long fallback) {
        var entry = CLOCKS.get(level);
        return entry == null || entry.commandSun ? fallback : entry.data.wallClock.project(System.currentTimeMillis()).sunTicks();
    }
    public static ServerLevel calendarLevel(ServerLevel level) {
        return CLOCKS.containsKey(level) ? level : primary == null ? level : primary;
    }
    public static long year(ServerLevel level) {
        var current = current(level);
        return current == null ? 1 : current.year();
    }
    public static RealTimeline.Projection current(ServerLevel level) {
        var entry = CLOCKS.get(calendarLevel(level));
        return entry == null ? null : entry.data.wallClock.project(System.currentTimeMillis());
    }
    public static void edit(ServerLevel sourceLevel, CalendarEdit edit) {
        var level = calendarLevel(sourceLevel);
        var entry = CLOCKS.get(level);
        if (entry == null) throw new IllegalStateException("Calendar is not initialized");
        long now = System.currentTimeMillis();
        // First accept any preceding /time or /season command in the same server tick.
        tick(level, now, false);
        entry.data.wallClock = entry.data.wallClock.edit(now, edit);
        tick(level, now, true);
    }
    public static void command(CommandEvent event) {
        var nodes = event.getParseResults().getContext().getNodes();
        if (nodes.size() < 2) return;
        String root = nodes.get(0).getNode().getName(), action = nodes.get(1).getNode().getName();
        boolean time = (root.equals("time") || root.equals("minecraft:time")) && (action.equals("set") || action.equals("add"));
        boolean season = (root.equals("season") || root.equals("sereneseasons:season")) && action.equals("set");
        if (!time && !season) return;
        for (var entry : CLOCKS.values()) { entry.commandSun |= time; entry.commandSeason |= season; }
    }
    public static void stopping() {
        for (var level : CLOCKS.keySet()) tick(level, System.currentTimeMillis(), true);
    }
    private static void publish(String key, String value) { System.setProperty(key, value); PUBLISHED.add(key); }
    private static void clearPublished() { for (String key : PUBLISHED) System.clearProperty(key); PUBLISHED.clear(); System.clearProperty(RealTimeline.PROPERTY); }
    public static void stopped() { CLOCKS.clear(); primary = null; clearPublished(); }
}
