package ua.empirecraft.potato;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraftforge.fml.loading.FMLPaths;

/** Only our own preference file is written. Vanilla and other mods' configurations stay owned by them. */
public final class PotatoState {
    private static final Gson JSON = new Gson();
    private record Settings(int format, boolean enabled) {}
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("empirecraft-potato.json");
    private static volatile boolean enabled = load();
    private static volatile String error;
    private PotatoState() {}
    public static boolean enabled() { return enabled; }
    public static String error() { return error; }
    private static boolean load() {
        if (!Files.exists(FILE)) return true;
        try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Settings settings = JSON.fromJson(reader, Settings.class);
            if (settings == null || settings.format != 1) throw new IOException("Unsupported potato settings");
            return settings.enabled;
        } catch (Exception failure) {
            error = failure.toString(); return false;
        }
    }
    public static void setEnabled(boolean desired) throws IOException {
        Files.createDirectories(FILE.getParent());
        Path temporary = Files.createTempFile(FILE.getParent(), "empirecraft-potato-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(new Settings(1, desired)) + "\n", StandardCharsets.UTF_8);
            try { Files.move(temporary, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING); }
            enabled = desired; error = null;
        } finally { Files.deleteIfExists(temporary); }
    }
}
