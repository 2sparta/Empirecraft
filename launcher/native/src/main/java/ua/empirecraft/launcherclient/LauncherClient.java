package ua.empirecraft.launcherclient;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("empirecraft_launcher_client")
public final class LauncherClient {
    public static final String HOST = "p2.foxter.top";
    public static final int PORT = 25628;
    public static final String SERVER = HOST + ":" + PORT;
    private static Boolean reduced;
    private static boolean connectionRequested;
    public static void requestConnection() { connectionRequested = true; }
    public static boolean connectionRequested() { return connectionRequested; }
    public LauncherClient() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(Events::opening);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(Events::overlay);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(Events::tick);
    }

    public static boolean allowed(String host, int port) {
        return HOST.equalsIgnoreCase(host) && port == PORT;
    }
    public static boolean reducedEffects() {
        if (reduced == null) {
            reduced = "potato".equals(System.getProperty("empirecraft.launcher.profile", "potato"));
            Path path = Minecraft.getInstance().gameDirectory.toPath().resolve("config/empirecraft-launcher-profile.json");
            try {
                if (Files.isRegularFile(path) && Files.size(path) < 4096)
                    reduced = JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("reducedEffects").getAsBoolean();
            } catch (Exception ignored) { }
        }
        return reduced;
    }

    public static final class Events {
        private static String lastSmokeScreen = "";
        public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            // Initial screens can be created before mod listeners are registered.
            if (mc.screen instanceof TitleScreen) mc.setScreen(new EmpireHome());
            if (Boolean.getBoolean("empirecraft.launcher.smoke") && mc.screen != null) {
                String current = mc.screen.getClass().getName();
                if (!current.equals(lastSmokeScreen)) {
                    com.mojang.logging.LogUtils.getLogger().info("Empirecraft smoke screen: {}", current);
                    lastSmokeScreen = current;
                }
            }
        }
        @SubscribeEvent
        public static void opening(ScreenEvent.Opening event) {
            if (event.getNewScreen() instanceof TitleScreen || event.getNewScreen() instanceof SelectWorldScreen || event.getNewScreen() instanceof JoinMultiplayerScreen)
                event.setNewScreen(new EmpireHome());
        }
        @SubscribeEvent
        public static void overlay(RenderGuiOverlayEvent.Pre event) {
            // Keep thirst/temperature widgets. Only suppress the full-screen heat/frost effect.
            if (reducedEffects() && "temperature_overlay".equals(event.getOverlay().id().getPath())
                && ("empirecraft_survival".equals(event.getOverlay().id().getNamespace()) || "legendarysurvivaloverhaul".equals(event.getOverlay().id().getNamespace())))
                event.setCanceled(true);
        }
    }
}
