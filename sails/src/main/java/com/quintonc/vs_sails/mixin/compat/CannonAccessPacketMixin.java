package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.protection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject both GUI and control packets before Supplementaries can change the tile, even after revocation. */
@Pseudo
@Mixin(targets={"net.mehvahdjukaar.supplementaries.common.network.ServerBoundSyncCannonPacket",
        "net.mehvahdjukaar.supplementaries.common.network.ServerBoundRequestOpenCannonGuiMessage"},remap=false)
public abstract class CannonAccessPacketMixin {
    @Shadow @Final private BlockPos pos;
    @Inject(method="handle",at=@At("HEAD"),cancellable=true)
    private void empirecraft$authorize(@Coerce Object context,CallbackInfo ci) {
        try {
            Object sender=Class.forName("net.mehvahdjukaar.moonlight.api.platform.network.ChannelHandler$Context")
                    .getMethod("getSender").invoke(context);
            if(!(sender instanceof Player player) || !ShipProtection.allows(player,player.level(),pos)) {ci.cancel();return;}
            ProtectionEvents.track(player,pos);
        }catch(ReflectiveOperationException | RuntimeException | LinkageError failure) {ci.cancel();}
    }
}
