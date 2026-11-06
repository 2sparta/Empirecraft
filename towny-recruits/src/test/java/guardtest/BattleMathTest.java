package guardtest;

import ua.townyrecruits.tactics.*;
import ua.townyrecruits.tactics.TacticalOrder.*;

public final class BattleMathTest {
    public static void main(String[] args) {
        for (double distance : new double[]{2, 8, 16, 32, 48}) for (double velocity : new double[]{-0.2, 0, 0.2, 0.6}) {
            double time = BattleMath.intercept(distance, 0, 0, 0, 0, velocity, 2.6);
            assert Math.abs(Math.hypot(distance, velocity * time) - 2.6 * time) < 1e-7;
            double lead = velocity * time * 0.81;
            assert velocity == 0 || Math.abs(lead) < Math.abs(velocity * time) : "Leading must leave room for error";
            var arrow = BattleMath.arrow(distance, 1.5, lead, 2.6);
            assert arrow != null;
            double x = 0, y = 0, z = 0, vx = arrow.x(), vy = arrow.y(), vz = arrow.z();
            for (int tick = 0; tick < arrow.ticks(); tick++) { x += vx; y += vy; z += vz; vx *= 0.99; vy = vy * 0.99 - 0.05; vz *= 0.99; }
            assert Math.abs(x - distance) < 1e-7 && Math.abs(y - 1.5) < 1e-7 && Math.abs(z - lead) < 1e-7;
        }
        assert BattleMath.intercept(10000, 0, 0, 100, 0, 0, 2) <= 35;
        assert BattleMath.arrow(10000, 0, 0, 2.6) == null;
        for (int i = -100; i <= 100; i++) {
            double[] p = BattleMath.clamp(i, i * 0.7, 4);
            assert Math.hypot(p[0], p[1]) <= 4.0000001 : "Hold front escaped its pursuit leash";
        }
        for (int sign : new int[]{-1, 1}) {
            double[] first = BattleMath.orbit(0.3, 16, sign, 0, 8);
            double[] last = BattleMath.orbit(0.3, 16, sign, 8, 8);
            assert Math.hypot(first[0] + last[0], first[1] + last[1]) < 1e-7;
            for (int i = 0; i <= 8; i++) { double[] p = BattleMath.orbit(0.3, 16, sign, i, 8); assert Math.abs(Math.hypot(p[0], p[1]) - 16) < 1e-7; }
        }
        for (int count = 1; count <= 128; count++) for (double width : new double[]{0.6, 1, 3}) {
            var wedge = TacticalMath.layout(Formation.CAVALRY_WEDGE, count, 2.2, width, 1.4);
            for (int i = 0; i < count; i++) for (int j = i + 1; j < count; j++)
                assert Math.hypot(wedge.get(i).right() - wedge.get(j).right(), wedge.get(i).forward() - wedge.get(j).forward()) > 1.4;
            var passage = TacticalMath.layout(Formation.PASSAGE, count, 0.85, width, 0.6);
            for (var p : passage) assert Math.abs(p.right()) > 1 : "The retreat corridor must remain open even for odd or tiny groups";
        }
        for (Speed speed : Speed.values()) assert BattleMath.speed(speed, true) <= 0.5;
        assert BattleMath.rank(4, 8, Formation.DOUBLE_LINE) == 1 && BattleMath.rank(3, 8, Formation.DOUBLE_LINE) == 0;
        // UUID selection interleaves the ranks. March/KEEP must retain the physical rows
        // instead of reclassifying them as a square block after movement.
        assert java.util.Arrays.equals(BattleMath.ranks(new double[]{0.05, -1, 0, -0.95, 0.1, -1.05}), new int[]{0, 1, 0, 1, 0, 1});
        System.out.println("PASS interception and imperfect lead, discrete arrow impacts, strict pursuit radius, semicircle continuity, cavalry clearance, retreat corridors for 1–128 recruits and every speed");
    }
}
