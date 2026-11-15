package ua.empirecraft.casting.mixin;
import net.minecraft.core.BlockPos;import net.minecraft.world.level.Level;import net.minecraft.world.level.block.state.BlockState;import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;import ua.empirecraft.casting.Thermal;import net.dries007.tfc.common.capabilities.heat.HeatCapability;
@Mixin(value=AbstractFurnaceBlockEntity.class,remap=false)
public abstract class FurnaceHeatMixin {
 @Unique private int casting$before;
 @Unique private float casting$temperature;
 // Mohist compares the full output capability with a cold recipe result. Normalize only
 // during its synchronous tick, then restore the previous heat or heat the new output.
 @Inject(method="m_155013_",at=@At("HEAD"))private static void before(Level l,BlockPos p,BlockState s,AbstractFurnaceBlockEntity f,CallbackInfo ci){var self=(FurnaceHeatMixin)(Object)f;var out=Thermal.normalize(f.getItem(2));if(out!=f.getItem(2))f.setItem(2,out);self.casting$before=out.getCount();self.casting$temperature=HeatCapability.getTemperature(out);if(self.casting$temperature>0)HeatCapability.setTemperature(out,0);}
 @Inject(method="m_155013_",at=@At("RETURN"))private static void after(Level l,BlockPos p,BlockState s,AbstractFurnaceBlockEntity f,CallbackInfo ci){var self=(FurnaceHeatMixin)(Object)f;var out=f.getItem(2);HeatCapability.setTemperature(out,self.casting$temperature);if(out.getCount()>self.casting$before){out=Thermal.normalize(out);Thermal.heatFurnaceOutput(out);f.setItem(2,out);}}
}
