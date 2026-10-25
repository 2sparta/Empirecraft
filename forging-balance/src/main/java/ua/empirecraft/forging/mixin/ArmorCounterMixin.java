package ua.empirecraft.forging.mixin;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkEvent;
import net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity;
import net.stirdrem.overgeared.networking.packet.PacketSendCounterC2SPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.forging.*;

@Mixin(value = PacketSendCounterC2SPacket.class, remap = false)
public abstract class ArmorCounterMixin {
    @Shadow @Final private BlockPos pos;
    @Shadow @Final private String quality;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 1)
    private static void empirecraft$perAnvilHits(PacketSendCounterC2SPacket message, Supplier<NetworkEvent.Context> supplier, CallbackInfo ci) {
        var packet = (ArmorCounterMixin)(Object)message;
        if (!packet.quality.startsWith(ArmorRules.PREFIX)) return;
        ci.cancel();
        var context = supplier.get();
        context.enqueueWork(() -> {
            var sender = context.getSender();
            if (sender == null || sender.m_20275_(packet.pos.m_123341_() + 0.5, packet.pos.m_123342_() + 0.5, packet.pos.m_123343_() + 0.5) > 64) return;
            var entity = sender.m_9236_().m_7702_(packet.pos);
            if (entity instanceof AbstractSmithingAnvilBlockEntity anvil && anvil.isOwnedBy(sender) && anvil.isMinigameOn()) {
                ((ArmorSession)anvil).empirecraft$report(ArmorRules.HitReport.decode(packet.quality));
            }
        });
        context.setPacketHandled(true);
    }
}
