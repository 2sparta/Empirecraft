package ua.empirecraft.engagement.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import java.util.Set;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.engagement.ShipAssemblyGate;

@Mixin(targets="org.valkyrienskies.mod.common.assembly.ShipAssembler",remap=false)
public abstract class AssemblyCaptureMixin {
    @Inject(method="assembleToShip(Lnet/minecraft/server/level/ServerLevel;Ljava/util/Set;D)Lorg/valkyrienskies/core/api/ships/ServerShip;",at=@At("RETURN"))
    private static void created(ServerLevel level,Set<BlockPos> blocks,double scale,CallbackInfoReturnable<ServerShip> result){ShipAssemblyGate.capture(result.getReturnValue());}
}
