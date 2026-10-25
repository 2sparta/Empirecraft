package com.empirecraft.multiverse.test;

import com.quintonc.vs_sails.wind.ServerWindManager;
import com.quintonc.vs_sails.wind.WindManager;
import com.quintonc.vs_sails.wind.WindRuleRegistry;
import com.wdiscute.starcatcher.registry.FishProperties;
import com.wdiscute.starcatcher.registry.fishrestrictions.DimensionRestriction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

/** Test only: creates no production content and stops the isolated test server. */
@Mod("empirecraft_multiverse_smoketest")
public final class MultiverseSmokeMod {
    private MinecraftServer server;
    private int ticks, assertions;
    private final boolean baseline = Boolean.getBoolean("empirecraft.multiverse.baseline");

    public MultiverseSmokeMod() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private void started(ServerStartedEvent event) { server = event.getServer(); }

    private void tick(TickEvent.ServerTickEvent event) {
        if (server == null || event.phase != TickEvent.Phase.END) return;
        ticks++;
        try {
            var key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("minecraft:world_europe"));
            ServerLevel europe = server.getLevel(key);
            if (ticks == 20) {
                check(europe != null, "world_europe exists with the same key as Mohist/Multiverse");
                europe.setDayTime(6000);
                var rule = WindRuleRegistry.getWind(europe);
                check(rule.dimensionMultiplier() == (baseline ? 0 : 1), "wind rule selected for Europe");
                var cod = FishProperties.getFP(europe, new ResourceLocation("minecraft:cod"));
                check(cod != null, "cod loaded through the real Starcatcher registry codec");
                var dimension = cod.restrictions().stream()
                        .filter(DimensionRestriction.class::isInstance).map(DimensionRestriction.class::cast)
                        .findFirst().orElseThrow();
                check((dimension.getFishChance(5, europe, cod, null, ItemStack.EMPTY, null) >= 0) != baseline,
                        "cod dimension restriction matches the test phase");
                check(dimension.getFishChance(5, server.overworld(), cod, null, ItemStack.EMPTY, null) >= 0,
                        "original Overworld remains allowed");
                check(dimension.getFishChance(5, server.getLevel(Level.NETHER), cod, null, ItemStack.EMPTY, null) < 0,
                        "cod remains blocked in the Nether");
                check(dimension.getFishChance(5, server.getLevel(Level.END), cod, null, ItemStack.EMPTY, null) < 0,
                        "cod remains blocked in the End");
                if (!baseline) {
                    check(server.getPackRepository().getSelectedIds().contains("file/empirecraft-multiverse.zip"),
                            "new pack enabled automatically on the existing test world");
                    int patched = 0;
                    for (var fish : FishProperties.getAllFPs(europe)) {
                        for (var restriction : fish.restrictions()) {
                            if (restriction instanceof DimensionRestriction d && d.getDimensions().contains(new ResourceLocation("minecraft:overworld"))) {
                                check(d.getDimensions().contains(key.location()), "all loaded Overworld fishing restrictions include Europe");
                                patched++;
                            }
                            if (restriction instanceof DimensionRestriction d &&
                                    (d.getDimensions().contains(new ResourceLocation("minecraft:the_nether")) ||
                                     d.getDimensions().contains(new ResourceLocation("minecraft:the_end")))) {
                                check(d.getFishChance(5, europe, fish, null, ItemStack.EMPTY, null) < 0,
                                        "Nether and End catches remain restricted");
                            }
                        }
                    }
                    check(patched > 100, "full native Starcatcher pool was patched");
                    check(WindRuleRegistry.getWind(server.overworld()).dimensionMultiplier() == 1,
                            "original Overworld wind unchanged");
                    check(WindRuleRegistry.getWind(server.getLevel(Level.NETHER)).dimensionMultiplier() == 1.3,
                            "Nether wind unchanged");
                    check(WindRuleRegistry.getWind(server.getLevel(Level.END)).dimensionMultiplier() == 1.1,
                            "End wind unchanged");
                }
                if (baseline) finish();
            }
            if (!baseline && ticks == 320) {
                check(Math.abs(ServerWindManager.getCachedStrength(europe)) > 0,
                        "world_europe receives nonzero wind from actual level ticks");
                check(WindManager.isWindEnabled(europe), "wind enabled in Europe");
                check(Math.abs(WindManager.getWindStrength(europe, new BlockPos(0, 80, 0))) > 0,
                        "Sails force consumer sees nonzero wind in Europe");
                finish();
            }
        } catch (Throwable error) {
            System.out.println("MULTIVERSE_SMOKE_FAIL " + error);
            error.printStackTrace();
            server.halt(false); server = null;
        }
    }

    private void finish() {
        System.out.println("MULTIVERSE_SMOKE_PASS baseline=" + baseline + " assertions=" + assertions);
        server.halt(false); server = null;
    }
}
