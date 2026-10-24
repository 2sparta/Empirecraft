package ua.empirecraft.worldrules.mixin;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="me.toastymop.combatlog.CombatConfig",remap=false)
public abstract class CombatConfigMixin {
    @Inject(method="findItems",at=@At("RETURN"),cancellable=true)
    private static void empirecraft$ignoreAirAndAllowCannon(List<String> configured,CallbackInfoReturnable<List<Item>> ci) {
        ci.setReturnValue(ci.getReturnValue().stream().filter(item->{
            ResourceLocation id=item==null?null:ForgeRegistries.ITEMS.getKey(item);
            return id!=null && !id.toString().equals("minecraft:air") && !id.toString().equals("supplementaries:cannon");
        }).toList());
    }
}
