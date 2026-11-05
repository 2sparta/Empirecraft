package ua.empirecraft.potato;

/** Saving reads stored preferences, never the temporary render preset. Finally-safe and nestable. */
public final class OriginalOptions implements AutoCloseable {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private final int previous;
    private boolean closed;
    private OriginalOptions() { previous = DEPTH.get(); DEPTH.set(previous + 1); }
    public static OriginalOptions read() { return new OriginalOptions(); }
    public static boolean active() { return DEPTH.get() != 0; }
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (previous == 0) DEPTH.remove(); else DEPTH.set(previous);
    }
}
