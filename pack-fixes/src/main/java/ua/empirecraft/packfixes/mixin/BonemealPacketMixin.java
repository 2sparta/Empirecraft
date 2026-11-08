package ua.empirecraft.packfixes.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import ua.empirecraft.packfixes.BonemealUse;

@Mixin(value=ServerGamePacketListenerImpl.class,remap=false)
public abstract class BonemealPacketMixin {
    @Shadow public ServerPlayer f_9743_;
    @WrapMethod(method="m_6371_")
    private void empirecraft$boneOn(ServerboundUseItemOnPacket packet,Operation<Void> original) {
        if(!f_9743_.server.isSameThread()){original.call(packet);return;}
        try(var ignored=BonemealUse.enter(f_9743_,f_9743_.getItemInHand(packet.getHand()),packet.getHitResult().getLocation())){original.call(packet);}
    }
    @WrapMethod(method="m_5760_")
    private void empirecraft$boneAir(ServerboundUseItemPacket packet,Operation<Void> original) {
        if(!f_9743_.server.isSameThread()){original.call(packet);return;}
        try(var ignored=BonemealUse.enter(f_9743_,f_9743_.getItemInHand(packet.getHand()),f_9743_.position())){original.call(packet);}
    }
}
