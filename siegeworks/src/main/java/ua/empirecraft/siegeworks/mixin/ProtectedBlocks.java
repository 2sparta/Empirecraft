package ua.empirecraft.siegeworks.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.siegeworks.Blocks;
/** Pending verification against the required Axiomata artifact; never installed untested. */
@Mixin(targets="me.mss1r.axiomata.ballistics.ProtectedBlockAccess",remap=false)
public abstract class ProtectedBlocks {
 @Inject(method="mayDamage",at=@At("HEAD"),cancellable=true,require=1)
 private void damage(ServerLevel level,BlockPos pos,BlockState state,Player player,CallbackInfoReturnable<Boolean> result){if(!Blocks.allowed(level,pos))result.setReturnValue(false);}
 @Inject(method="placeBlock",at=@At("HEAD"),cancellable=true,require=1)
 private void place(ServerLevel level,BlockPos pos,BlockState state,Player player,CallbackInfoReturnable<Boolean> result){if(!Blocks.allowed(level,pos))result.setReturnValue(false);}
 @Inject(method={"placeFire","breakBlock"},at=@At("HEAD"),cancellable=true,require=1)
 private void modify(ServerLevel level,BlockPos pos,Player player,CallbackInfoReturnable<Boolean> result){if(!Blocks.allowed(level,pos))result.setReturnValue(false);}
}
