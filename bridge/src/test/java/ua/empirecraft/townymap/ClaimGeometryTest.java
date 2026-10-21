package ua.empirecraft.townymap;

import org.junit.jupiter.api.Test;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ua.empirecraft.townymap.ClaimGeometry.*;

class ClaimGeometryTest {
    @Test void adjacentClaimsHaveOnlyOneOuterBorder() {
        var polygons = build(Set.of(new Cell(0, 0), new Cell(1, 0)), 16);
        assertEquals(1, polygons.size());
        assertEquals(4, polygons.get(0).outer().size());
        assertEquals(512, Math.abs(signedArea(polygons.get(0).outer())));
    }

    @Test void concaveBoundariesAndNegativeCoordinates() {
        check(Set.of(new Cell(-2, -2), new Cell(-1, -2), new Cell(-2, -1)), 16);
    }

    @Test void holesAndAnIslandInsideAHoleStaySeparate() {
        Set<Cell> cells = new HashSet<>();
        for (int z = 0; z < 5; z++) for (int x = 0; x < 5; x++)
            if (x == 0 || x == 4 || z == 0 || z == 4 || x == 2 && z == 2) cells.add(new Cell(x, z));
        var polygons = build(cells, 16);
        assertEquals(2, polygons.size());
        assertEquals(1, polygons.stream().mapToInt(p -> p.holes().size()).sum());
        check(cells, 16);
    }

    @Test void diagonalTouchDoesNotClaimTheGap() {
        check(Set.of(new Cell(0, 0), new Cell(1, 1), new Cell(2, 0)), 16);
    }

    @Test void customTownBlockSizeAndDistantIslands() {
        check(Set.of(new Cell(-234, 789), new Cell(1500, -800)), 32);
    }

    @Test void emptyClaimsDisappear() { assertTrue(build(Set.of(), 16).isEmpty()); }

    @Test void randomClaimsMatchTheirExactUnion() {
        Random random = new Random(349632);
        for (int run = 0; run < 200; run++) {
            Set<Cell> cells = new HashSet<>();
            for (int z = -8; z < 8; z++) for (int x = -8; x < 8; x++)
                if (random.nextBoolean()) cells.add(new Cell(x, z));
            check(cells, 16);
        }
    }

    @Test void tenThousandContiguousClaimsCollapseToFourPoints() {
        Set<Cell> cells = new HashSet<>();
        for (int z = 0; z < 100; z++) for (int x = 0; x < 100; x++) cells.add(new Cell(x, z));
        var polygons = build(cells, 16);
        assertEquals(1, polygons.size());
        assertEquals(4, polygons.get(0).outer().size());
    }

    @Test void thousandsOfHolesAreSplitWithinJourneyMapsPacketLimits() {
        Set<Cell> cells = new HashSet<>();
        for (int z = 0; z < 71; z++) for (int x = 0; x < 71; x++)
            if (x % 2 == 0 || z % 2 == 0) cells.add(new Cell(x, z));
        var polygons = buildBounded(cells, 16);
        assertTrue(polygons.size() > 1);
        assertTrue(polygons.stream().allMatch(p -> p.holes().size() <= 512
                && p.outer().size() + p.holes().stream().mapToInt(List::size).sum() <= 8192));
        double area = polygons.stream().mapToDouble(p -> Math.abs(signedArea(p.outer()))
                - p.holes().stream().mapToDouble(h -> Math.abs(signedArea(h))).sum()).sum();
        assertEquals((double) cells.size() * 16 * 16, area);
    }

    private static void check(Set<Cell> cells, int size) {
        Area expected = new Area();
        for (Cell cell : cells) expected.add(new Area(new Rectangle2D.Double(cell.x()*size, cell.z()*size, size, size)));
        Area actual = new Area();
        for (Polygon polygon : build(cells, size)) {
            Area shape = new Area(path(polygon.outer()));
            polygon.holes().forEach(h -> shape.subtract(new Area(path(h))));
            actual.add(shape);
        }
        actual.exclusiveOr(expected);
        assertTrue(actual.isEmpty(), "Generated boundary changes claimed territory");
    }
}
