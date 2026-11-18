package ua.empirecraft.townymap.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import ua.empirecraft.townymap.MapHistoryNetwork;
import static ua.empirecraft.townymap.MapHistoryNetwork.Status.*;
import java.util.List;

class MapCardSelectionTest {
    private static final String WORLD = "minecraft:overworld";

    @Test void onlyKnownNonEmptyRepliesSelectACard() {
        var cards = new MapCardSelection();
        for (var status : List.of(EMPTY,UNKNOWN,KNOWN)) {
            cards.select(WORLD, 0, 0);
            assertFalse(cards.accept(new MapHistoryNetwork.Reply(cards.pending().request(),status,List.of())));
            assertNull(cards.active());
            assertNull(cards.pending());
        }
        cards.select(WORLD, 16, 0);
        assertTrue(cards.accept(known(cards.pending().request())));
        assertEquals(16,cards.active().x());
        cards.select(WORLD, 32, 0);
        assertFalse(cards.accept(new MapHistoryNetwork.Reply(cards.pending().request(),EMPTY,List.of())));
        assertEquals(16,cards.active().x());
    }

    @Test void lateRepliesCannotOpenPanelsAfterAnotherClickOrDismissal() {
        var cards = new MapCardSelection();
        cards.select(WORLD,1,1);
        var late = known(cards.pending().request());
        cards.select(WORLD,2,2);
        assertFalse(cards.accept(late));
        var afterDismissal = known(cards.pending().request());
        cards.cancel();
        assertFalse(cards.accept(afterDismissal));
        assertNull(cards.active());
    }

    private static MapHistoryNetwork.Reply known(String request) {
        return new MapHistoryNetwork.Reply(request,KNOWN,List.of(new MapHistoryNetwork.Entry("Example","Details")));
    }
}
