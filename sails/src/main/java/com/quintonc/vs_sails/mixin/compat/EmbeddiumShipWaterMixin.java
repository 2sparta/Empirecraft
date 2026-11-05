package com.quintonc.vs_sails.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.quintonc.vs_sails.client.FloodWaterRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Use Embeddium's existing custom-fluid vertex bridge only for finite ship water. */
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer",remap=false)
public abstract class EmbeddiumShipWaterMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/Fluid;m_205067_(Lnet/minecraft/tags/TagKey;)Z"),remap=false)
    private boolean empirecraft$shipSurface(Fluid fluid,TagKey<Fluid> tag,Operation<Boolean> original,
            @Local(argsOnly=true,ordinal=0) BlockPos pos,@Local(argsOnly=true) FluidState state){
        return original.call(fluid,tag) || FloodWaterRenderer.managed(pos,state);
    }
}
