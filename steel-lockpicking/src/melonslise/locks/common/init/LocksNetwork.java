package melonslise.locks.common.init;

import java.util.Optional;
import melonslise.locks.common.network.toclient.*;
import melonslise.locks.common.network.toserver.TryPinPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;
import ua.empirecraft.locks.PickPackets;

public final class LocksNetwork {
    public static final String PROTOCOL = "empirecraft-steel-picking-1";
    public static final SimpleChannel MAIN = NetworkRegistry.newSimpleChannel(new ResourceLocation("locks", "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private LocksNetwork() {}
    public static void register() {
        MAIN.registerMessage(0, AddLockablePacket.class, AddLockablePacket::encode, AddLockablePacket::decode, AddLockablePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        MAIN.registerMessage(1, AddLockableToChunkPacket.class, AddLockableToChunkPacket::encode, AddLockableToChunkPacket::decode, AddLockableToChunkPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        MAIN.registerMessage(2, RemoveLockablePacket.class, RemoveLockablePacket::encode, RemoveLockablePacket::decode, RemoveLockablePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        MAIN.registerMessage(3, UpdateLockablePacket.class, UpdateLockablePacket::encode, UpdateLockablePacket::decode, UpdateLockablePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        MAIN.registerMessage(4, TryPinPacket.class, TryPinPacket::encode, TryPinPacket::decode, TryPinPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        MAIN.registerMessage(5, TryPinResultPacket.class, TryPinResultPacket::encode, TryPinResultPacket::decode, TryPinResultPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        PickPackets.register(MAIN);
    }
}
