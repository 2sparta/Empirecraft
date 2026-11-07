import java.util.*;
import ua.empirecraft.locks.PickingSession;

public final class SessionTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (int length : new int[]{1, 9, 20}) {
            var session = new PickingSession(length, 0, new Random(238));
            check(session.remaining(100) == 0, "First attempt has no artificial wait");
            check(session.attempt(-1, 100) == PickingSession.Outcome.INVALID, "Negative pin rejected");
            check(session.attempt(length, 100) == PickingSession.Outcome.INVALID, "Oversized pin rejected");
            long tick = 100;
            var solved = new ArrayList<Integer>();
            for (int index = 0; index < length; index++) {
                boolean found = false;
                for (int candidate = 0; candidate < length; candidate++) {
                    if (solved.contains(candidate)) continue;
                    for (int pin : solved) {
                        check(session.attempt(pin, tick) == PickingSession.Outcome.CORRECT, "Remembered prefix remains usable after reset"); tick += 10;
                    }
                    var outcome = session.attempt(candidate, tick);
                    check(session.attempt(candidate, tick) == (session.finished() ? PickingSession.Outcome.FINISHED : PickingSession.Outcome.BUSY), "Repeated immediate attempt does not advance");
                    check(session.remaining(tick) == 10, "Server cooldown enforced"); tick += 10;
                    if (outcome == PickingSession.Outcome.CORRECT) { solved.add(candidate); found = true; break; }
                    check(outcome == PickingSession.Outcome.WRONG && session.progress() == 0, "Every mistake resets the entire prefix");
                }
                check(found, "Combination can be discovered by a player");
                // Start the next search from a deliberate error so the remembered prefix must be replayed.
                if (index + 1 < length) {
                    check(session.attempt(solved.get(0), tick) == PickingSession.Outcome.WRONG, "Already set pin cannot count twice"); tick += 10;
                }
            }
            check(session.finished(), "All pins open the lock");
        }
        var slow = new PickingSession(9, 3, new Random(25));
        slow.attempt(0, 100);
        check(slow.attempt(1, 115) == PickingSession.Outcome.BUSY && slow.remaining(115) == 1, "Complexity III extends the pause");
        check(PickingSession.wear(0, 0) == 4 && PickingSession.wear(3, 3) == 16, "Enchantments increase cost");
        for (int bad : new int[]{0, 21, Integer.MAX_VALUE}) {
            try { new PickingSession(bad, 0, new Random()); throw new AssertionError("Invalid length accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        System.out.println("STEEL_SESSION_TEST_PASS checks=" + checks);
    }
}
