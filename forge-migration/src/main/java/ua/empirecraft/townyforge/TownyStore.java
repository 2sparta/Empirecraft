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
        current = Files.exists(stateFile) ? TownySnapshot.read(stateFile) : TownySnapshot.load(world);
    }

    synchronized TownySnapshot snapshot() {
        return current;
    }

    synchronized <T> T change(Function<Source, T> operation) throws IOException {
        if (!current.editable()) {
            throw new IOException("Recreate the import with the current Towny migrator before editing");
        }
        Source source = current.editableCopy();
        T result = operation.apply(source);
        TownySnapshot updated = new TownySnapshot(source);
        Path temporary = Files.createTempFile(stateFile.getParent(), ".towny-state-", ".tmp");
        try {
            try (var out = Files.newBufferedWriter(temporary)) {
                TownySnapshot.JSON.toJson(source, out);
            }
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            current = updated;
        } finally {
            Files.deleteIfExists(temporary);
        }
        return result;
    }
}
