package com.quintonc.vs_sails.mixin.compat;

import com.quintonc.vs_sails.protection.ProtectionEvents;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ShipContainerPacketMixin {
    @Shadow public ServerPlayer player;
    @Inject(method={"m_5914_","m_6557_"},at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$inventory(CallbackInfo ci) {
        // Vanilla schedules these packets onto the server thread; only check on that second invocation.
        if(player.getServer().isSameThread() && !ProtectionEvents.allowsOpen(player)) {player.closeContainer();ci.cancel();}
    }
}
