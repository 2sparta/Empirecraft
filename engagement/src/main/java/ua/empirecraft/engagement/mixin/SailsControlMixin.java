package ua.empirecraft.engagement.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.engagement.*;
import java.util.Map;

@Mixin(targets="com.quintonc.vs_sails.protection.ShipProtection",remap=false)
public abstract class SailsControlMixin {
    @Inject(method="allows(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/server/level/ServerLevel;Lorg/valkyrienskies/core/api/ships/LoadedServerShip;)Z",at=@At("HEAD"),cancellable=true)
    private static void privateAccess(Player player,ServerLevel level,LoadedServerShip ship,CallbackInfoReturnable<Boolean> ci){
        if(ship==null)return;
        try {
            var state=Government.read(level,"ship",Map.of("ship",ship.getId()));if(Boolean.TRUE.equals(state.get("locked"))){ci.setReturnValue(false);return;}
            ci.setReturnValue(player==null?java.util.Objects.equals(ShotOrigins.automationShip(),ship.getId()):Government.controls(level,player.getUUID(),ship.getId()));
        }catch(RuntimeException unavailable){ci.setReturnValue(false);}
    }
}
