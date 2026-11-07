package ua.empirecraft.forging.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.stirdrem.overgeared.item.custom.UpgradeArrowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.empirecraft.forging.EquipmentContent;

/** Quality/custom NBT alone does not turn a normal tiered arrow into an empty potion arrow. */
@Mixin(value=UpgradeArrowItem.class,remap=false)
public abstract class ArrowNameMixin {
    @Inject(method="m_7626_(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/network/chat/Component;",at=@At("HEAD"),cancellable=true)
    private void empirecraft$plainArrowName(ItemStack stack,CallbackInfoReturnable<Component> result) {
        if(!EquipmentContent.hasPotion(stack.m_41783_()))result.setReturnValue(Component.m_237115_(((Item)(Object)this).m_5524_()));
    }
}
