package ua.empirecraft.clientdebug;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;

final class ClientHooks {
    private static final Logger LOG = LogUtils.getLogger();
    static final String REPORTS = "debug/empirecraft-client";
    private static Session session;
    private static Thread exporter;
    private static volatile Status status = new Status("idle", "");
    record Status(String key, String detail) {}

    private ClientHooks() {}

    static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new DebugScreen(parent)));
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(ClientHooks::options);
        bus.addListener(ClientHooks::render);
        bus.addListener(ClientHooks::tick);
        bus.addListener(ClientHooks::shutdown);
    }

    private static void options(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof OptionsScreen screen)) return;
        Component label = key("title");
        Minecraft game = Minecraft.getInstance();
        int x = 6, y = 6, width = game.font.width(label) + 16, height = 20;
        // Keep the standard Options title centered and respect buttons added by other mods.
        if (x + width > screen.width / 2 - 58) return;
        for (var listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget w && w.visible
                    && x < w.getX() + w.getWidth() && x + width > w.getX()
                    && y < w.getY() + w.getHeight() && y + height > w.getY()) return;
        }
        event.addListener(Button.builder(label, button -> game.setScreen(new DebugScreen(screen)))
                .bounds(x, y, width, height).build());
    }

    static Component key(String name, Object... args) {
        return Component.translatable(ClientDebugMod.ID + "." + name, args);
    }

    static Status status() { return status; }
    static boolean running() { return session != null; }

    static void setEnabled(boolean enable) {
        if (!enable) {
            stop("disabled in settings", true);
            return;
        }
        if (session != null || status.key().equals("saving")) return;
        FlightProfile profile = null;
        String warning = null;
        try {
            profile = FlightProfile.start(Session.LIMIT);
        } catch (RuntimeException | LinkageError e) {
            warning = e.getClass().getSimpleName();
            LOG.warn("Client JFR is unavailable. Continuing with client metrics only.", e);
        }
        session = new Session(profile, warning);
        status = new Status(session.hasFlightProfile() ? "recording" : "recording_metrics", "");
    }

    private static void render(TickEvent.RenderTickEvent event) {
        Session active = session;
        if (active == null) return;
        long now = System.nanoTime();
        if (event.phase == TickEvent.Phase.START) active.renderStart(now);
        else active.renderEnd(now);
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        Session active = session;
        if (active == null) return;
        long now = System.nanoTime();
        if (event.phase == TickEvent.Phase.START) {
            active.tickStart(now);
            return;
        }
        active.tickEnd(now);
        if (now - active.started >= Session.LIMIT.toNanos()) stop("five-minute limit", true);
        else if (active.sampleDue(now)) active.sample(now, snapshot());
    }

    private static Session.Context snapshot() {
        Minecraft game = Minecraft.getInstance();
        var level = game.level;
        String scene = level == null ? "menu" : game.isPaused() ? "paused" : game.screen == null ? "world" : "world_screen";
        return new Session.Context(scene, game.isWindowActive(),
                level == null ? 0 : level.getChunkSource().getLoadedChunksCount(),
                level == null ? 0 : level.getEntityCount());
    }

    private static void stop(String reason, boolean async) {
        Session completed = session;
        if (completed == null) return;
        session = null;
        completed.sample(System.nanoTime(), snapshot());
        Path directory = Minecraft.getInstance().gameDirectory.toPath().resolve(REPORTS);
        status = new Status("saving", "");
        if (!async) {
            export(completed, directory, reason);
            return;
        }
        exporter = new Thread(() -> export(completed, directory, reason), "Empirecraft client report writer");
        exporter.setDaemon(true);
        exporter.start();
    }

    private static void export(Session completed, Path directory, String reason) {
        try {
            var result = completed.save(directory, reason);
            LOG.info("Client diagnostics saved to {}", result.path());
            status = new Status(result.metricsOnly() ? "saved_metrics" : "saved", result.path().getFileName().toString());
        } catch (IOException | RuntimeException | LinkageError e) {
            LOG.error("Could not save client diagnostics to {}", directory, e);
            status = new Status("error", "");
        }
    }

    private static void shutdown(GameShuttingDownEvent event) {
        stop("client closed", false);
        if (exporter == null) return;
        try {
            exporter.join(4_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
