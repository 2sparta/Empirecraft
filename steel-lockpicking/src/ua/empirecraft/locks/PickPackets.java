package ua.empirecraft.locks;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PickPackets {
    public record Attempt(int menu, UUID nonce, int sequence, int pin) {}
    public record Result(int menu, UUID nonce, int sequence, int pin, int progress, boolean correct,
                         boolean reset, boolean broken, boolean open, int cooldown, int durability) {}
    private PickPackets() {}
    public static void register(SimpleChannel channel) {
        channel.messageBuilder(Attempt.class, 6, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PickPackets::encodeAttempt).decoder(PickPackets::decodeAttempt)
                .consumerMainThread(PickPackets::handleAttempt).add();
        channel.messageBuilder(Result.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PickPackets::encodeResult).decoder(PickPackets::decodeResult)
                .consumerMainThread(PickPackets::handleResult).add();
    }
    public static void encodeAttempt(Attempt p, FriendlyByteBuf b) {
        b.writeVarInt(p.menu()); b.writeUUID(p.nonce()); b.writeVarInt(p.sequence()); b.writeByte(p.pin());
    }
    public static Attempt decodeAttempt(FriendlyByteBuf b) {
        return new Attempt(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readUnsignedByte());
    }
    public static void encodeResult(Result p, FriendlyByteBuf b) {
        b.writeVarInt(p.menu()); b.writeUUID(p.nonce()); b.writeVarInt(p.sequence()); b.writeByte(p.pin()); b.writeByte(p.progress());
        b.writeBoolean(p.correct()); b.writeBoolean(p.reset()); b.writeBoolean(p.broken()); b.writeBoolean(p.open());
        b.writeVarInt(p.cooldown()); b.writeVarInt(p.durability());
    }
    public static Result decodeResult(FriendlyByteBuf b) {
        return new Result(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readUnsignedByte(), b.readUnsignedByte(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt());
    }
    private static void handleAttempt(Attempt p, Supplier<NetworkEvent.Context> context) {
        var sender = context.get().getSender();
        if (sender != null && sender.containerMenu instanceof SteelPickingMenu menu) menu.receive(p);
        context.get().setPacketHandled(true);
    }
    private static void handleResult(Result p, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ua.empirecraft.locks.client.PickingClient.receive(p));
        context.get().setPacketHandled(true);
    }
}
