package ua.empirecraft.townymap;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.*;
import java.util.function.Supplier;

/** A map click reads only the authenticated sender's saved observations. */
public final class MapHistoryNetwork {
    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("empirecraft", "map_history"), () -> VERSION,
            v -> VERSION.equals(v) || NetworkRegistry.ABSENT.equals(v),
            v -> VERSION.equals(v) || NetworkRegistry.ABSENT.equals(v));
    private MapHistoryNetwork() {}
    public static void init() {
        CHANNEL.registerMessage(0, Query.class, Query::encode, Query::decode, MapHistoryNetwork::query,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Reply.class, Reply::encode, Reply::decode, MapHistoryNetwork::reply,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public record Query(String request, String dimension, int x, int z) {
        static void encode(Query query, FriendlyByteBuf buffer) { buffer.writeUtf(query.request, 40); buffer.writeUtf(query.dimension, 256); buffer.writeInt(query.x); buffer.writeInt(query.z); }
        static Query decode(FriendlyByteBuf buffer) { return new Query(buffer.readUtf(40), buffer.readUtf(256), buffer.readInt(), buffer.readInt()); }
    }
    public enum Status { UNKNOWN, EMPTY, KNOWN }
    public record Entry(String name, String detail, String profile) {
        public Entry(String name, String detail) { this(name, detail, ""); }
        public Entry { profile = profile == null ? "" : profile; }
    }
    public record Reply(String request, Status status, List<Entry> entries) {
        public Reply { entries = List.copyOf(entries); if (entries.size() > 32) throw new IllegalArgumentException("Too many map entries"); }
        static void encode(Reply reply, FriendlyByteBuf buffer) {
            buffer.writeUtf(reply.request, 40); buffer.writeEnum(reply.status); buffer.writeVarInt(reply.entries.size());
            for (Entry entry : reply.entries) { buffer.writeUtf(limit(entry.name, 512), 512); buffer.writeUtf(limit(entry.detail, 8192), 8192); buffer.writeUtf(limit(entry.profile, 32768), 32768); }
        }
        static Reply decode(FriendlyByteBuf buffer) {
            String request = buffer.readUtf(40); Status status = buffer.readEnum(Status.class); int count = buffer.readVarInt();
            if (count < 0 || count > 32) throw new IllegalArgumentException("Invalid map entry count");
            var entries = new ArrayList<Entry>();
            for (int i = 0; i < count; i++) entries.add(new Entry(buffer.readUtf(512), buffer.readUtf(8192), buffer.readUtf(32768)));
            return new Reply(request, status, entries);
        }
        private static String limit(String text, int length) { return text.length() <= length ? text : text.substring(0, length - 1) + "…"; }
    }
    private static void query(Query query, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get(); var player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            Reply reply = BridgeService.describe(player, query);
            if (reply != null && CHANNEL.isRemotePresent(player.connection.connection))
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), reply);
        });
        context.setPacketHandled(true);
    }
    private static void reply(Reply reply, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ua.empirecraft.townymap.client.StrategyMapUi.receive(reply)));
        context.setPacketHandled(true);
    }
}
