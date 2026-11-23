package ua.empirecraft.townyforge;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;
import ua.empirecraft.townyforge.TownySnapshot.Source;

final class TownyStore {
    private final Path stateFile;
    private TownySnapshot current;

    TownyStore(Path world) throws IOException {
        stateFile = world.resolve("empirecraft/towny-state.json");
        if (Files.exists(stateFile)) {
            current = TownySnapshot.read(stateFile);
        } else if (Files.exists(world.resolve("empirecraft/towny-import.json"))) {
            current = TownySnapshot.load(world);
        } else {
            Files.createDirectories(stateFile.getParent());
            var source = TownySnapshot.fresh();
            current = new TownySnapshot(source);
            persist(source);
        }
    }

    synchronized TownySnapshot snapshot() {
        return current;
    }

    synchronized void collectDaily(long epochSeconds) throws IOException {
        if (!current.editable() || !current.settingEnabled("economy.using_economy")
                || !current.settingEnabled("economy.daily_taxes.enabled")) return;
        var settings = current.settings();
        long cycle = TownyDaily.cycle(settings, epochSeconds);
        String previous = settings.get(TownyDaily.LAST_CYCLE);
        if (previous != null && cycle <= Long.parseLong(previous)) return;
        change(data -> {
            if (previous != null) new TownyDaily(data).run();
            data.settings().put(TownyDaily.LAST_CYCLE, Long.toString(cycle));
            return null;
        });
    }

    synchronized <T> T change(Function<Source, T> operation) throws IOException {
        if (!current.editable()) {
            throw new IOException("Recreate the import with the current Towny migrator before editing");
        }
        Source source = current.editableCopy();
        T result = operation.apply(source);
        TownySnapshot updated = new TownySnapshot(source);
        persist(source);
        current = updated;
        return result;
    }

    private void persist(Source source) throws IOException {
        Path temporary = Files.createTempFile(stateFile.getParent(), ".towny-state-", ".tmp");
        try {
            try (var out = Files.newBufferedWriter(temporary)) {
                TownySnapshot.JSON.toJson(source, out);
            }
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
