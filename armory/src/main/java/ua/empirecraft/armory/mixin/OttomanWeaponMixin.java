package ua.empirecraft.armory.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import ua.empirecraft.armory.OttomanCombat;
@Mixin(targets="ua.empirecraft.forging.Gunsmith",remap=false)
public abstract class OttomanWeaponMixin {
    @Redirect(method="hurt",at=@At(value="INVOKE",target="Lua/empirecraft/forging/Gunsmith;id(Lnet/minecraft/world/item/ItemStack;)Ljava/lang/String;"),require=2)
    private static String armory$physicalWeapon(ItemStack stack){return OttomanCombat.weapon(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());}
}
