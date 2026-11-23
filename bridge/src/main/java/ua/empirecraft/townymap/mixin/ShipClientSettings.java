package ua.empirecraft.townymap.mixin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.townymap.ShipViewDistance;
@Mixin(value=ServerPlayer.class,remap=false)public abstract class ShipClientSettings {
 @Inject(method="m_9156_(Lnet/minecraft/network/protocol/game/ServerboundClientInformationPacket;)V",at=@At("HEAD"))
 private void options(ServerboundClientInformationPacket packet,CallbackInfo ci){ShipViewDistance.update((ServerPlayer)(Object)this,packet.viewDistance());}
}
