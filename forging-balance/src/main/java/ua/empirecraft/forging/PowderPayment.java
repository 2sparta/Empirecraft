package ua.empirecraft.forging;

import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

public final class PowderPayment {
    private PowderPayment() {}
    public static boolean pay(boolean creative, boolean client, int slots, IntPredicate containsPowder, IntConsumer consumeOne) {
        if (creative) return true;
        for (int slot = 0; slot < slots; slot++) {
            if (!containsPowder.test(slot)) continue;
            if (!client) consumeOne.accept(slot);
            return true;
        }
        return false;
    }
}
