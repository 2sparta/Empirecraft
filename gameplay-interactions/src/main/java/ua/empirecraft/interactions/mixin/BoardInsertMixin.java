package ua.empirecraft.interactions.mixin;

import com.mctabletop.neoforge.block.BoardInteraction;
import com.mctabletop.neoforge.block.GameTableBlock;
import com.mctabletop.neoforge.item.BoardInsertItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BoardInsertItem.class, remap = false)
public abstract class BoardInsertMixin {
    // Forge calls the held item's interaction before dispatching to the table block.
    public InteractionResult onItemUseFirst(ItemStack board, UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (player == null || player.isSpectator() || !(level.getBlockState(pos).getBlock() instanceof GameTableBlock)) {
            return InteractionResult.PASS;
        }
        return BoardInteraction.fit(level, pos, player, board);
    }
}
