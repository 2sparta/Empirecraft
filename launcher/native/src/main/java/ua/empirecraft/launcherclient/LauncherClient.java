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

    @Mod.EventBusSubscriber(modid="empirecraft_launcher_client", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
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
