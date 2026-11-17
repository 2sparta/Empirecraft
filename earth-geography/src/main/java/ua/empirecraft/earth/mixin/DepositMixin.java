package ua.empirecraft.earth.mixin;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ua.empirecraft.tfc.OneTimeTerrain;
import ua.empirecraft.earth.*;
/** The independent geographic pass owns new Earth deposits; preserve the old completion state. */
@Mixin(value=OneTimeTerrain.class,remap=false)
public abstract class DepositMixin {
    @Redirect(method="process",at=@At(value="FIELD",target="Lua/empirecraft/tfc/OneTimeTerrain$Deposit;chance:D",opcode=180))
    private static double earth$chance(OneTimeTerrain.Deposit deposit,ServerLevel level,LevelChunk chunk){
        return EarthMod.enabled(level)?0:deposit.chance();
    }
}
