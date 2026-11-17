package ua.empirecraft.earth.mixin;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.earth.*;
@Mixin(targets="ua.empirecraft.tfc.NaturalMaterials",remap=false)
public abstract class NaturalMaterialsMixin {
    @Inject(method="generate(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Ljava/util/function/Predicate;)I",at=@At("HEAD"))
    private static void earth$start(ServerLevel l,LevelChunk c,Predicate<BlockPos> p,CallbackInfoReturnable<Integer> cir){EarthTerrainContext.set(EarthMod.enabled(l));}
    @Inject(method="generate(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Ljava/util/function/Predicate;)I",at=@At("RETURN"))
    private static void earth$end(ServerLevel l,LevelChunk c,Predicate<BlockPos> p,CallbackInfoReturnable<Integer> cir){EarthTerrainContext.clear();}
}
