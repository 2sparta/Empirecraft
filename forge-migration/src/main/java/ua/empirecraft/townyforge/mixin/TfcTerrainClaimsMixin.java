package ua.empirecraft.townyforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.townyforge.TownyForge;

@Pseudo
@Mixin(targets = "ua.empirecraft.tfc.OneTimeTerrain", remap = false)
abstract class TfcTerrainClaimsMixin {
    @Inject(method = "wilderness", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void nativeTowny(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(TownyForge.wilderness(level, pos));
    }
}
