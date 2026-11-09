package ua.empirecraft.armor.mixin;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import ua.empirecraft.armor.LayerData;
@Mixin(value=LivingEntity.class,remap=false)
public abstract class ProtectionMixin {
    @Redirect(method="m_6515_",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/enchantment/EnchantmentHelper;m_44856_(Ljava/lang/Iterable;Lnet/minecraft/world/damagesource/DamageSource;)I"))
    private int allProtection(Iterable<ItemStack> equipment,DamageSource source){var all=new ArrayList<ItemStack>();for(var item:equipment)all.addAll(LayerData.pieces(item));return EnchantmentHelper.getDamageProtection(all,source);}
}
