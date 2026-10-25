package com.empirecraft.multiverse.test;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModConfig;
import sereneseasons.season.SeasonHandler;
import ua.empirecraft.calendar.WallClockCalendar;

/** Isolated test fixture. Never install on a player's server: it stops the server. */
@Mod("empirecraft_seasons_smoketest")
public final class SeasonsSmokeMod {
    private MinecraftServer server;
    private int ticks, assertions, firstSeasonTick;
    private final boolean baseline = Boolean.getBoolean("empirecraft.seasons.baseline");

    public SeasonsSmokeMod() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void started(ServerStartedEvent event) {
        server = event.getServer();
        System.out.println("SEASONS_CONFIG path=" + ModConfig.seasons.getPath()
                + " dimensions=" + ModConfig.seasons.whitelistedDimensions);
    }

    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if (server == null || event.phase != TickEvent.Phase.END) return;
        ticks++;
        try {
            var key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("minecraft", "world_europe"));
            ServerLevel europe = server.getLevel(key);
            if (ticks == 20) {
                check(europe != null, "Europe dimension exists");
                check(ModConfig.seasons.isDimensionWhitelisted(key) != baseline, "Europe season allowlist matches phase");
                check(ModConfig.seasons.isDimensionWhitelisted(Level.OVERWORLD), "Overworld remains seasonal");
                check(!ModConfig.seasons.isDimensionWhitelisted(Level.NETHER), "Nether remains excluded");
                check(!ModConfig.seasons.isDimensionWhitelisted(Level.END), "End remains excluded");
                check(ModConfig.seasons.dayDuration == 57600, "calendar day duration preserved");
                check(ModConfig.seasons.subSeasonDuration == 30, "calendar month duration preserved");
                check(ModConfig.seasons.changeGrassColor && ModConfig.seasons.changeFoliageColor
                        && ModConfig.seasons.changeBirchColor, "seasonal client colour settings preserved");
                check(ModConfig.seasons.changeWeatherFrequency && ModConfig.seasons.generateSnowAndIce,
                        "seasonal weather settings preserved");
                check((WallClockCalendar.sunTime(europe, -99999) >= 0) != baseline,
                        "Empirecraft real-time calendar initialized only for seasonal Europe");
                if (baseline) finish();
                else {
                    firstSeasonTick = SeasonHandler.getSeasonSavedData(europe).seasonCycleTicks;
                    var state = SeasonHelper.getSeasonState(europe);
                    check(state.getDayDuration() == 57600 && state.getSubSeasonDuration() == 30 * 57600,
                            "Serene Seasons API exposes the matching calendar in Europe");
                    check(state.getSeasonCycleTicks() == firstSeasonTick, "API reads Europe's actual saved season");
                }
            }
            if (!baseline && ticks == 80) {
                int current = SeasonHandler.getSeasonSavedData(europe).seasonCycleTicks;
                check(current > firstSeasonTick, "Europe season counter advances through real level ticks");
                check(SeasonHelper.getSeasonState(europe).getSeasonCycleTicks() == current,
                        "API observes season progression");
                check(WallClockCalendar.sunTime(europe, -99999) >= 0, "Europe real-time calendar stays active");
                finish();
            }
        } catch (Throwable error) {
            System.out.println("SEASONS_SMOKE_FAIL " + error);
            error.printStackTrace();
            server.halt(false); server = null;
        }
    }

    private void finish() {
        System.out.println("SEASONS_SMOKE_PASS baseline=" + baseline + " assertions=" + assertions);
        server.halt(false); server = null;
    }
}
