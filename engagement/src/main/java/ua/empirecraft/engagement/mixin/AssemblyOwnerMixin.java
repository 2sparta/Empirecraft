package ua.empirecraft.engagement.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.engagement.ShipAssemblyGate;

@Mixin(targets="com.quintonc.vs_sails.protection.ShipProtection",remap=false)
public abstract class AssemblyOwnerMixin {
    @Inject(method="assembled",at=@At("HEAD"),cancellable=true)
    private static void pendingOwner(ServerLevel level,ServerShip ship,Player actor,CallbackInfo callback){if(ShipAssemblyGate.active()){ShipAssemblyGate.capture(ship);callback.cancel();}}
}
