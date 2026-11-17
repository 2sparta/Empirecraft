package ua.empirecraft.earth.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(targets="ua.empirecraft.tfc.OneTimeTerrain",remap=false)
public interface TerrainAccess {
    @Invoker("wilderness") static boolean earth$wilderness(ServerLevel l,BlockPos p){throw new AssertionError("Mixin not applied");}
}
