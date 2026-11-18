package net.fretux.knockedback;
import java.util.concurrent.ThreadLocalRandom;
public final class BleedoutPolicy {
    public static int duration() { return ThreadLocalRandom.current().nextInt(1000, 2401); }
    public static float darkness(int ticks) { return ticks > 0 && ticks <= 200 ? Math.min(1f, (200 - ticks) / 40f) : 0f; }
    public static boolean heartbeat(int ticks) { return ticks > 0 && ticks <= 300; }
}
