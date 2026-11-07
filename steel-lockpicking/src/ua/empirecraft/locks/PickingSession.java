package ua.empirecraft.locks;

import java.util.Random;

/** Server-only combination; never serialized to the client or derived from a key ID. */
public final class PickingSession {
    public enum Outcome { CORRECT, WRONG, BUSY, INVALID, FINISHED }
    private final int[] order;
    private final int cooldown;
    private int progress;
    private long nextTick = Long.MIN_VALUE;

    public PickingSession(int length, int complexity, Random random) {
        if (length < 1 || length > 20) throw new IllegalArgumentException("Invalid lock length");
        order = new int[length];
        for (int i = 0; i < length; i++) order[i] = i;
        for (int i = length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1), value = order[i]; order[i] = order[j]; order[j] = value;
        }
        cooldown = 10 + 2 * clamp(complexity);
    }

    public Outcome attempt(int pin, long tick) {
        if (pin < 0 || pin >= order.length) return Outcome.INVALID;
        if (progress == order.length) return Outcome.FINISHED;
        if (tick < nextTick) return Outcome.BUSY;
        nextTick = tick + cooldown;
        if (pin == order[progress]) { progress++; return Outcome.CORRECT; }
        progress = 0;
        return Outcome.WRONG;
    }

    public int progress() { return progress; }
    public int remaining(long tick) { return tick >= nextTick ? 0 : (int)Math.min(cooldown, nextTick - tick); }
    public boolean finished() { return progress == order.length; }
    public static int wear(int complexity, int sturdy) { return 4 + 2 * clamp(complexity) + 2 * clamp(sturdy); }
    private static int clamp(int level) { return Math.max(0, Math.min(3, level)); }
}
