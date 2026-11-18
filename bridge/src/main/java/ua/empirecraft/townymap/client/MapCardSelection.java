package ua.empirecraft.townymap.client;

import java.util.UUID;
import ua.empirecraft.townymap.MapHistoryNetwork;

/** A map card is selected only after a matching reply provides visible entries. */
final class MapCardSelection {
    private MapHistoryNetwork.Query pending;
    private MapHistoryNetwork.Query active;

    void select(String dimension,int x,int z) {
        pending = new MapHistoryNetwork.Query(UUID.randomUUID().toString(),dimension,x,z);
    }

    MapHistoryNetwork.Query pending() { return pending; }
    MapHistoryNetwork.Query active() { return active; }

    boolean accept(MapHistoryNetwork.Reply reply) {
        if (pending == null || !pending.request().equals(reply.request())) return false;
        var target = pending;
        pending = null;
        if (reply.status() != MapHistoryNetwork.Status.KNOWN || reply.entries().isEmpty()) return false;
        active = target;
        return true;
    }

    void cancel() { pending = null; }
    void clear() { pending = active = null; }
}
