package ua.empirecraft.clientdebug;

import com.sun.management.OperatingSystemMXBean;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Session {
    static final Duration LIMIT = Duration.ofMinutes(5);
    private static final long SECOND = 1_000_000_000L;
    private static final DateTimeFormatter FILE_DATE =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final String TIMING_FIELDS = "%1$s_count,%1$s_mean_ms,%1$s_p50_ms,%1$s_p95_ms,%1$s_p99_ms,%1$s_max_ms,%1$s_over_33ms,%1$s_over_50ms,%1$s_over_100ms";
    private static final String HEADER = "elapsed_s,window_s,fps,scene,focused,chunks,entities,heap_used_mib,heap_max_mib,process_cpu_pct,system_cpu_pct,gc_count,gc_ms,"
            + String.format(Locale.ROOT, TIMING_FIELDS, "frame") + ","
            + String.format(Locale.ROOT, TIMING_FIELDS, "render") + ","
            + String.format(Locale.ROOT, TIMING_FIELDS, "tick");

    final long started = System.nanoTime();
    private final Instant timestamp = Instant.now();
    private final String gameThread = Thread.currentThread().getName();
    private final List<Row> rows = new ArrayList<>(301);
    private final List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
    private final OperatingSystemMXBean cpu = ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os ? os : null;
    private final Timings frames = new Timings(), render = new Timings(), ticks = new Timings();
    private final FlightProfile flight;
    private final String jfrStartWarning;
    private long window = started;
    private long frameStart, tickStart;
    private long lastGcCount = gcTotal(false), lastGcMs = gcTotal(true);

    Session(FlightProfile flight, String jfrStartWarning) {
        this.flight = flight;
        this.jfrStartWarning = jfrStartWarning;
    }

    boolean hasFlightProfile() { return flight != null; }
    boolean sampleDue(long now) { return now - window >= SECOND; }

    void renderStart(long now) {
        if (frameStart != 0) measure(frames, "Frame interval", now - frameStart);
        frameStart = now;
    }

    void renderEnd(long now) {
        if (frameStart != 0) measure(render, "Render pass", now - frameStart);
    }

    void tickStart(long now) { tickStart = now; }

    void tickEnd(long now) {
        if (tickStart != 0) measure(ticks, "Client tick", now - tickStart);
        tickStart = 0;
    }

    private void measure(Timings timings, String operation, long nanos) {
        timings.add(nanos);
        if (flight != null && nanos >= 50_000_000) flight.slow(operation, nanos);
    }

    void sample(long now, Context current) {
        if (now <= window) return;
        long count = gcTotal(false), gcMs = gcTotal(true);
        var runtime = Runtime.getRuntime();
        rows.add(new Row((now - started) / (double) SECOND, (now - window) / (double) SECOND,
                current, mib(runtime.totalMemory() - runtime.freeMemory()), mib(runtime.maxMemory()),
                percent(cpu == null ? -1 : cpu.getProcessCpuLoad()), percent(cpu == null ? -1 : cpu.getCpuLoad()),
                delta(count, lastGcCount), delta(gcMs, lastGcMs), frames.drain(), render.drain(), ticks.drain()));
        window = now;
        lastGcCount = count;
        lastGcMs = gcMs;
    }

    private long gcTotal(boolean millis) {
        long total = 0;
        boolean supported = false;
        for (var collector : collectors) {
            long value = millis ? collector.getCollectionTime() : collector.getCollectionCount();
            if (value >= 0) {
                total += value;
                supported = true;
            }
        }
        return supported ? total : -1;
    }

    private static long delta(long now, long previous) { return now < 0 || previous < 0 ? -1 : now - previous; }
    private static double mib(long bytes) { return bytes / 1048576.0; }
    private static double percent(double load) { return load < 0 ? Double.NaN : load * 100; }

    Export save(Path directory, String reason) throws IOException {
        try (FlightProfile profile = flight) {
            Files.createDirectories(directory);
            Path folder = Files.createTempDirectory(directory, FILE_DATE.format(timestamp) + "-");
            try (var csv = new PrintWriter(Files.newBufferedWriter(folder.resolve("metrics.csv")))) {
                csv.println(HEADER);
                for (Row row : rows) row.write(csv);
                if (csv.checkError()) throw new IOException("Could not write metrics.csv");
            }

            String warning = jfrStartWarning;
            Path jfr = folder.resolve("profile.jfr");
            if (profile != null) {
                try {
                    profile.save(jfr);
                } catch (IOException | RuntimeException e) {
                    Files.deleteIfExists(jfr);
                    warning = e.getClass().getSimpleName();
                }
            }

            try (var summary = new PrintWriter(Files.newBufferedWriter(folder.resolve("summary.txt")))) {
                summary.println("Empirecraft client diagnostics");
                summary.println("Started (UTC): " + timestamp);
                summary.println("Stopped: " + reason);
                summary.printf(Locale.ROOT, "Duration: %.2f s, %d metric windows%n", (window - started) / (double) SECOND, rows.size());
                summary.println("Java: " + Runtime.version().feature() + ", processors: " + Runtime.getRuntime().availableProcessors());
                summary.println("Frame intervals include the FPS limit and waiting. Render times measure the Forge render hook on the CPU, not GPU time.");
                summary.println("Percentiles are rounded down to 0.5 ms and capped at 250 ms; maxima are not capped. NaN or -1 means unavailable.");
                summary.println();
                if (warning == null) {
                    try {
                        FlightProfile.summarize(jfr, gameThread, summary);
                    } catch (IOException | RuntimeException e) {
                        summary.println("Couldn't summarize the recording: " + e.getClass().getSimpleName());
                    }
                } else {
                    summary.println("No JFR recording: " + warning);
                }
                if (summary.checkError()) throw new IOException("Could not write summary.txt");
            }
            return new Export(folder, warning != null);
        }
    }

    record Context(String scene, boolean focused, int chunks, int entities) {}
    record Export(Path path, boolean metricsOnly) {}

    private record Row(double elapsed, double seconds, Context context, double heap, double heapMax,
                       double processCpu, double systemCpu, long gcCount, long gcMs,
                       Timings.Sample frame, Timings.Sample render, Timings.Sample tick) {
        void write(PrintWriter csv) {
            csv.printf(Locale.ROOT, "%.3f,%.3f,%.2f,%s,%b,%d,%d,%.1f,%.1f,%.1f,%.1f,%d,%d",
                    elapsed, seconds, frame.count() / seconds, context.scene(), context.focused(),
                    context.chunks(), context.entities(), heap, heapMax, processCpu, systemCpu, gcCount, gcMs);
            writeTiming(csv, frame);
            writeTiming(csv, render);
            writeTiming(csv, tick);
            csv.println();
        }

        private static void writeTiming(PrintWriter csv, Timings.Sample s) {
            csv.printf(Locale.ROOT, ",%d,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d",
                    s.count(), s.mean(), s.p50(), s.p95(), s.p99(), s.max(), s.over33(), s.over50(), s.over100());
        }
    }
}
