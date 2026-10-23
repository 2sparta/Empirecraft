package ua.empirecraft.townymap;

import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TownyAccessTest {
    private TestPlugin plugin;

    @BeforeEach
    void reset() {
        Bukkit.server = null;
        Bukkit.manager = null;
        plugin = new TestPlugin();
        TownyAPI.instance = new TownyAPI();
        TownyAPI.lookups = 0;
    }

    @Test
    void waitsThroughEveryStartupStageThenReadsWithoutRestart() throws Exception {
        assertWaiting("Bukkit server is still initialising");
        Bukkit.server = new Object();
        assertWaiting("Bukkit plugin manager is still initialising");
        Bukkit.manager = name -> null;
        assertWaiting("Towny is not registered yet");
        Bukkit.manager = name -> "Towny".equals(name) ? plugin : null;
        assertWaiting("Towny is registered but not enabled");
        assertEquals(0, TownyAPI.lookups, "Do not initialise Towny API before the plugin is enabled");

        plugin.enabled = true;
        assertTrue(new TownyAccess().snapshot(false).isEmpty());
    }

    @Test
    void waitsForApiSingletonAndRecovers() throws Exception {
        ready();
        TownyAPI.instance = null;
        assertWaiting("Towny API is still initialising");
        TownyAPI.instance = new TownyAPI();
        assertTrue(new TownyAccess().snapshot(false).isEmpty());
    }

    @Test
    void detectsDisableThenReconnectsAfterEnable() throws Exception {
        ready();
        TownyAccess access = new TownyAccess();
        plugin.enabled = false;
        assertThrows(TownyAccess.NotReadyException.class, () -> access.snapshot(false));
        plugin.enabled = true;
        assertTrue(new TownyAccess().snapshot(false).isEmpty());
    }

    @Test
    void rejectsStalePluginInstanceAfterReload() throws Exception {
        ready();
        TownyAccess access = new TownyAccess();
        TestPlugin replacement = new TestPlugin();
        replacement.enabled = true;
        Bukkit.manager = name -> "Towny".equals(name) ? replacement : null;
        assertEquals("Towny plugin instance changed; reconnecting",
                assertThrows(TownyAccess.NotReadyException.class, () -> access.snapshot(false)).getMessage());
        assertTrue(new TownyAccess().snapshot(false).isEmpty());
    }

    @Test
    void detectsPluginRemoval() throws Exception {
        ready();
        TownyAccess access = new TownyAccess();
        Bukkit.manager = name -> null;
        assertEquals("Towny is not registered yet",
                assertThrows(TownyAccess.NotReadyException.class, () -> access.snapshot(false)).getMessage());
    }

    private void ready() {
        Bukkit.server = new Object();
        plugin.enabled = true;
        Bukkit.manager = name -> "Towny".equals(name) ? plugin : null;
    }

    private void assertWaiting(String reason) {
        assertEquals(reason, assertThrows(TownyAccess.NotReadyException.class, TownyAccess::new).getMessage());
    }

    private static final class TestPlugin implements Plugin {
        boolean enabled;
        public boolean isEnabled() { return enabled; }
    }
}

