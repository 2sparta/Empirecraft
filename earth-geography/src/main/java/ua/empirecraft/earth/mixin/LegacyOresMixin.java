package ua.empirecraft.earth.mixin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.earth.*;
/** Disable blanket sulfur/zinc/quartz targets in Earth dimensions, preserving all old completion flags. */
@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets="ua.empirecraft.ores.OreRetrogen",remap=false)
public abstract class LegacyOresMixin {
    @Inject(method="process",at=@At("HEAD"),cancellable=true)
    private static void earth$regional(ServerLevel l,LevelChunk c,CallbackInfoReturnable<Boolean> cir){if(EarthMod.enabled(l))cir.setReturnValue(false);}
}
