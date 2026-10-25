package ua.empirecraft.forging.mixin;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkEvent;
import net.stirdrem.overgeared.networking.packet.PacketSendCounterC2SPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.forging.*;

@Mixin(value = PacketSendCounterC2SPacket.class, remap = false)
public abstract class ArmorCounterMixin implements CounterPacketPosition {
    @Shadow @Final private BlockPos pos;
    @Override @Unique public BlockPos empirecraft$position() { return pos; }

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 1)
    private static void empirecraft$perAnvilHits(PacketSendCounterC2SPacket message, Supplier<NetworkEvent.Context> supplier, CallbackInfo ci) {
        if (ArmorReports.handle(message, supplier)) ci.cancel();
    }
}
