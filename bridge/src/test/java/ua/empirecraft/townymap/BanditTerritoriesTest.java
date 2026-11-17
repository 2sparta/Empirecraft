package ua.empirecraft.townymap;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BanditTerritoriesTest {
    private static final String WORLD = "minecraft:overworld";
    private static Claims land(String nation, String name, int size, ClaimGeometry.Cell... cells) {
        return new Claims("nation/" + nation, name, WORLD, 0x123456, size, Set.of(cells), "Details", 0, nation);
    }
    private static Map<MapMode, Map<String, Claims>> source(Claims... rows) {
        var source = new EnumMap<MapMode, Map<String, Claims>>(MapMode.class);
        for (var mode : MapMode.values()) {
            var map = new HashMap<String, Claims>();
            for (var row : rows) map.put(row.id(), row);
            source.put(mode, map);
        }
        return source;
    }
    private static Claims marker(String nation, String title) {
        return new Claims("marker/" + nation, title, WORLD, 0xffd700, 1,
                Set.of(new ClaimGeometry.Cell(100, 100)), "Secret centre", 4, nation);
    }

    @Test void allRequestedPrefixesSuffixesAlphabetsAndSeparators() {
        for (String word : List.of("Розбійники", "Rozbiynyky", "Разбойники"))
            for (String name : List.of("Січ", "Sich", "Сечь"))
                for (String separator : List.of("", "_", "*", "*_", " ")) {
                    assertTrue(BanditNames.matches(word + separator + name));
                    assertTrue(BanditNames.matches(name + separator + word));
                    assertTrue(BanditNames.matches("  " + word.toUpperCase(Locale.ROOT) + separator + name + "  "));
                }
        for (String name : List.of("Україна", "Hungary", "A_Rozbiynyky_B", "Розбійник_Січ", "Rozbiynyk", "Разбойник"))
            assertFalse(BanditNames.matches(name), name);
        assertFalse(BanditNames.matches(null));
    }

    @Test void exactSixteenBlockThresholdAndMovementWithinSameChunk() {
        var bandits = new BanditTerritories(source(land("a", "Розбійники_Січ", 16, new ClaimGeometry.Cell(2, 0))));
        assertTrue(bandits.nearby(WORLD, 15.999, 8).isEmpty());
        assertEquals(Set.of("a"), bandits.nearby(WORLD, 16, 8));
        assertEquals(Set.of("a"), bandits.nearby(WORLD, 48, 8));
        assertEquals(Set.of("a"), bandits.nearby(WORLD, 64, 8));
        assertTrue(bandits.nearby(WORLD, 64.001, 8).isEmpty());
        assertEquals(ClaimKnowledge.Chunk.at(WORLD, 64, 8), ClaimKnowledge.Chunk.at(WORLD, 65, 8));
        assertTrue(bandits.nearby(WORLD, 65, 8).isEmpty());
    }

    @Test void diagonalDistanceIsEuclideanAndHolesRemainWilderness() {
        var bandits = new BanditTerritories(source(land("a", "Rozbiynyky_A", 16, new ClaimGeometry.Cell(0, 0))));
        assertEquals(Set.of("a"), bandits.nearby(WORLD, -9, -12));
        assertTrue(bandits.nearby(WORLD, -12, -12).isEmpty());
        var ring = new HashSet<ClaimGeometry.Cell>();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            if (Math.abs(x) == 3 || Math.abs(z) == 3) ring.add(new ClaimGeometry.Cell(x, z));
        var donut = new BanditTerritories(source(land("a", "Rozbiynyky_A", 16, ring.toArray(ClaimGeometry.Cell[]::new))));
        assertTrue(donut.nearby(WORLD, 8, 8).isEmpty());
        assertEquals(Set.of("a"), donut.nearby(WORLD, 40, 8));
    }

    @Test void insideLargeTerritoryAndNonstandardTownyCells() {
        for (int size : List.of(1, 3, 16, 17, 32, 1024)) {
            var bandits = new BanditTerritories(source(land("a", "РозбійникиA", size, new ClaimGeometry.Cell(-1, -1))));
            assertEquals(Set.of("a"), bandits.nearby(WORLD, -size / 2.0, -size / 2.0));
            assertEquals(Set.of("a"), bandits.nearby(WORLD, -size - 16, -0.5));
            assertTrue(bandits.nearby(WORLD, -size - 16.01, -0.5).isEmpty());
            assertEquals(Set.of("a"), bandits.nearby(WORLD, 16, -0.5));
            assertTrue(bandits.nearby(WORLD, 16.01, -0.5).isEmpty());
        }
    }

    @Test void revealsOnlyNearbyCountryInCurrentDimensionForEachPlayerAndMode() {
        var rows = source(land("a", "РозбійникиA", 16, new ClaimGeometry.Cell(0, 0), new ClaimGeometry.Cell(100, 0)),
                land("b", "BРазбойники", 16, new ClaimGeometry.Cell(200, 0)), marker("a", "★ Столиця: Січ"));
        var bandits = new BanditTerritories(rows);
        for (var mode : MapMode.values()) {
            var near = bandits.visible(mode, WORLD, bandits.nearby(WORLD, -16, 8));
            assertEquals(2, near.size());
            assertTrue(near.stream().allMatch(c -> c.banditNation().equals("a")));
            assertTrue(near.stream().anyMatch(c -> c.cells().contains(new ClaimGeometry.Cell(100, 0))));
            assertTrue(bandits.visible(mode, WORLD, bandits.nearby(WORLD, -17, 8)).isEmpty());
            assertTrue(bandits.visible(mode, "minecraft:the_nether", bandits.nearby("minecraft:the_nether", 0, 0)).isEmpty());
        }
        assertTrue(bandits.nearby(WORLD, 100, 100).isEmpty(), "Markers do not extend the detection radius");
    }

    @Test void townsAndTradeLayersUseNationIdentityRatherThanTheirOwnNames() {
        var rows = source(land("a", "Rozbiynyky_A", 16, new ClaimGeometry.Cell(0, 0)));
        rows.put(MapMode.TOWNS, Map.of("town", new Claims("town", "Звичайна назва міста", WORLD, 1, 16,
                Set.of(new ClaimGeometry.Cell(0, 0)), "Town", 0, "a")));
        rows.put(MapMode.TRADE, Map.of("trade", marker("a", "Торговий центр")));
        var bandits = new BanditTerritories(rows);
        for (var mode : MapMode.values()) {
            assertEquals(1, bandits.visible(mode, WORLD, bandits.nearby(WORLD, 8, 8)).size());
            assertTrue(bandits.visible(mode, WORLD, bandits.nearby(WORLD, 1000, 1000)).isEmpty());
        }
    }

    @Test void banditsNeverEnterPermanentHistoryAndOrdinaryCountriesKeepTheirHistory() {
        var rows = source(land("a", "РозбійникиA", 16, new ClaimGeometry.Cell(0, 0)), marker("a", "Secret capital"),
                land("", "Hungary", 16, new ClaimGeometry.Cell(1, 0)));
        var index = new ClaimIndex(rows); var knowledge = new ClaimKnowledge();
        var banditChunk = new ClaimKnowledge.Chunk(WORLD, 0, 0);
        knowledge.observe(banditChunk, index.observe(banditChunk), 1);
        assertTrue(knowledge.at(banditChunk).fragments().isEmpty());
        var normalChunk = new ClaimKnowledge.Chunk(WORLD, 1, 0);
        knowledge.observe(normalChunk, index.observe(normalChunk), 2);
        var restarted = ClaimKnowledge.restore(knowledge.snapshot());
        for (var mode : MapMode.values()) {
            assertEquals(1, ClaimKnowledge.claims(restarted.snapshot(), mode).size());
            assertEquals("Hungary", ClaimKnowledge.claims(restarted.snapshot(), mode).get(0).name());
        }
    }

    @Test void upgradeRemovesLegacyBanditTownsAndMarkersInAllModes() {
        var legacySource = ClaimKnowledgeTest.index(ClaimKnowledgeTest.claim("Розбійники_Січ", 16, 0, new ClaimGeometry.Cell(0, 0)),
                ClaimKnowledgeTest.claim("Old city", 16, 2, new ClaimGeometry.Cell(0, 0)),
                ClaimKnowledgeTest.claim("Capital", 1, 4, new ClaimGeometry.Cell(1, 1)));
        var old = new ClaimKnowledge(); old.observe(ClaimKnowledgeTest.HOME, legacySource.observe(ClaimKnowledgeTest.HOME), 1);
        var restored = ClaimKnowledge.restore(old.snapshot());
        assertTrue(restored.dirty());
        for (var mode : MapMode.values()) assertTrue(ClaimKnowledge.claims(restored.snapshot(), mode).isEmpty());
    }

    @Test void independentTownNamesDoNotTurnTownsIntoBanditCountries() {
        var town = new Claims("KINGDOMS/town/independent", "Розбійники_Місто", WORLD, 1, 16,
                Set.of(new ClaimGeometry.Cell(0, 0)), "Independent town", 0);
        var index = new ClaimIndex(source(town)); var memory = new ClaimKnowledge();
        memory.observe(ClaimKnowledgeTest.HOME, index.observe(ClaimKnowledgeTest.HOME), 1);
        assertEquals(1, ClaimKnowledge.claims(ClaimKnowledge.restore(memory.snapshot()).snapshot(), MapMode.KINGDOMS).size());
        assertTrue(new BanditTerritories(source(town)).nearby(WORLD, 8, 8).isEmpty());
    }

    @Test void legacyMigrationPreservesNeighbouringCountryInTheSameMinecraftChunk() {
        var source = ClaimKnowledgeTest.index(ClaimKnowledgeTest.claim("РозбійникиA", 8, 0, new ClaimGeometry.Cell(0, 0)),
                new Claims("KINGDOMS/nation/normal", "Hungary", WORLD, 1, 8,
                        Set.of(new ClaimGeometry.Cell(1, 0)), "Normal country", 0));
        var history = new ClaimKnowledge(); history.observe(ClaimKnowledgeTest.HOME, source.observe(ClaimKnowledgeTest.HOME), 1);
        var restored = ClaimKnowledge.restore(history.snapshot());
        for (var mode : MapMode.values()) {
            var claims = ClaimKnowledge.claims(restored.snapshot(), mode);
            assertEquals(1, claims.size());
            assertEquals("Hungary", claims.get(0).name());
            assertEquals(Set.of(new ClaimGeometry.Cell(1, 0)), claims.get(0).cells());
        }
    }

    @Test void historicalMarkerOutsideCountryBorderIsHiddenByItsOwnerIdentity() {
        var ordinary = new Claims("marker/a", "Capital", WORLD, 1, 1,
                Set.of(new ClaimGeometry.Cell(100, 100)), "Old ruler", 4);
        var index = new ClaimIndex(source(ordinary)); var memory = new ClaimKnowledge();
        var chunk = ClaimKnowledge.Chunk.at(WORLD, 100, 100); memory.observe(chunk, index.observe(chunk), 1);
        var bandits = new BanditTerritories(source(land("a", "РозбійникиA", 16, new ClaimGeometry.Cell(0, 0)), marker("a", "New capital")));
        for (var mode : MapMode.values()) assertTrue(ClaimKnowledge.claims(bandits.history(memory.snapshot()), mode).isEmpty());
    }

    @Test void renamedOrReassignedLandCannotLeakOldCountryOrCapitalInAnyMode() {
        var normal = ClaimKnowledgeTest.index(ClaimKnowledgeTest.claim("Hungary", 16, 0, new ClaimGeometry.Cell(0, 0), new ClaimGeometry.Cell(1, 0)),
                ClaimKnowledgeTest.claim("Capital", 1, 4, new ClaimGeometry.Cell(1, 1)));
        var history = new ClaimKnowledge();
        history.observe(ClaimKnowledgeTest.HOME, normal.observe(ClaimKnowledgeTest.HOME), 1);
        var other = new ClaimKnowledge.Chunk(WORLD, 1, 0); history.observe(other, normal.observe(other), 2);
        var bandits = new BanditTerritories(source(land("a", "РазбойникиA", 16, new ClaimGeometry.Cell(0, 0))));
        var filtered = ClaimKnowledge.restore(bandits.history(history.snapshot()));
        for (var mode : MapMode.values()) {
            assertTrue(filtered.labelsAt(ClaimKnowledgeTest.HOME, mode, 1, 1).isEmpty());
            assertEquals(1, ClaimKnowledge.claims(filtered.snapshot(), mode).size());
            assertEquals(Set.of(new ClaimGeometry.Cell(1, 0)), ClaimKnowledge.claims(filtered.snapshot(), mode).get(0).cells());
        }
        assertEquals(2, history.snapshot().observations().size(), "Filtering does not overwrite ordinary history");
    }

    @Test void removalsBypassUploadBudgetAndFailedRemovalsAreRetried() {
        var sync = new OverlaySync<String>(); var client = new HashMap<String, String>();
        var desired = new HashMap<String, String>();
        for (int i = 0; i < 20; i++) desired.put("temporary/" + i, "bandit");
        desired.put("ordinary", "Hungary");
        sync.update(desired, 100, client::remove, client::put);
        assertThrows(IllegalStateException.class, () -> sync.removeMatching(id -> id.startsWith("temporary/"), id -> { throw new IllegalStateException(); }));
        sync.removeMatching(id -> id.startsWith("temporary/"), client::remove);
        assertEquals(Map.of("ordinary", "Hungary"), client);
        sync.update(Map.of("ordinary", "Hungary"), 4, client::remove, client::put);
        assertEquals(Map.of("ordinary", "Hungary"), client);
    }
}
