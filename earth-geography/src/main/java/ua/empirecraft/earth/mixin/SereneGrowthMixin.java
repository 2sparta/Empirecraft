package ua.empirecraft.earth.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.earth.*;
/** Replace the native northern-calendar gate only for explicitly mapped plants in Earth worlds. */
@Mixin(targets="sereneseasons.season.SeasonalCropGrowthHandler",remap=false)
public abstract class SereneGrowthMixin {
    @Inject(method="onCropGrowth",at=@At("HEAD"),cancellable=true)
    private static void earth$localGrowth(Level l,BlockPos p,BlockState s,CallbackInfo growth,CallbackInfo hook){
        String id=ForgeRegistries.BLOCKS.getKey(s.getBlock()).toString();
        if(EarthMod.enabled(l)&&EarthMod.data.plants.has(id)){
            if(!EarthMod.canGrow(l,p,id))growth.cancel();
            hook.cancel();
        }
    }
}
