package ua.empirecraft.leaves.mixin.client;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.leaves.SeasonalLeaves;
import ua.empirecraft.leaves.client.ClientSeasons;

@Mixin(targets="net.minecraft.client.renderer.block.BlockModelShaper",remap=false)
public abstract class LeafModelMixin {
    @Inject(method="m_110893_",at=@At("RETURN"),cancellable=true)
    private void empirecraft$leafFaces(BlockState state,CallbackInfoReturnable<BakedModel> ci) {
        if(SeasonalLeaves.isLeaves(state)&&!SeasonalLeaves.isEvergreen(state))ci.setReturnValue(ClientSeasons.wrap(ci.getReturnValue()));
    }
}
