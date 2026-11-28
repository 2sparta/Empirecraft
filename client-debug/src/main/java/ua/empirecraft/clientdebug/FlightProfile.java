package ua.empirecraft.clientdebug;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

final class FlightProfile implements AutoCloseable {
    private final Recording recording;

    private FlightProfile(Recording recording) { this.recording = recording; }

    static FlightProfile start(Duration duration) {
        if (!FlightRecorder.isAvailable()) throw new IllegalStateException("Java Flight Recorder is unavailable");
        Recording recording = new Recording();
        try {
            recording.setName("Empirecraft client diagnostics");
            recording.setToDisk(true);
            recording.setMaxSize(48L * 1024 * 1024);
            recording.setDuration(duration);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20));
            recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(40));
            recording.enable("jdk.ObjectAllocationSample").with("throttle", "100/s").withStackTrace();
            recording.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));
            recording.enable("jdk.GarbageCollection");
            recording.enable("jdk.GCPhasePause");
            recording.enable("jdk.GCHeapSummary");
            recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(20)).withStackTrace();
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(20)).withStackTrace();
            recording.enable("jdk.FileRead").withThreshold(Duration.ofMillis(20)).withStackTrace();
            recording.enable("jdk.FileWrite").withThreshold(Duration.ofMillis(20)).withStackTrace();
            recording.enable(SlowClientOperation.class);
            recording.start();
            return new FlightProfile(recording);
        } catch (RuntimeException e) {
            recording.close();
            throw e;
        }
    }

    void slow(String operation, long nanos) {
        var event = new SlowClientOperation();
        event.operation = operation;
        event.elapsed = nanos;
        event.commit();
    }

    void save(Path path) throws IOException {
        if (recording.getState() == RecordingState.RUNNING) recording.stop();
        recording.dump(path);
    }

    @Override public void close() { recording.close(); }

    static void summarize(Path path, String gameThread, PrintWriter output) throws IOException {
        var samples = new TreeMap<String, Map<String, Integer>>();
        try (var events = new RecordingFile(path)) {
            while (events.hasMoreEvents()) {
                var event = events.readEvent();
                if (!event.getEventType().getName().equals("jdk.ExecutionSample")) continue;
                var thread = event.getThread("sampledThread");
                var stack = event.getStackTrace();
                if (thread == null || stack == null || stack.getFrames().isEmpty()) continue;
                var method = stack.getFrames().get(0).getMethod();
                if (method == null) continue;
                String name = thread.getJavaName();
                String group = gameThread.equals(name) ? "Render/client thread"
                        : "Server thread".equals(name) ? "Integrated server thread" : "Other JVM threads";
                samples.computeIfAbsent(group, k -> new HashMap<>())
                        .merge(method.getType().getName() + "." + method.getName(), 1, Integer::sum);
            }
        }
        output.println("Top Java methods by CPU samples (top stack frame, not inclusive time):");
        if (samples.isEmpty()) output.println("No Java execution samples in this recording.");
        samples.forEach((group, methods) -> {
            output.println();
            output.println(group + " (" + methods.values().stream().mapToInt(Integer::intValue).sum() + " samples)");
            methods.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .limit(15).forEach(entry -> output.println(entry.getValue() + "  " + entry.getKey()));
        });
    }

    @Name("empirecraft.SlowClientOperation")
    @Label("Slow Minecraft client operation")
    @Category({"Empirecraft", "Client diagnostics"})
    @StackTrace(false)
    private static final class SlowClientOperation extends Event {
        @Label("Operation") String operation;
        @Label("Duration") @Timespan(Timespan.NANOSECONDS) long elapsed;
    }
}
