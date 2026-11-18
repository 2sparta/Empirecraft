package ua.empirecraft.interactions.mixin;

import com.mctabletop.neoforge.block.GameTableBlockEntity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.empirecraft.interactions.TableAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameTableBlockEntity.class, remap = false)
public abstract class TableOwnershipMixin implements TableAccess {
    @Unique private static final String EMPIRECRAFT_PRIVATE = "EmpirecraftPrivateTable";
    @Unique private boolean empirecraft$privateTable;

    @Override public boolean empirecraft$isPrivate() { return empirecraft$privateTable; }

    @Override
    public void empirecraft$setPrivate(boolean restricted) {
        if (empirecraft$privateTable == restricted) return;
        empirecraft$privateTable = restricted;
        var table=(GameTableBlockEntity)(Object)this;
        table.setChanged();
        var level=table.getLevel();
        if (level!=null) level.sendBlockUpdated(table.getBlockPos(),table.getBlockState(),table.getBlockState(),3);
    }

    @Inject(method="canSit",at=@At("HEAD"),cancellable=true)
    private void empirecraft$admitGuest(UUID player, com.mctabletop.core.game.Player seat, CallbackInfoReturnable<Boolean> result) {
        var table=(GameTableBlockEntity)(Object)this;
        if (empirecraft$privateTable && !table.isOwner(player) && !table.isSeated(player))
            result.setReturnValue(false);
    }

    @Inject(method="m_183515_",at=@At("TAIL"))
    private void empirecraft$saveAccess(CompoundTag tag,CallbackInfo callback) {
        if (empirecraft$privateTable) tag.putBoolean(EMPIRECRAFT_PRIVATE,true);
    }

    @Inject(method="m_142466_",at=@At("TAIL"))
    private void empirecraft$loadAccess(CompoundTag tag,CallbackInfo callback) {
        empirecraft$privateTable=tag.getBoolean(EMPIRECRAFT_PRIVATE);
    }

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
