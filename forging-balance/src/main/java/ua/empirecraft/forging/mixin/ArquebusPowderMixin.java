package ua.empirecraft.forging.mixin;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ua.empirecraft.forging.PowderPayment;

@Mixin(targets = "com.TBK.medieval_boomsticks.common.items.RechargeItem", remap = false)
public abstract class ArquebusPowderMixin {
    @Shadow public abstract boolean canShoot(Player player);

    @Unique private static boolean empirecraft$arquebus(ItemStack stack) {
        return new ResourceLocation("medieval_boomsticks", "arquebus").equals(ForgeRegistries.ITEMS.getKey(stack.m_41720_()));
    }

    @Inject(method = "m_7203_", at = @At("HEAD"), cancellable = true, require = 1)
    private void empirecraft$payPowder(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack weapon = player.m_21120_(hand);
        if (!empirecraft$arquebus(weapon) || !CrossbowItem.m_40932_(weapon) || !canShoot(player)
            || player.m_36335_().m_41519_(weapon.m_41720_())) return;
        Item powder = ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft", "gunpowder"));
        var inventory = player.m_150109_();
        boolean paid = PowderPayment.pay(player.m_150110_().f_35937_, level.m_5776_(), inventory.m_6643_(),
            slot -> !inventory.m_8020_(slot).m_41619_() && inventory.m_8020_(slot).m_150930_(powder),
            slot -> { inventory.m_8020_(slot).m_41774_(1); inventory.m_6596_(); });
        if (paid) return;
        if (!level.m_5776_()) player.m_5661_(Component.m_237115_("message.empirecraft.arquebus.no_powder"), true);
        cir.setReturnValue(InteractionResultHolder.m_19100_(weapon));
    }

    @Inject(method = "m_7373_", at = @At("TAIL"), require = 1)
    private void empirecraft$ammoTooltip(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag, CallbackInfo ci) {
        if (empirecraft$arquebus(stack)) tooltip.add(Component.m_237115_("tooltip.empirecraft.arquebus.ammo"));
    }
}
