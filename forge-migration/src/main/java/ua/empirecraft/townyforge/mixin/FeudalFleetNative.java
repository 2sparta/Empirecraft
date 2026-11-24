package ua.empirecraft.townyforge.mixin;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.feudal.ForgeFeudal;
import ua.empirecraft.townyforge.TownyForge;

@Pseudo
@Mixin(targets = "com.quintonc.vs_sails.protection.FleetNative", remap = false)
abstract class FeudalFleetNative {
    @Inject(method = "plugin", at = @At("HEAD"), cancellable = true)
    private static void provider(CallbackInfoReturnable<Object> result) {
        result.setReturnValue(ForgeFeudal.active());
    }

    @Inject(method = "world", at = @At("HEAD"), cancellable = true)
    private static void world(ServerLevel level, CallbackInfoReturnable<String> result) {
        String dimension = level.m_46472_().m_135782_().toString();
        result.setReturnValue(Objects.toString(TownyForge.legacyWorld(dimension), dimension));
    }
}
