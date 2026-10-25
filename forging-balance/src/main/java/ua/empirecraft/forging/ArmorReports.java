package ua.empirecraft.forging;

import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;
import net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity;
import net.stirdrem.overgeared.networking.packet.PacketSendCounterC2SPacket;

public final class ArmorReports {
    private ArmorReports() {}

    public static boolean handle(PacketSendCounterC2SPacket message, Supplier<NetworkEvent.Context> supplier) {
        String quality = message.getCounter();
        if (!quality.startsWith(ArmorRules.PREFIX)) return false;
        var pos = ((CounterPacketPosition)(Object)message).empirecraft$position();
        var context = supplier.get();
        context.enqueueWork(() -> {
            var sender = context.getSender();
            if (sender == null || sender.m_20275_(pos.m_123341_() + 0.5, pos.m_123342_() + 0.5, pos.m_123343_() + 0.5) > 64) return;
            var entity = sender.m_9236_().m_7702_(pos);
            if (entity instanceof AbstractSmithingAnvilBlockEntity anvil && anvil.isOwnedBy(sender) && anvil.isMinigameOn()) {
                ((ArmorSession)anvil).empirecraft$report(ArmorRules.HitReport.decode(quality));
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
