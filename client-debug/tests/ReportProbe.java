package ua.empirecraft.clientdebug;

import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class ReportProbe {
    public static void main(String[] args) throws Exception {
        Path reports = Path.of(args[0]);
        var timings = new Timings();
        for (int i = 0; i < 98; i++) timings.add(10_000_000);
        timings.add(80_000_000);
        timings.add(400_000_000);
        var summary = timings.drain();
        require(summary.count() == 100 && summary.p95() == 10 && summary.p99() == 80, "percentiles");
        require(summary.max() == 400 && summary.over50() == 2 && summary.over100() == 1, "stalls");
        require(timings.drain().count() == 0, "window reset");

        var session = new Session(FlightProfile.start(Duration.ofSeconds(30)), null);
        measure(session);
        var exported = session.save(reports, "probe complete");
        require(!exported.metricsOnly(), "JFR export");
        var csv = Files.readAllLines(exported.path().resolve("metrics.csv"));
        require(csv.size() == 2, "CSV rows");
        require(csv.get(0).split(",").length == csv.get(1).split(",").length, "CSV shape");
        require(csv.get(1).contains("world,true,12,34"), "world counters");
        require(Files.readString(exported.path().resolve("summary.txt")).contains("Top Java methods"), "CPU summary");
        try (var file = new RecordingFile(exported.path().resolve("profile.jfr"))) {
            boolean slowFrame = false;
            boolean slowTick = false;
            while (file.hasMoreEvents()) {
                var event = file.readEvent();
                if (!event.getEventType().getName().equals("empirecraft.SlowClientOperation")) continue;
                var operation = event.getString("operation");
                require(event.getDuration("elapsed").toMillis() >= 50, "duration metadata");
                slowFrame |= operation.equals("Frame interval");
                slowTick |= operation.equals("Client tick");
            }
            require(slowFrame && slowTick, "recorded frame and tick stalls");
        }

        var fallback = new Session(null, "JFR unavailable in probe");
        measure(fallback);
        var metrics = fallback.save(reports, "fallback");
        require(metrics.metricsOnly() && Files.isRegularFile(metrics.path().resolve("metrics.csv")), "metrics fallback");
        require(!Files.exists(metrics.path().resolve("profile.jfr")), "fallback has no JFR");
        require(!exported.path().equals(metrics.path()), "unique session directories");
        System.out.println("PASS client timing, CSV, JFR stall events, summary and fallback");
    }

    private static void measure(Session session) {
        long start = session.started;
        session.renderStart(start);
        session.renderEnd(start + 15_000_000);
        session.renderStart(start + 80_000_000);
        session.renderEnd(start + 95_000_000);
        session.tickStart(start + 100_000_000);
        session.tickEnd(start + 160_000_000);
        session.sample(start + 1_000_000_000, new Session.Context("world", true, 12, 34));
    }

    private static void require(boolean condition, String problem) {
        if (!condition) throw new AssertionError(problem);
    }
}
