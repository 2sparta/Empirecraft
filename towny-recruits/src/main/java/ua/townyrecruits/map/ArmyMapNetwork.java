package ua.townyrecruits.map;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import ua.townyrecruits.tactics.TacticalNetwork;

public final class ArmyMapNetwork {
    private ArmyMapNetwork() {}
    public static void init() {
        TacticalNetwork.CHANNEL.registerMessage(2,ArmyMapData.Snapshot.class,ArmyMapData.Codec::write,ArmyMapData.Codec::read,(message,supplier)->{
            var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                ()->()->ua.townyrecruits.client.ArmyMapClient.receive(message,context.getNetworkManager())));context.setPacketHandled(true);
        },java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(ServerPlayer player,ArmyMapData.Snapshot snapshot){TacticalNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),snapshot);}
}
