package ua.empirecraft.engagement.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.engagement.ShipAssemblyGate;

@Mixin(targets="com.quintonc.vs_sails.protection.FleetNative",remap=false)
public abstract class SailsAssemblyMixin {
    @Inject(method="beforeAssembly",at=@At("HEAD"),cancellable=true)
    private static void approvedOnly(UseOnContext context,CallbackInfoReturnable<Boolean> result){
        if(ShipAssemblyGate.active())result.setReturnValue(false);
        else if(!context.getLevel().isClientSide){ShipAssemblyGate.requestForm(context);result.setReturnValue(true);}
    }
}
