package ua.empirecraft.townyforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.townyforge.TownyMenu;

/**
 * Routes tribune clicks (the returned chest-menu screens) into the Towny menu
 * before vanilla slot handling can move the button items.
 */
public abstract class TownyMenuClickMixin {
    @Inject(method = "m_150399_(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), cancellable = true)
    private void empirecraft$tribuneClick(int slot, int button, ClickType type, Player player, CallbackInfo info) {
        if (!(player instanceof ServerPlayer server) || slot < 0) return;
        if (type != ClickType.PICKUP) return;
        if (TownyMenu.click(server, (AbstractContainerMenu) (Object) this, slot)) {
            info.cancel();
        }
    }

    @Inject(method = "m_6877_(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"))
    private void empirecraft$tribuneClose(Player player, CallbackInfo info) {
        if (player instanceof ServerPlayer server) {
            TownyMenu.closed(server, (AbstractContainerMenu) (Object) this);
        }
    }
}