package ua.empirecraft.leaves.mixin;
import java.util.ArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import ua.empirecraft.leaves.SeasonalVegetation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Different invocation from the calendar's canSurvive redirect, so both policies compose. */
@Pseudo
@Mixin(targets="ua.empirecraft.worldrules.NaturalRenewal",remap=false)
public abstract class RareRenewalMixin {
    @Redirect(method="plant",at=@At(value="INVOKE",target="Ljava/util/ArrayList;add(Ljava/lang/Object;)Z"),require=1)
    private boolean empirecraft$filterRare(ArrayList<BlockState> choices,Object value,
        ServerLevel level,int cx,int cz,long key,@Coerce Object data) {
        BlockState state=(BlockState)value;
        return SeasonalVegetation.allowRenewal(level,cx,cz,state) && choices.add(state);
    }
    @Inject(method="plant",at=@At("RETURN"),require=1)
    private void empirecraft$rememberRare(ServerLevel level,int cx,int cz,long key,@Coerce Object data,CallbackInfo ci){
        SeasonalVegetation.renewalComplete(level,cx,cz);
    }
}
