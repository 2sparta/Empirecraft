package ua.empirecraft.townymap;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** World-scoped, atomic personal files; pending writes also cover an immediate reconnect. */
final class KnowledgeStore implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private final Path directory;
    private final Consumer<Exception> onError;
    private final ConcurrentMap<UUID, ClaimKnowledge.Snapshot> pending = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "EmpireCraft-claim-history-save"); thread.setDaemon(true); return thread;
    });

    KnowledgeStore(Path directory, Consumer<Exception> onError) { this.directory = directory; this.onError = onError; }
    ClaimKnowledge load(UUID player) throws IOException {
        var queued = pending.get(player);
        if (queued != null) return ClaimKnowledge.restore(queued);
        Path file = path(player);
        if (!Files.exists(file)) return new ClaimKnowledge();
        try (var reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
            var snapshot = JSON.fromJson(reader, ClaimKnowledge.Snapshot.class);
            if (snapshot == null) throw new IOException("Empty claim knowledge file");
            return ClaimKnowledge.restore(snapshot);
        } catch (RuntimeException e) { throw new IOException("Invalid claim knowledge file: " + file.getFileName(), e); }
    }
    void save(UUID player, ClaimKnowledge.Snapshot snapshot) {
        pending.put(player, snapshot);
        submit(player, snapshot);
    }
    void retryPending() { pending.forEach(this::submit); }
    private void submit(UUID player, ClaimKnowledge.Snapshot snapshot) {
        writer.execute(() -> {
            if (pending.get(player) != snapshot) return;
            try { write(player, snapshot); pending.remove(player, snapshot); }
            catch (Exception error) { onError.accept(error); }
        });
    }
    private Path path(UUID player) { return directory.resolve(player + ".json.gz"); }
    private void write(UUID player, ClaimKnowledge.Snapshot snapshot) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, player + "-", ".tmp");
        try {
            try (var out = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(temporary)), StandardCharsets.UTF_8)) {
                JSON.toJson(snapshot, out);
            }
            try { Files.move(temporary, path(player), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path(player), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    @Override public void close() throws IOException {
        retryPending(); writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) throw new IOException("Timed out saving claim knowledge");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
        if (!pending.isEmpty()) throw new IOException("Unsaved claim knowledge for " + pending.size() + " players");
    }
}
