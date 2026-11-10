package ua.empirecraft.armor;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
/** The hand and ordinary armor slot obey the same outer-first rule. */
public final class ArmorUse {
    private ArmorUse(){}
    public static void use(PlayerInteractEvent.RightClickItem event) {
        Player player=event.getEntity();InteractionHand hand=event.getHand();
        ItemStack incoming=player.getItemInHand(hand);var body=LayerData.slot(incoming);
        if(body==null)return;
        var usedItem=incoming.getItem();ItemStack worn=player.getItemBySlot(body);if(worn.isEmpty())return;
        boolean accepted=false;
        if(LayerData.canAdd(worn,incoming)) {
            var pieces=LayerData.pieces(worn);pieces.add(LayerData.plain(incoming));
            player.setItemSlot(body,LayerData.wrap(pieces));incoming.shrink(1);accepted=true;
        }else if(!LayerData.layered(incoming)&&incoming.getCount()==1&&LayerData.rank(incoming)==LayerData.rank(worn)
                &&(player.isCreative()||!EnchantmentHelper.hasBindingCurse(worn))) {
            var pieces=LayerData.pieces(worn);var previous=pieces.set(pieces.size()-1,LayerData.plain(incoming));
            player.setItemSlot(body,LayerData.wrap(pieces));player.setItemInHand(hand,previous);accepted=true;
        }
        if(!player.level().isClientSide) {
            if(accepted){player.awardStat(Stats.ITEM_USED.get(usedItem));player.inventoryMenu.broadcastChanges();}
            else player.displayClientMessage(Component.translatable("empirecraft.armor.order"),true);
        }
        event.setCanceled(true);
        event.setCancellationResult(accepted?InteractionResult.sidedSuccess(player.level().isClientSide):InteractionResult.FAIL);
    }
}
