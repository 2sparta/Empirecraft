package ua.empirecraft.calendar;

import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.logging.Logger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModConfig;
import sereneseasons.init.ModFertility;
import sereneseasons.season.SeasonHandler;

@Mod("empirecraft_calendar")
public final class CalendarMod {
    private static final Logger LOG = Logger.getLogger("EmpirecraftCalendar");
    public CalendarMod() {
        CalendarNetwork.init();
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(this::started);
        bus.addListener((ServerAboutToStartEvent event) -> { repairTiming(); WallClockCalendar.beforeStart(event.getServer()); });
        bus.addListener((ServerStoppingEvent event) -> WallClockCalendar.stopping());
        bus.addListener((ServerStoppedEvent event) -> WallClockCalendar.stopped());
        bus.addListener(EventPriority.HIGHEST, (TickEvent.LevelTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START && event.level instanceof ServerLevel level)
                WallClockCalendar.tick(level, System.currentTimeMillis(), false);
        });
        bus.addListener(EventPriority.LOWEST, WallClockCalendar::command);
        bus.addListener(this::commands);
        bus.addListener(this::joined);
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) CalendarNetwork.sync(player);
        });
        bus.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof net.minecraft.world.level.Level level && level.m_5776_())
                CalendarNetwork.unload(level.m_46472_().m_135782_());
        });
        bus.addListener(EventPriority.LOWEST, this::sleep);
        bus.addListener(EventPriority.LOW, this::spawns);
        bus.addListener(EventPriority.LOWEST, this::spawnPosition);
        bus.addListener(EventPriority.LOWEST, this::bonemeal);
    }
    public static Component date(net.minecraft.world.level.Level level) {
        if (!ModConfig.seasons.isDimensionWhitelisted(level.m_46472_()) && level.m_7654_() != null)
            level = level.m_7654_().m_129783_();
        var state = SeasonHelper.getSeasonState(level);
        var current = level instanceof ServerLevel serverLevel ? WallClockCalendar.current(serverLevel) : null;
        int ticks = current == null ? state.getSeasonCycleTicks() : current.seasonTicks();
        long minutes = Math.floorMod((current == null ? level.m_46468_() : current.sunTicks()) + 6000, 24_000L) * 1440 / 24_000;
        long year = current == null ? CalendarNetwork.clientYear(level.m_46472_().m_135782_()) : current.year();
        return Component.m_237110_("calendar.empirecraft.date_with_year", CalendarMath.dayOfMonth(ticks),
            Component.m_237115_("calendar.empirecraft.month." + (CalendarMath.monthIndex(ticks) + 1)), year)
            .m_130946_(" • " + String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60));
    }
    private static void repairTiming() {
        if (ModConfig.seasons.dayDuration == CalendarMath.DAY_TICKS && ModConfig.seasons.subSeasonDuration == 30) return;
        LOG.warning("Repairing Serene Seasons timing to day_duration=57600, sub_season_duration=30; other settings and world data are retained");
        ModConfig.seasons.dayDuration = CalendarMath.DAY_TICKS;
        ModConfig.seasons.subSeasonDuration = 30;
        try { CalendarConfigRepair.install(FMLPaths.CONFIGDIR.get().resolve("sereneseasons/seasons.toml")); }
        catch (java.io.IOException error) { LOG.warning("Calendar timing corrected in memory, but config could not be saved: " + error); }
    }
    private void started(ServerStartedEvent event) {
        repairTiming();
        for (var level : event.getServer().m_129785_()) {
            if (!ModConfig.seasons.isDimensionWhitelisted(level.m_46472_())) continue;
            var marker = level.m_8895_().m_164861_(CalendarData::load, CalendarData::new, "empirecraft_calendar");
            boolean hadOldCalendar = marker.migrated;
            if (!marker.migrated) {
                var season = SeasonHandler.getSeasonSavedData(level);
                season.seasonCycleTicks = CalendarMath.migrateOriginalCycle(season.seasonCycleTicks);
                season.m_77762_();
                marker.migrated = true;
                marker.m_77762_();
                SeasonHandler.sendSeasonUpdate(level);
            }
            SeasonHandler.lastDayTimes.put(level, 0L);
            WallClockCalendar.initialize(level, marker, hadOldCalendar);
        }
        auditCoverage();
        LOG.info("Real-time calendar ready: 48-minute days, 30-day months; downtime and TPS changes are included");
    }
    private void commands(RegisterCommandsEvent event) {
        CalendarCommands.register(event.getDispatcher(), source -> source.m_6761_(2), context -> {
            var level = context.getSource().m_81372_();
            context.getSource().m_288197_(() -> date(level), false);
            return 1;
        }, (source, edit) -> {
            try { WallClockCalendar.edit(source.m_81372_(), edit); }
            catch (IllegalStateException error) {
                throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.m_237115_("calendar.empirecraft.unavailable")).create();
            }
            source.m_288197_(() -> Component.m_237110_("calendar.empirecraft.updated", date(source.m_81372_())), true);
            return 1;
        });
    }
    private void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) CalendarNetwork.sync(player);
        if (event.getEntity().m_9236_() instanceof ServerLevel level)
            event.getEntity().m_213846_(date(level));
    }
    private void sleep(SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof ServerLevel level && ModConfig.seasons.isDimensionWhitelisted(level.m_46472_()))
            // Despite its name this API accepts an absolute time, never a delta below minTime.
            event.setTimeAddition(level.m_46468_());
    }
    private void bonemeal(BonemealEvent event) {
        if (!event.getLevel().m_5776_() && !SeasonalPlants.canGrow(event.getLevel(), event.getPos(), event.getBlock())) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);
        }
    }
    private static double factor(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.EntityType<?> type) {
        var id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (id == null) return 1;
        int month = CalendarMath.monthIndex(SeasonHelper.getSeasonState(level).getSeasonCycleTicks());
        var biome=level.m_204166_(pos);
        String habitat=biome.m_203543_().map(k -> k.m_135782_().toString()).orElse("");
        return HabitatRules.factor(id.toString(), FaunaRules.profile(id.toString(), type.m_20674_().name()),
            FaunaRules.factor(id.toString(), type.m_20674_().name(), month), biome.m_203334_().m_47554_(), habitat, level.m_46471_());
    }
    private void spawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ModConfig.seasons.isDimensionWhitelisted(level.m_46472_())) return;
        if (event.getSpawnerDataList().stream().allMatch(entry -> factor(level, event.getPos(), entry.f_48404_) == 1)) return;
        // Only reweight existing biome entries. Never introduce animals into foreign biomes or raise caps.
        for (var entry : new ArrayList<>(event.getSpawnerDataList())) {
            double multiplier = factor(level, event.getPos(), entry.f_48404_);
            int weight = (int)Math.min(32767, Math.max(1, Math.round(entry.m_142631_().m_146281_() * multiplier * 100)));
            event.removeSpawnerData(entry);
            if (multiplier > 0) event.addSpawnerData(new MobSpawnSettings.SpawnerData(entry.f_48404_, weight, entry.f_48405_, entry.f_48406_));
        }
    }
    private void spawnPosition(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) return;
        var level = event.getLevel().m_6018_();
        if (!ModConfig.seasons.isDimensionWhitelisted(level.m_46472_())) return;
        double multiplier = factor(level, event.getEntity().m_20183_(), event.getEntity().m_6095_());
        // Natural spawns were already reweighted. A second probability roll squared the winter penalty.
        // Chunk generation does not consistently use PotentialSpawns, so keep its single acceptance roll.
        double chance = event.getSpawnType() == MobSpawnType.CHUNK_GENERATION ? Math.min(1, multiplier) : (multiplier > 0 ? 1 : 0);
        if (chance < 1 && level.m_213780_().m_188500_() >= chance) event.setResult(Event.Result.DENY);
    }
    private static void auditCoverage() {
        try {
            var animals = new LinkedHashMap<String, String>();
            for (var type : ForgeRegistries.ENTITY_TYPES) {
                var id = ForgeRegistries.ENTITY_TYPES.getKey(type);
                animals.put(id.toString(), FaunaRules.profile(id.toString(), type.m_20674_().name()));
            }
            var plants = new ArrayList<String>();
            for (var block : ForgeRegistries.BLOCKS) if (ModFertility.isCrop(block.m_49966_())) plants.add(ForgeRegistries.BLOCKS.getKey(block).toString());
            var report = new LinkedHashMap<String, Object>();
            report.put("animals", animals); report.put("seasonal_plants", plants);
            var output = FMLPaths.CONFIGDIR.get().resolve("empirecraft-calendar-coverage.json");
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8);
        } catch (Exception error) { LOG.warning("Cannot write calendar coverage: " + error); }
    }
}
