package ua.empirecraft.calendar;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Serene Seasons synchronizes only the repeating cycle; carry its persistent year separately. */
public final class CalendarNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("empirecraft_calendar", "year"), () -> "1", "1"::equals, "1"::equals);
    private static final Map<ResourceLocation, Long> CLIENT_YEARS = new HashMap<>();
    private CalendarNetwork() {}
    public static void init() {
        CHANNEL.registerMessage(0, YearSync.class, YearSync::encode, YearSync::decode, CalendarNetwork::receive,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public record YearSync(ResourceLocation dimension, long year) {
        public YearSync { if (year < 1) throw new IllegalArgumentException("Invalid calendar year"); }
        public static void encode(YearSync packet, FriendlyByteBuf buffer) {
            buffer.m_130085_(packet.dimension); buffer.writeLong(packet.year);
        }
        public static YearSync decode(FriendlyByteBuf buffer) { return new YearSync(buffer.m_130281_(), buffer.readLong()); }
    }
    private static void receive(YearSync packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> CLIENT_YEARS.put(packet.dimension, packet.year));
        context.setPacketHandled(true);
    }
    public static long clientYear(ResourceLocation dimension) { return CLIENT_YEARS.getOrDefault(dimension, 1L); }
    public static void unload(ResourceLocation dimension) { CLIENT_YEARS.remove(dimension); }
    public static void sync(ServerPlayer player) {
        if (player.f_8906_ == null || !CHANNEL.isRemotePresent(player.f_8906_.f_9742_)) return;
        var level = (ServerLevel)player.m_9236_();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new YearSync(level.m_46472_().m_135782_(), WallClockCalendar.year(level)));
    }
    public static void sync(ServerLevel calendar, long year) {
        for (var level : calendar.m_7654_().m_129785_()) {
            if (WallClockCalendar.calendarLevel(level) != calendar) continue;
            for (var player : level.m_6907_())
                if (player.f_8906_ != null && CHANNEL.isRemotePresent(player.f_8906_.f_9742_))
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new YearSync(level.m_46472_().m_135782_(), year));
        }
    }
}
