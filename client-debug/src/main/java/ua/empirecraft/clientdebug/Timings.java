package ua.empirecraft.clientdebug;

import java.util.Arrays;

final class Timings {
    private static final long BIN_NS = 500_000;
    private final int[] bins = new int[501];
    private long total, max;
    private int count, over33, over50, over100;

    void add(long nanos) {
        if (nanos <= 0) return;
        bins[(int) Math.min(nanos / BIN_NS, bins.length - 1)]++;
        count++;
        total += nanos;
        max = Math.max(max, nanos);
        if (nanos > 33_333_333) over33++;
        if (nanos > 50_000_000) over50++;
        if (nanos > 100_000_000) over100++;
    }

    Sample drain() {
        var sample = new Sample(count, count == 0 ? Double.NaN : ms(total) / count,
                percentile(0.50), percentile(0.95), percentile(0.99),
                count == 0 ? Double.NaN : ms(max), over33, over50, over100);
        Arrays.fill(bins, 0);
        total = max = 0;
        count = over33 = over50 = over100 = 0;
        return sample;
    }

    private double percentile(double fraction) {
        if (count == 0) return Double.NaN;
        int remaining = (int) Math.ceil(count * fraction);
        for (int i = 0; i < bins.length; i++) {
            remaining -= bins[i];
            if (remaining <= 0) return ms(i * BIN_NS);
        }
        throw new IllegalStateException("Incomplete timing histogram");
    }

    static double ms(long nanos) { return nanos / 1_000_000.0; }

    record Sample(int count, double mean, double p50, double p95, double p99,
                  double max, int over33, int over50, int over100) {}
}
