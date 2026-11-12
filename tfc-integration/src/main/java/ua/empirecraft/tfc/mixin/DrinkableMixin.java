package ua.empirecraft.tfc.mixin;
import net.dries007.tfc.util.Drinkable;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.thirst.ThirstCapability;

/** Ceramic jugs refill the existing LSO thirst bar, never a second thirst system. */
@Mixin(value=Drinkable.class,remap=false)
public abstract class DrinkableMixin {
    @Inject(method="onDrink",at=@At("TAIL"))
    private void empirecraft$lsoThirst(Player player,int amount,CallbackInfo ci){
        int hydration=Math.round(((Drinkable)(Object)this).getThirst()*amount*ThirstCapability.MAX_HYDRATION/(100f*25f));
        if(!player.level().isClientSide&&hydration>0)ThirstUtil.takeDrink(player,hydration,0.25f);
    }
}
