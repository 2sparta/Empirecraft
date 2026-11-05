package ua.empirecraft.townymap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static ua.empirecraft.townymap.ClaimKnowledgeTest.*;

class KnowledgeStoreTest {
    @TempDir Path directory;
    static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private KnowledgeStore store(Path path) { return new KnowledgeStore(path, error -> fail(error)); }
    private static ClaimKnowledge memory(String name) {
        var memory = new ClaimKnowledge(); var source = index(claim(name, 16, 0, new ClaimGeometry.Cell(100, -200)));
        memory.observe(HUNGARY, source.observe(HUNGARY), 1); return memory;
    }
    @Test void restartRestoresHistoricalCountryEvenWhenItNoLongerExists() throws Exception {
        try (var store = store(directory)) { store.save(PLAYER, memory("Kingdom of Hungary").snapshot()); }
        try (var store = store(directory)) {
            var restored = store.load(PLAYER); restored.observe(HOME, index().observe(HOME), 31L * 86400000);
            assertEquals("Kingdom of Hungary", visible(restored, MapMode.KINGDOMS).get(0).name());
            assertTrue(visible(store.load(UUID.randomUUID()), MapMode.KINGDOMS).isEmpty());
        }
    }
    @Test void immediateReconnectReadsNewestQueuedObservation() throws Exception {
        try (var store = store(directory)) {
            store.save(PLAYER, memory("Old Hungary").snapshot()); store.save(PLAYER, memory("New Hungary").snapshot());
            assertEquals("New Hungary", visible(store.load(PLAYER), MapMode.KINGDOMS).get(0).name());
        }
        try (var store = store(directory)) { assertEquals("New Hungary", visible(store.load(PLAYER), MapMode.KINGDOMS).get(0).name()); }
    }
    @Test void knownWildernessSurvivesRestartAndRemainsDifferentFromUnknown() throws Exception {
        var memory = new ClaimKnowledge(); memory.observe(HOME, List.of(), 1);
        try (var store = store(directory)) { store.save(PLAYER, memory.snapshot()); }
        try (var store = store(directory)) {
            var restored = store.load(PLAYER); assertNotNull(restored.at(HOME)); assertNull(restored.at(HUNGARY));
        }
    }
    @Test void worldsWithTheSamePlayerUuidDoNotShareHistory() throws Exception {
        try (var first = store(directory.resolve("world-a")); var second = store(directory.resolve("world-b"))) {
            first.save(PLAYER, memory("Hungary").snapshot()); assertTrue(second.load(PLAYER).snapshot().observations().isEmpty());
        }
    }
    @Test void corruptHistoryIsRejectedAndNeverOverwrittenWithAnEmptyMap() throws Exception {
        Path file = directory.resolve(PLAYER + ".json.gz"); byte[] broken = {1, 2, 3, 4}; Files.write(file, broken);
        try (var store = store(directory)) { assertThrows(IOException.class, () -> store.load(PLAYER)); assertTrue(store.load(UUID.randomUUID()).snapshot().observations().isEmpty()); }
        assertArrayEquals(broken, Files.readAllBytes(file));
    }
    @Test void unknownFormatAndOutOfChunkCellsAreRejected() throws Exception {
        Path file = directory.resolve(PLAYER + ".json.gz");
        writeGzip(file, "{\"format\":999,\"observations\":[]}");
        try (var store = store(directory)) { assertThrows(IOException.class, () -> store.load(PLAYER)); }
        String json;
        try (var store = store(directory.resolve("valid"))) { store.save(PLAYER, memory("Hungary").snapshot()); }
        try (var input = new GZIPInputStream(Files.newInputStream(directory.resolve("valid").resolve(file.getFileName())))) { json = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        writeGzip(file, json.replace("\"cells\":[{\"x\":100", "\"cells\":[{\"x\":999"));
        try (var store = store(directory)) { assertThrows(IOException.class, () -> store.load(PLAYER)); }
    }
    @Test void temporaryPartialFilesAreNeverLoadedAsHistory() throws Exception {
        Files.writeString(directory.resolve(PLAYER + "-partial.tmp"), "partial");
        try (var store = store(directory)) { assertTrue(store.load(PLAYER).snapshot().observations().isEmpty()); }
    }
    @Test void failedSaveRetainsNewestMemoryForReconnectAndRetriesAfterRecovery() throws Exception {
        Path blocked = directory.resolve("blocked"); Files.writeString(blocked, "not a directory");
        var failed = new CountDownLatch(1);
        try (var store = new KnowledgeStore(blocked, error -> failed.countDown())) {
            store.save(PLAYER, memory("Hungary").snapshot()); assertTrue(failed.await(5, TimeUnit.SECONDS));
            assertEquals("Hungary", visible(store.load(PLAYER), MapMode.KINGDOMS).get(0).name());
            Files.delete(blocked); Files.createDirectory(blocked);
        }
        try (var store = store(blocked)) { assertEquals("Hungary", visible(store.load(PLAYER), MapMode.KINGDOMS).get(0).name()); }
    }
    private static void writeGzip(Path path, String value) throws Exception {
        try (var out = new GZIPOutputStream(Files.newOutputStream(path))) { out.write(value.getBytes(StandardCharsets.UTF_8)); }
    }
}
