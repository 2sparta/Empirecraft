package ua.empirecraft.shipcannons.mixin.client;
import net.mehvahdjukaar.supplementaries.client.cannon.*;
import net.mehvahdjukaar.supplementaries.common.block.tiles.CannonBlockTile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import ua.empirecraft.shipcannons.AimMath;
import ua.empirecraft.shipcannons.client.ClientCoordinates;

@Mixin(value=CannonController.class,remap=false)
public abstract class CannonControllerMixin {
    @Shadow protected static CannonBlockTile cannon;
    @Shadow protected static CannonTrajectory trajectory;
    @Shadow private static boolean needsToUpdateServer;
    @Redirect(method="onClientTick",at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;m_203193_(Lnet/minecraft/core/Position;)D"),require=1)
    private static double empirecraft$controlRange(BlockPos pos,Position player) {
        var level=Minecraft.getInstance().level;
        return ClientCoordinates.center(level,pos).distanceToSqr(ClientCoordinates.world(level,new Vec3(player.x(),player.y(),player.z())));
    }
    @Redirect(method="setupCamera",at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;m_252807_()Lnet/minecraft/world/phys/Vec3;"),require=2)
    private static Vec3 empirecraft$worldCenter(BlockPos pos){return ClientCoordinates.center(cannon.getLevel(),pos);}
    @Inject(method="setCannonAngles",at=@At("HEAD"),cancellable=true,require=1)
    private static void empirecraft$localBarrelAngles(float partialTick,float targetYaw,CallbackInfo ci) {
        var ship=VSGameUtilsKt.getShipManagingPos(cannon.getLevel(),cannon.getBlockPos());
        if(!(ship instanceof ClientShip client))return;
        var direction=AimMath.localDirection(client.getRenderTransform().getShipToWorldRotation(),targetYaw,Math.toDegrees(trajectory.pitch()));
        float beforeYaw=cannon.getYaw(),beforePitch=cannon.getPitch();
        cannon.setRestrainedPitch((float)AimMath.pitch(direction));cannon.setRenderYaw((float)AimMath.yaw(direction));
        if(Math.abs(cannon.getYaw()-beforeYaw)>.001 || Math.abs(cannon.getPitch()-beforePitch)>.001)needsToUpdateServer=true;
        ci.cancel();
    }
}
