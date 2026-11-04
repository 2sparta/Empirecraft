package ua.empirecraft.townymap;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimKnowledgeTest {
    static final String WORLD = "minecraft:overworld";
    static final ClaimKnowledge.Chunk HOME = new ClaimKnowledge.Chunk(WORLD, 0, 0);
    static final ClaimKnowledge.Chunk HUNGARY = new ClaimKnowledge.Chunk(WORLD, 100, -200);
    static Claims claim(String name, int size, int layer, ClaimGeometry.Cell... cells) {
        return new Claims("nation/hungary/" + layer, name, WORLD, 0x123456, size, Set.of(cells), "Ruler of " + name, layer);
    }
    static ClaimIndex index(Claims... claims) {
        var modes = new EnumMap<MapMode, Map<String, Claims>>(MapMode.class);
        for (MapMode mode : MapMode.values()) {
            var rows = new HashMap<String, Claims>(); for (Claims c : claims) rows.put(c.id(), c);
            modes.put(mode, rows);
        }
        return new ClaimIndex(modes);
    }
    static List<Claims> visible(ClaimKnowledge knowledge, MapMode mode) { return ClaimKnowledge.claims(knowledge.snapshot(), mode); }
    static double area(List<Claims> claims) {
        double area = 0;
        for (Claims c : claims) for (var p : ClaimGeometry.buildBounded(c.cells(), c.cellSize())) {
            area += Math.abs(ClaimGeometry.signedArea(p.outer()));
            for (var h : p.holes()) area -= Math.abs(ClaimGeometry.signedArea(h));
        }
        return area;
    }

    @Test void neverVisitedTerritoriesAreAbsentInEveryMode() {
        var source = index(claim("Kingdom of Hungary", 16, 0, new ClaimGeometry.Cell(100, -200)));
        var knowledge = new ClaimKnowledge(); knowledge.observe(HOME, source.observe(HOME), 1);
        for (MapMode mode : MapMode.values()) assertTrue(visible(knowledge, mode).isEmpty());
        assertNull(knowledge.at(HUNGARY));
    }
    @Test void destroyedHungaryRemainsAfterAMonthUntilThatChunkIsRevisited() {
        var old = index(claim("Kingdom of Hungary", 16, 0, new ClaimGeometry.Cell(100, -200)));
        var memory = new ClaimKnowledge(); memory.observe(HUNGARY, old.observe(HUNGARY), 1);
        var monthLater = index(); memory.observe(HOME, monthLater.observe(HOME), 31L * 86400000);
        assertEquals("Kingdom of Hungary", visible(memory, MapMode.KINGDOMS).get(0).name());
        memory.observe(HUNGARY, monthLater.observe(HUNGARY), 31L * 86400000 + 1);
        assertTrue(visible(memory, MapMode.KINGDOMS).isEmpty());
        assertNotNull(memory.at(HUNGARY)); // Wilderness is a real observation, not an unknown chunk.
    }
    @Test void revisitUpdatesOnlyTheVisitedPartOfTheFormerCountry() {
        var neighbour = new ClaimKnowledge.Chunk(WORLD, 101, -200);
        var old = index(claim("Hungary", 16, 0, new ClaimGeometry.Cell(100, -200), new ClaimGeometry.Cell(101, -200)));
        var memory = new ClaimKnowledge(); memory.observe(HUNGARY, old.observe(HUNGARY), 1); memory.observe(neighbour, old.observe(neighbour), 2);
        memory.observe(HUNGARY, index().observe(HUNGARY), 3);
        assertEquals(Set.of(new ClaimGeometry.Cell(101, -200)), visible(memory, MapMode.KINGDOMS).get(0).cells());
        assertEquals(256, area(visible(memory, MapMode.KINGDOMS)));
    }
    @Test void namesColorsAndDetailsAreFrozenSeparatelyForEachObservedChunk() {
        var a = claim("Old Hungary", 16, 0, new ClaimGeometry.Cell(0, 0), new ClaimGeometry.Cell(1, 0));
        var b = new Claims(a.id(), "New Hungary", WORLD, 0xffcc00, 16, a.cells(), "New ruler", 0);
        var memory = new ClaimKnowledge(); var old = index(a);
        memory.observe(HOME, old.observe(HOME), 1);
        var next = new ClaimKnowledge.Chunk(WORLD, 1, 0); memory.observe(next, old.observe(next), 2);
        memory.observe(HOME, index(b).observe(HOME), 3);
        var claims = visible(memory, MapMode.KINGDOMS);
        assertEquals(2, claims.size());
        assertTrue(claims.stream().anyMatch(c -> c.name().equals("Old Hungary") && c.color() == 0x123456 && c.detail().equals("Ruler of Old Hungary") && c.cells().equals(Set.of(new ClaimGeometry.Cell(1, 0)))));
        assertTrue(claims.stream().anyMatch(c -> c.name().equals("New Hungary") && c.detail().equals("New ruler")));
        assertNotEquals(claims.get(0).id(), claims.get(1).id());
    }
    @Test void observationsBelongToIndividualPlayers() {
        var source = index(claim("Hungary", 16, 0, new ClaimGeometry.Cell(100, -200)));
        var alice = new ClaimKnowledge(); alice.observe(HUNGARY, source.observe(HUNGARY), 1);
        var bob = new ClaimKnowledge(); bob.observe(HOME, source.observe(HOME), 1);
        assertEquals(1, visible(alice, MapMode.KINGDOMS).size()); assertTrue(visible(bob, MapMode.KINGDOMS).isEmpty());
    }
    @Test void switchingModesUsesOnlyTheOriginalVisitForAllLayers() {
        var old = index(claim("Hungary", 16, 0, new ClaimGeometry.Cell(0, 0)), claim("Old county", 16, 2, new ClaimGeometry.Cell(0, 0)));
        var memory = new ClaimKnowledge(); memory.observe(HOME, old.observe(HOME), 1);
        var current = index(); // Today's removed estates cannot affect a mode switch or a tooltip read.
        for (MapMode mode : MapMode.values()) {
            assertEquals(2, visible(memory, mode).size()); assertEquals(2, memory.at(HOME).fragments().stream().filter(f -> f.label().mode() == mode).count());
        }
        assertTrue(current.observe(HOME).isEmpty()); assertEquals(1, memory.revision());
    }
    @Test void aNewClaimInRememberedWildernessIsUnknownUntilRevisit() {
        var memory = new ClaimKnowledge(); memory.observe(HOME, List.of(), 1);
        assertEquals(0, memory.revision()); assertTrue(memory.dirty());
        assertNotNull(memory.at(HOME)); // Persist wilderness without rebuilding identical empty geometry.
        var newClaims = index(claim("New country", 16, 0, new ClaimGeometry.Cell(0, 0)));
        assertTrue(visible(memory, MapMode.KINGDOMS).isEmpty());
        memory.observe(HOME, newClaims.observe(HOME), 2);
        assertEquals(1, memory.revision());
        assertEquals("New country", visible(memory, MapMode.KINGDOMS).get(0).name());
    }
    @Test void geometryDoesNotFillUnvisitedGapsOrDiscloseDistantIslands() {
        var source = index(claim("Hungary", 16, 0, new ClaimGeometry.Cell(0, 0), new ClaimGeometry.Cell(1, 0), new ClaimGeometry.Cell(2, 0), new ClaimGeometry.Cell(100, -200)));
        var memory = new ClaimKnowledge(); memory.observe(HOME, source.observe(HOME), 1);
        var other = new ClaimKnowledge.Chunk(WORLD, 2, 0); memory.observe(other, source.observe(other), 2);
        var c = visible(memory, MapMode.KINGDOMS).get(0);
        assertEquals(Set.of(new ClaimGeometry.Cell(0, 0), new ClaimGeometry.Cell(2, 0)), c.cells());
        assertEquals(2, ClaimGeometry.buildBounded(c.cells(), c.cellSize()).size());
    }
    @Test void negativeBlockCoordinatesUseFloorDivision() {
        assertEquals(new ClaimKnowledge.Chunk(WORLD, -1, -2), ClaimKnowledge.Chunk.at(WORLD, -1, -17));
        var source = index(claim("Hungary", 16, 0, new ClaimGeometry.Cell(-1, -2)));
        var chunk = ClaimKnowledge.Chunk.at(WORLD, -1, -17);
        var memory = new ClaimKnowledge(); memory.observe(chunk, source.observe(chunk), 1);
        assertEquals(256, area(visible(memory, MapMode.KINGDOMS)));
    }
    @Test void nonstandardTownyCellsAreClippedToExactlyOneMinecraftChunk() {
        for (int size : List.of(1, 2, 3, 4, 8, 15, 16, 17, 31, 32, 1024)) for (int sign : List.of(-1, 0)) {
            var source = index(claim("Hungary", size, 0, new ClaimGeometry.Cell(sign, sign)));
            for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) {
                var chunk = new ClaimKnowledge.Chunk(WORLD, cx, cz); var memory = new ClaimKnowledge();
                memory.observe(chunk, source.observe(chunk), 1);
                int width = Math.max(0, Math.min(cx * 16 + 16, sign * size + size) - Math.max(cx * 16, sign * size));
                int height = Math.max(0, Math.min(cz * 16 + 16, sign * size + size) - Math.max(cz * 16, sign * size));
                assertEquals(width * height, area(visible(memory, MapMode.KINGDOMS)), "size=" + size + " chunk=" + chunk);
            }
        }
    }
    @Test void markersAreClippedAndRemoteCapitalsAreNotRevealed() {
        var near = claim("Capital", 1, 4, new ClaimGeometry.Cell(15, 15), new ClaimGeometry.Cell(16, 15));
        var far = new Claims("trade/far", "Hidden trade centre", WORLD, 0x00eeaa, 1, Set.of(new ClaimGeometry.Cell(9999, 9999)), "Hidden ruler", 4);
        var source = index(near, far); var memory = new ClaimKnowledge(); memory.observe(HOME, source.observe(HOME), 1);
        for (MapMode mode : MapMode.values()) {
            assertEquals(1, visible(memory, mode).size()); assertEquals(1, area(visible(memory, mode)));
        }
    }
    @Test void dimensionsDoNotShareKnowledge() {
        var memory = new ClaimKnowledge(); var source = index(claim("Hungary", 16, 0, new ClaimGeometry.Cell(0, 0)));
        var nether = new ClaimKnowledge.Chunk("minecraft:the_nether", 0, 0);
        memory.observe(nether, source.observe(nether), 1); assertTrue(visible(memory, MapMode.KINGDOMS).isEmpty());
        memory.observe(HOME, source.observe(HOME), 2); assertEquals(1, visible(memory, MapMode.KINGDOMS).size());
        assertTrue(memory.at(nether).fragments().isEmpty());
    }
    @Test void unchangedVisitsDoNotRebuildGeometryOrDirtyTheStore() {
        var memory = new ClaimKnowledge(); var source = index(claim("Hungary", 16, 0, new ClaimGeometry.Cell(0, 0)));
        memory.observe(HOME, source.observe(HOME), 1); memory.saved();
        assertFalse(memory.observe(HOME, source.observe(HOME), 2)); assertFalse(memory.dirty()); assertEquals(1, memory.revision());
    }
    @Test void invalidObservationsCannotSmuggleCellsFromAnotherChunkOrDimension() {
        assertNull(ClaimKnowledge.Chunk.tryAt("minecraft:", 0, 0));
        assertNull(ClaimKnowledge.Chunk.tryAt("minecraft:overworld", Integer.MIN_VALUE, 0));
        assertNull(ClaimKnowledge.Chunk.tryAt("minecraft:overworld", 30_000_001, 0));
        assertNotNull(ClaimKnowledge.Chunk.tryAt(WORLD, -30_000_000, 30_000_000));
        var label = new ClaimKnowledge.Label(MapMode.KINGDOMS, "id", "Name", WORLD, 1, 16, "detail", 0);
        assertThrows(IllegalArgumentException.class, () -> new ClaimKnowledge.Observation(HOME, 1, List.of(new ClaimKnowledge.Fragment(label, Set.of(new ClaimGeometry.Cell(1, 0))))));
        assertThrows(IllegalArgumentException.class, () -> new ClaimKnowledge.Observation(new ClaimKnowledge.Chunk("minecraft:the_nether", 0, 0), 1, List.of(new ClaimKnowledge.Fragment(label, Set.of(new ClaimGeometry.Cell(0, 0))))));
    }
    @Test void tooltipQueriesReadHistoryWithoutDiscoveringOrRefreshingAnything() {
        var memory = new ClaimKnowledge(); var source = index(claim("Old Hungary", 16, 0, new ClaimGeometry.Cell(100, -200)));
        memory.observe(HUNGARY, source.observe(HUNGARY), 1); var before = memory.snapshot();
        assertEquals("Old Hungary", memory.labelsAt(HUNGARY, MapMode.KINGDOMS, 1601, -3199).get(0).name());
        assertTrue(memory.labelsAt(HOME, MapMode.KINGDOMS, 1, 1).isEmpty());
        assertTrue(memory.labelsAt(HUNGARY, MapMode.KINGDOMS, 1, 1).isEmpty());
        assertEquals(before, memory.snapshot()); assertEquals(1, memory.revision());
    }
    @Test void clickingBesideAMarkerDoesNotReturnItsDetails() {
        var memory = new ClaimKnowledge(); var source = index(claim("Capital", 1, 4, new ClaimGeometry.Cell(15, 15)));
        memory.observe(HOME, source.observe(HOME), 1);
        assertEquals("Capital", memory.labelsAt(HOME, MapMode.HIERARCHY, 15, 15).get(0).name());
        assertTrue(memory.labelsAt(HOME, MapMode.HIERARCHY, 14, 15).isEmpty());
    }
}
