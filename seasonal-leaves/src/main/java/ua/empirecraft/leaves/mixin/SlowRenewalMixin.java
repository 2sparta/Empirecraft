package ua.empirecraft.leaves.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.leaves.SeasonalVegetation;
@Pseudo
@Mixin(targets="ua.empirecraft.worldrules.NaturalRenewal",remap=false)
public abstract class SlowRenewalMixin {
    @Inject(method="plant",at=@At("HEAD"),cancellable=true,require=1)
    private void slowPlants(ServerLevel l,int cx,int cz,long key,@Coerce Object data,CallbackInfo ci){
        if(!SeasonalVegetation.allowNativeAttempt(l,cx,cz,false))ci.cancel();
    }
    @ModifyConstant(method="plant",constant=@Constant(intValue=24),require=1)
    private int onePlantAttempt(int original){return 1;}
    @Inject(method="tree",at=@At("HEAD"),cancellable=true,require=1)
    private void slowTrees(ServerLevel l,int cx,int cz,long key,@Coerce Object data,CallbackInfo ci){
        if(!SeasonalVegetation.allowNativeAttempt(l,cx,cz,true))ci.cancel();
    }
    @Redirect(method="tree",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerLevel;m_46597_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),require=1)
    private boolean plantYoungTree(ServerLevel l,BlockPos p,BlockState state){
        boolean placed=l.setBlockAndUpdate(p,SeasonalVegetation.youngTree(state));
        if(placed)SeasonalVegetation.rememberTree(l,p);
        return placed;
    }
}
