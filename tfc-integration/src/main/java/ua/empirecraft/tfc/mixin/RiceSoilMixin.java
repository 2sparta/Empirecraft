package ua.empirecraft.tfc.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.RiceBlock", remap = false)
public abstract class RiceSoilMixin {
    @Unique
    private static final TagKey<Block> EMPIRECRAFT_PADDY_SOILS =
        TagKey.create(Registries.BLOCK, new ResourceLocation("empirecraft_tfc", "paddy_soils"));

    @Inject(method = "m_6266_", at = @At("HEAD"), cancellable = true)
    private void empirecraft$claySoil(BlockState state, BlockGetter world, BlockPos pos,
                                      CallbackInfoReturnable<Boolean> result) {
        if (state.is(Blocks.CLAY) || state.is(EMPIRECRAFT_PADDY_SOILS)) result.setReturnValue(true);
    }
}
