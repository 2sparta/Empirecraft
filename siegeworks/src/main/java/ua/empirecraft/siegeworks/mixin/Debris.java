package ua.empirecraft.siegeworks.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.siegeworks.Blocks;
@Mixin(targets="me.mss1r.axiomata.ballistics.DebrisPhysics",remap=false)
public abstract class Debris {
 @Inject(method="canBecomeDebris(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",at=@At("HEAD"),cancellable=true,require=1)
 private void plan(ServerLevel level,BlockPos pos,BlockState state,CallbackInfoReturnable<Boolean> result){if(!Blocks.allowed(level,pos))result.setReturnValue(false);}
 @Inject(method="spawnDebris(Lnet/minecraft/server/level/ServerLevel;FLnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/player/Player;Z)V",at=@At("HEAD"),cancellable=true,require=1)
 private void emit(ServerLevel level,float power,BlockPos pos,BlockState state,Vec3 from,Vec3 motion,Player player,boolean removeSource,CallbackInfo result){if(!Blocks.allowed(level,pos))result.cancel();}
}
