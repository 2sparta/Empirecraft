package com.quintonc.vs_sails.mixin.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.quintonc.vs_sails.client.FloodWaterRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="net.minecraft.client.renderer.block.LiquidBlockRenderer",remap=false)
public abstract class ShipWaterRenderMixin {
    @Inject(method="m_234369_",at=@At("HEAD"),cancellable=true,remap=false)
    private void empirecraft$gravitySurface(BlockAndTintGetter level,BlockPos pos,VertexConsumer consumer,BlockState state,FluidState fluid,CallbackInfo ci){
        if(FloodWaterRenderer.render(level,pos,consumer,state,fluid))ci.cancel();
    }
}
