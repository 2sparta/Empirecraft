package ua.empirecraft.locks;

import java.util.List;
import java.util.UUID;
import melonslise.locks.common.item.LockPickItem;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class SteelLockPickItem extends LockPickItem {
    public SteelLockPickItem() { super(1.0f, new Properties().durability(96)); }

    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || player.isSpectator()) return InteractionResult.FAIL;
        Lockable target = LocksUtil.intersecting(context.getLevel(), context.getClickedPos())
                .filter(l -> l.lock.isLocked()).findFirst().orElse(null);
        if (target == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server) || !SteelPickingMenu.canReach(player, target)
                || !ClaimPermission.allowed(player, target)) {
            player.displayClientMessage(Component.translatable("locks.status.claim_denied"), true);
            return InteractionResult.FAIL;
        }
        UUID nonce = UUID.randomUUID();
        NetworkHooks.openScreen(server, new SteelPickingMenu.Provider(context.getHand(), target, nonce), buf -> {
            buf.writeEnum(context.getHand()); Lockable.toBuf(buf, target); buf.writeUUID(nonce);
        });
        return InteractionResult.CONSUME;
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("locks.tooltip.steel_pick").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("locks.tooltip.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(ChatFormatting.DARK_GREEN));
    }
}
