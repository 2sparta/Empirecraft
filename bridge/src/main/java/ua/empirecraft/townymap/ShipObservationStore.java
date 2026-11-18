package ua.empirecraft.townymap;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Personal, world-scoped optical logs. A pending write remains visible to an immediate reconnect. */
final class ShipObservationStore implements AutoCloseable {
    private record FileData(int format,List<ShipObservation> ships) {}
    private static final Gson JSON = new Gson();
    private final Path directory;
    private final Consumer<Exception> errors;
    private final Map<UUID,List<ShipObservation>> pending = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r,"Empirecraft-ship-observations"); thread.setDaemon(true); return thread;
    });
    ShipObservationStore(Path directory,Consumer<Exception> errors) { this.directory=directory;this.errors=errors; }
    ShipObservation.Log load(UUID person) throws IOException {
        if (pending.containsKey(person)) return ShipObservation.Log.restore(pending.get(person));
        Path file = directory.resolve(person+".json.gz");
        if (!Files.exists(file)) return new ShipObservation.Log();
        try (var input = new GZIPInputStream(Files.newInputStream(file))) {
            byte[] raw = input.readNBytes(4*1024*1024+1);
            if (raw.length>4*1024*1024) throw new IOException("Oversized ship history");
            var data = JSON.fromJson(new String(raw,StandardCharsets.UTF_8),FileData.class);
            if (data==null || data.format()!=1) throw new IOException("Unsupported ship history");
            return ShipObservation.Log.restore(data.ships());
        } catch (RuntimeException invalid) { throw new IOException("Invalid ship history; file left untouched",invalid); }
    }
    void save(UUID person,List<ShipObservation> ships) { var snapshot=List.copyOf(ships);pending.put(person,snapshot);submit(person,snapshot); }
    private void submit(UUID person,List<ShipObservation> snapshot) {
        writer.execute(() -> {
            if (pending.get(person)!=snapshot) return;
            Path temporary = null;
            try {
                Files.createDirectories(directory);
                temporary=Files.createTempFile(directory,person+"-",".tmp");
                try (var out=new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(temporary)),StandardCharsets.UTF_8)) {
                    JSON.toJson(new FileData(1,snapshot),out);
                }
                Path target=directory.resolve(person+".json.gz");
                try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
                pending.remove(person,snapshot);
            } catch (Exception error) { errors.accept(error); }
            finally { if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){} }
        });
    }
    void retry() { pending.forEach(this::submit); }
    @Override public void close() throws IOException {
        retry();writer.shutdown();
        try { if(!writer.awaitTermination(30,TimeUnit.SECONDS))throw new IOException("Ship observations still saving"); }
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException(error);}
        if(!pending.isEmpty())throw new IOException("Unsaved ship observations: "+pending.size());
    }
}
