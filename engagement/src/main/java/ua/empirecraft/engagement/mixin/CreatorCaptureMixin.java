package ua.empirecraft.engagement.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.joml.Vector3ic;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ua.empirecraft.engagement.ShipAssemblyGate;

@Mixin(targets="org.valkyrienskies.mod.common.item.ShipCreatorItem",remap=false)
public abstract class CreatorCaptureMixin {
    @WrapOperation(method="m_6225_",at=@At(value="INVOKE",target="Lorg/valkyrienskies/core/internal/world/VsiServerShipWorld;createNewShipAtBlock(Lorg/joml/Vector3ic;ZDLjava/lang/String;)Lorg/valkyrienskies/core/api/ships/ServerShip;"))
    private ServerShip capture(VsiServerShipWorld world,Vector3ic position,boolean isStatic,double scale,String dimension,Operation<ServerShip> original){var ship=original.call(world,position,isStatic,scale,dimension);ShipAssemblyGate.capture(ship);return ship;}
}
