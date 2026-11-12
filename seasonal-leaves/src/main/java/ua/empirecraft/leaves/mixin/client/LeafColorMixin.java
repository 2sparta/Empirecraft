package ua.empirecraft.leaves.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.leaves.SeasonalLeaves;
import ua.empirecraft.leaves.client.ClientSeasons;

@Mixin(targets="net.minecraft.client.color.block.BlockColors",remap=false)
public abstract class LeafColorMixin {
    @Inject(method="m_92577_",at=@At("RETURN"),cancellable=true)
    private void empirecraft$autumn(BlockState state,BlockAndTintGetter view,BlockPos pos,int tint,CallbackInfoReturnable<Integer> ci) {
        if(SeasonalLeaves.isLeaves(state)||SeasonalLeaves.isEvergreen(state))ci.setReturnValue(ClientSeasons.color(state,view,pos,ci.getReturnValue(),tint));
    }
}
