package ua.empirecraft.earth.mixin;
import com.mojang.datafixers.util.Either;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.earth.*;
/** Imported FULL chunks load normally. Missing coverage becomes void, never random terrain. Light/FULL stages still run. */
@Mixin(value=ChunkStatus.class,remap=false)
public abstract class GenerationMixin {
    @Inject(method="m_280308_",at=@At("HEAD"),cancellable=true)
    private void earth$noTerrain(Executor executor,ServerLevel level,ChunkGenerator generator,StructureTemplateManager structures,
        ThreadedLevelLightEngine light,Function<ChunkAccess,CompletableFuture<Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure>>> converter,
        List<ChunkAccess> neighbours,CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess,ChunkHolder.ChunkLoadingFailure>>> cir){
        if(!EarthMod.enabled(level)||!EarthMod.data.voidMissingChunks)return;
        ChunkStatus stage=(ChunkStatus)(Object)this;
        if(stage!=ChunkStatus.STRUCTURE_STARTS&&stage!=ChunkStatus.STRUCTURE_REFERENCES&&stage!=ChunkStatus.NOISE&&stage!=ChunkStatus.SURFACE&&stage!=ChunkStatus.CARVERS&&stage!=ChunkStatus.FEATURES&&stage!=ChunkStatus.SPAWN)return;
        var chunk=neighbours.get(neighbours.size()/2);
        if(chunk.getStatus().isOrAfter(stage))return;
        if(chunk instanceof ProtoChunk proto){
            if(stage==ChunkStatus.NOISE)EarthMod.LOG.warn("Missing Earth terrain in {} {}: generating VOID. Restore this chunk from the original Earth archive.",level.dimension().location(),chunk.getPos());
            if(stage==ChunkStatus.NOISE||stage==ChunkStatus.FEATURES)net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk,stage.heightmapsAfter());
            proto.setStatus(stage);
            cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
        }
    }
}
