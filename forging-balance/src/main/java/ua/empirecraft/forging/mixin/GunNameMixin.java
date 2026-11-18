package ua.empirecraft.forging.mixin;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=ItemStack.class,remap=false)
public abstract class GunNameMixin {
    private static final Map<String,Integer> EC_YEARS=Map.ofEntries(
        Map.entry("thirtyyearswar:baroque_matchlock_musket",1630),Map.entry("thirtyyearswar:baroque_wheellock_pistol",1630));
    @Inject(method="m_41786_",at=@At("RETURN"),cancellable=true)
    private void empirecraft$datedGun(CallbackInfoReturnable<Component> ci){
        var key=ForgeRegistries.ITEMS.getKey(((ItemStack)(Object)this).m_41720_());
        Integer year=key==null?null:EC_YEARS.get(key.toString());
        if(year!=null)ci.setReturnValue(Component.m_237110_("item.empirecraft_forging.dated_gun", ci.getReturnValue(), year));
    }
}
