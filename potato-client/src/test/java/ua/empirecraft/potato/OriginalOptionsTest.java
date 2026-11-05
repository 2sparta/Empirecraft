package ua.empirecraft.potato;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class OriginalOptionsTest {
    @Test void savingIsScopedAndNested() {
        assertFalse(OriginalOptions.active());
        try(var first=OriginalOptions.read()) {
            assertTrue(OriginalOptions.active());try(var second=OriginalOptions.read()) { assertTrue(OriginalOptions.active()); }
            assertTrue(OriginalOptions.active());
        }
        assertFalse(OriginalOptions.active());
    }
    @Test void serializerFailureCannotDisableTheProfileAfterSave() {
        assertThrows(IllegalStateException.class,()->{try(var ignored=OriginalOptions.read()){throw new IllegalStateException("disk error");}});
        assertFalse(OriginalOptions.active());
    }
    @Test void aSaveOnOneThreadDoesNotChangeChunkWorkersOrAnotherClientTask() throws Exception {
        var pool=Executors.newSingleThreadExecutor();
        try(var ignored=OriginalOptions.read()) { try { assertFalse(pool.submit(OriginalOptions::active).get()); } finally { pool.shutdownNow(); } }
        assertFalse(OriginalOptions.active());
    }
    @Test void closingTwiceCannotBreakAnOuterSave() {
        try(var outer=OriginalOptions.read()) { var inner=OriginalOptions.read();inner.close();inner.close();assertTrue(OriginalOptions.active()); }
        assertFalse(OriginalOptions.active());
    }
}
