package ua.empirecraft.townymap;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OverlaySyncTest {
    @Test void claimChangesAndRemovedNationsReplaceOldOverlays() {
        var sync = new OverlaySync<String>();
        Map<String, String> client = new HashMap<>();
        sync.update(Map.of("nation/a", "old", "nation/b", "removed"), 4, client::remove, client::put);
        var next = Map.of("nation/a", "changed", "nation/c", "new");
        sync.update(next, 4, client::remove, client::put);
        assertEquals(next, client);
        sync.update(Map.of(), 4, client::remove, client::put);
        assertTrue(client.isEmpty());
    }

    @Test void unchangedClaimsSendNothing() {
        var sync = new OverlaySync<String>();
        var claims = Map.of("a", "claim");
        sync.update(claims, 4, id -> {}, (id, value) -> {});
        sync.update(claims, 4, id -> fail(), (id, value) -> fail());
    }

    @Test void packetBudgetConvergesAndRemovesOldPages() {
        var sync = new OverlaySync<String>();
        Map<String, String> client = new HashMap<>();
        sync.update(Map.of("a", "1", "b", "2", "c", "3"), 2, client::remove, client::put);
        assertEquals(2, client.size());
        sync.update(Map.of("a", "1", "b", "2", "c", "3"), 2, client::remove, client::put);
        assertEquals(3, client.size());
        sync.update(Map.of(), 2, client::remove, client::put);
        assertEquals(1, client.size());
        sync.update(Map.of(), 2, client::remove, client::put);
        assertTrue(client.isEmpty());
    }

    @Test void failedSendsAreRetried() {
        var sync = new OverlaySync<String>();
        assertThrows(IllegalStateException.class, () -> sync.update(Map.of("a", "1"), 4,
                id -> {}, (id, value) -> { throw new IllegalStateException(); }));
        Map<String, String> client = new HashMap<>();
        sync.update(Map.of("a", "1"), 4, client::remove, client::put);
        assertEquals(Map.of("a", "1"), client);
    }

    @Test void periodicResendStillRemovesDeletedClaims() {
        var sync = new OverlaySync<String>();
        Map<String, String> client = new HashMap<>();
        sync.update(Map.of("a", "1", "b", "2"), 4, client::remove, client::put);
        sync.invalidate();
        sync.update(Map.of("a", "1"), 4, client::remove, client::put);
        assertEquals(Map.of("a", "1"), client);
        sync.invalidate();
        client.clear();
        sync.update(Map.of("a", "1"), 4, client::remove, client::put);
        assertEquals(Map.of("a", "1"), client);
    }
}
