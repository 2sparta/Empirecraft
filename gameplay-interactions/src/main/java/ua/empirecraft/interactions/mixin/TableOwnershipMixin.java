package ua.empirecraft.interactions.mixin;

import com.mctabletop.neoforge.block.GameTableBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameTableBlockEntity.class, remap = false)
public abstract class TableOwnershipMixin {
    @Redirect(method = "fitBoard", at = @At(value = "INVOKE",
        target = "Lcom/mctabletop/neoforge/block/GameTableBlockEntity;canManage(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean empirecraft$unownedTable(GameTableBlockEntity table, Player player) {
        return table.owner() == null || table.canManage(player);
    }

    @Inject(method = "fitBoard", at = @At("RETURN"))
    private void empirecraft$claimAfterSuccessfulFit(Player player, ItemStack board, CallbackInfoReturnable<String> result) {
        var table = (GameTableBlockEntity) (Object) this;
        if (result.getReturnValue() == null && table.owner() == null) table.setOwner(player);
    }
}
