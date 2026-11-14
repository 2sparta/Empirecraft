package ua.empirecraft.casting.mixin;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.*;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;import net.dries007.tfc.util.Fuel;import net.minecraftforge.items.IItemHandlerModifiable;
@Mixin(value=HeatCapability.class,remap=false)
public abstract class FuelTimeMixin {
 @Inject(method="consumeFuelForTicks(JLnet/minecraftforge/items/IItemHandlerModifiable;IFII)Lnet/dries007/tfc/common/capabilities/heat/HeatCapability$Remainder;",at=@At("HEAD"),cancellable=true)
 private static void units(long ticks,IItemHandlerModifiable inventory,int burnTicks,float temperature,int start,int end,CallbackInfoReturnable<HeatCapability.Remainder> cir){
  if(burnTicks>ticks){cir.setReturnValue(new HeatCapability.Remainder((int)(burnTicks-ticks),temperature,0));return;}ticks-=burnTicks;
  for(int slot=start;slot<=end;slot++)while(!inventory.getStackInSlot(slot).isEmpty()){
   var fuel=Fuel.get(inventory.getStackInSlot(slot));if(fuel==null)break;inventory.extractItem(slot,1,false);
   if(fuel.getDuration()>ticks){cir.setReturnValue(new HeatCapability.Remainder((int)(fuel.getDuration()-ticks),fuel.getTemperature(),0));return;}ticks-=fuel.getDuration();
  }cir.setReturnValue(new HeatCapability.Remainder(0,temperature,ticks));
 }
}
