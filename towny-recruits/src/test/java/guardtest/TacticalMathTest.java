package guardtest;

import java.util.*;
import ua.townyrecruits.tactics.*;

public final class TacticalMathTest {
    public static void main(String[] args) {
        int cases = 0;
        for (var shape : TacticalOrder.Formation.values()) for (int count = 1; count <= 128; count++) {
            var layout = TacticalMath.layout(shape, count, 1.35);
            assert layout.size() == count;
            assert Math.abs(layout.stream().mapToDouble(TacticalMath.Offset::right).sum()) < 1e-8;
            assert Math.abs(layout.stream().mapToDouble(TacticalMath.Offset::forward).sum()) < 1e-8;
            Set<String> occupied = new HashSet<>();
            for (var slot : layout) {
                assert Double.isFinite(slot.right() + slot.forward() + slot.facing());
                assert occupied.add(Math.round(slot.right() * 10000) + ":" + Math.round(slot.forward() * 10000)) : shape + " overlapping slots";
            }
            cases++;
        }
        for (int count : new int[]{4, 5, 8, 19, 20, 65, 128}) {
            var square = TacticalMath.layout(TacticalOrder.Formation.SQUARE, count, 1.35);
            for (int i = 0; i < count; i++) assert square.get(i).facing() == (i % 4) * 90;
        }
        var wall = TacticalMath.layout(TacticalOrder.Formation.PIKE_WALL, 20, 0.85);
        assert wall.stream().map(TacticalMath.Offset::forward).distinct().count() == 2;
        for (float yaw : new float[]{-180,-90,-15,0,15,90,180,270}) {
            double[] p = TacticalMath.rotate(3.5, -2, yaw), back = TacticalMath.rotate(p[0], p[1], -yaw);
            assert Math.abs(back[0] - 3.5) < 1e-9 && Math.abs(back[1] + 2) < 1e-9;
        }
        assert Math.abs(TacticalMath.wrap(TacticalMath.turn(179, -179, 8)) + 179) < 1e-6;
        assert TacticalMath.turn(0, 90, 8) == 8;
        int reachable = 0, unreachable = 0;
        for (int distance = 4; distance < 60; distance++) for (int height = -8; height <= 8; height += 2) {
            var f = TacticalMath.flight(distance, height - 1.7, 3, 1.25, (double)0.04f, (double)0.99f, 100);
            if (f == null) { unreachable++; continue; }
            reachable++;
            double x = 0, y = 0, z = 0, vx = f.x(), vy = f.y(), vz = f.z();
            for (int i = 0; i < f.ticks(); i++) { x += vx; y += vy; z += vz; vx *= (double)0.99f; vy = vy * (double)0.99f - (double)0.04f; vz *= (double)0.99f; }
            assert Math.abs(x - distance) < 1e-7 && Math.abs(y - (height - 1.7)) < 1e-7 && Math.abs(z - 3) < 1e-7;
            assert f.x() * f.x() + f.y() * f.y() + f.z() * f.z() <= 1.25 * 1.25 + 1e-9;
        }
        assert reachable > 100 && unreachable > 100;
        assert TacticalMath.flight(100, 0, 0, 1.25, 0.04, 0.99, 100) == null;
        assert TacticalMath.flight(1, 0, 0, 1.25, 0.04, 0.99, 3) == null : "Fuse must constrain flight time";
        Random random = new Random(731); double radialMean = 0, mx = 0, mz = 0;
        for (int i = 0; i < 100000; i++) {
            double[] p = TacticalMath.disk(random.nextDouble(), random.nextDouble(), 2.5);
            double d2 = p[0] * p[0] + p[1] * p[1]; assert d2 <= 6.25;
            radialMean += d2; mx += p[0]; mz += p[1];
        }
        assert Math.abs(radialMean / 100000 - 3.125) < 0.04;
        assert Math.abs(mx / 100000) < 0.02 && Math.abs(mz / 100000) < 0.02;
        assert TacticalMath.cooling(17999, 18000) && !TacticalMath.cooling(18000, 18000);
        assert TacticalMath.bombRoom(0) == 1 && TacticalMath.bombRoom(1) == 0 && TacticalMath.bombRoom(64) == 0;
        System.out.println("PASS " + cases + " formation sizes; outward square, two-rank pike wall, rotation; " + reachable + " native-physics arcs; scatter distribution; 18000-tick boundary and shared bomb capacity");
    }
}
