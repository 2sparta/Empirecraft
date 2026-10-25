package guardtest;

import java.util.*;
import ua.empirecraft.forging.*;

public final class ArmorRulesTest {
    static void equal(Object expected, Object actual) { if (!Objects.equals(expected, actual)) throw new AssertionError(expected+" != "+actual); }
    public static void main(String[] args) {
        for (String quality : List.of("poor", "well", "expert", "perfect", "master")) {
            for (int tier : List.of(7, 8, 9)) equal(quality, ArmorRules.result(tier, quality, 0));
            equal("poor", ArmorRules.result(9, quality, 2));
            if (!quality.equals("master")) equal("poor", ArmorRules.result(9, quality, 1));
        }
        equal("well", ArmorRules.result(9, "master", 1));
        equal("well", ArmorRules.result(8, "master", 1));
        equal("poor", ArmorRules.result(8, "master", 2));
        equal("expert", ArmorRules.result(7, "master", 1));
        equal("well", ArmorRules.result(7, "master", 2));
        equal("poor", ArmorRules.result(7, "master", 3));
        // A starter blueprint still earns research after accurate forging, despite its Well output cap.
        equal("well", ArmorRules.result(9, "well", 0));
        equal("perfect", ArmorRules.accuracy(new ArmorRules.HitReport(4, 0, 0), 0.95, 0.7, 0.3));
        equal("expert", ArmorRules.accuracy(new ArmorRules.HitReport(3, 0, 1), 0.95, 0.7, 0.3));
        equal("poor", ArmorRules.accuracy(new ArmorRules.HitReport(0, 0, 0), 0.95, 0.7, 0.3));
        equal(9, ArmorRules.difficulty(9, "chest"));
        equal(9, ArmorRules.difficulty(4, "head"));
        equal(9, ArmorRules.difficulty(7, "legs"));
        equal(8, ArmorRules.difficulty(3, "feet"));
        for (int tier : List.of(7, 8, 9)) {
            assert ArmorRules.initialSpeed(tier, true) < ArmorRules.initialSpeed(tier, false);
            assert ArmorRules.maximumSpeed(tier, true) < ArmorRules.maximumSpeed(tier, false);
            equal(new ArmorRules.Game(tier, true, "master"), ArmorRules.Game.decode(new ArmorRules.Game(tier, true, "master").encode()));
        }
        assert ArmorRules.initialSpeed(7, false) < ArmorRules.initialSpeed(8, false);
        assert ArmorRules.initialSpeed(8, false) < ArmorRules.initialSpeed(9, false);
        var first = new ArmorRules.HitReport(1, 0, 0);
        assert first.follows(new ArmorRules.HitReport(0, 0, 0));
        assert !new ArmorRules.HitReport(2, 0, 0).follows(new ArmorRules.HitReport(0, 0, 0));
        assert !new ArmorRules.HitReport(2, 0, 0).follows(new ArmorRules.HitReport(0, 0, 1));
        equal(first, ArmorRules.HitReport.decode(first.encode()));
        for (String malformed : List.of("ecf1|-1|0|0", "ecf1|2147483647|1|0", "ecf1|1|0", "ecf1|1|0|0|1", "perfect")) equal(null, ArmorRules.HitReport.decode(malformed));
        UpgradeGraph<String> graph = new UpgradeGraph<>();
        graph.add("base5", 5); graph.add("middle6", 6); graph.add("final9", 9); graph.add("unrelated", 5);
        graph.upgrade("base5", "middle6"); graph.upgrade("middle6", "final9"); graph.upgrade("final9", "middle6");
        var ratings = graph.resolve();
        equal(9, ratings.get("base5")); equal(9, ratings.get("middle6")); equal(5, ratings.get("unrelated"));
        int[] inventory = {0, 2, 3};
        assert PowderPayment.pay(false, false, 3, slot -> inventory[slot] > 0, slot -> inventory[slot]--);
        equal(1, inventory[1]); equal(3, inventory[2]);
        assert PowderPayment.pay(false, true, 3, slot -> inventory[slot] > 0, slot -> { throw new AssertionError("Client consumed powder"); });
        equal(1, inventory[1]);
        assert !PowderPayment.pay(false, false, 3, slot -> false, slot -> { throw new AssertionError("Missing powder consumed"); });
        assert PowderPayment.pay(true, false, 3, slot -> false, slot -> { throw new AssertionError("Creative consumed powder"); });
        System.out.println("PASS armor quality boundaries, slot speeds, hit reports, transitive upgrades and cycles");
        System.out.println("PASS exactly one gunpowder per server shot; client prediction, missing powder and creative exemption");
    }
}
