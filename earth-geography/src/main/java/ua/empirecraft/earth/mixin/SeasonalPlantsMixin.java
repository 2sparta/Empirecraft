package ua.empirecraft.earth.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.earth.*;
/** Explicit geographic crop profiles replace the hemisphere-blind seasonal gate; TFC still checks its own crop ranges. */
@Mixin(targets="ua.empirecraft.calendar.SeasonalPlants",remap=false)
public abstract class SeasonalPlantsMixin {
    @Inject(method="canGrow",at=@At("HEAD"),cancellable=true)
    private static void earth$grow(Level l,BlockPos p,BlockState s,CallbackInfoReturnable<Boolean> cir){
        String id=ForgeRegistries.BLOCKS.getKey(s.getBlock()).toString();
        if(EarthMod.enabled(l)&&EarthMod.data.plants.has(id))cir.setReturnValue(EarthMod.canGrow(l,p,id));
    }
}
