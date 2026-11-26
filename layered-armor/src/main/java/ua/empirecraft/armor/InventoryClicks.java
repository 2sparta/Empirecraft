package ua.empirecraft.armor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Native inventory packets remain authoritative. Every removal takes exactly the outer piece. */
public final class InventoryClicks {
    private InventoryClicks() {}
    public static boolean handle(AbstractContainerMenu menu,int index,int button,ClickType type,Player player) {
        if(menu!=player.inventoryMenu||index<0||index>=menu.slots.size())return false;
        // Shift+click on an armor piece in the main inventory equips it by layer:
        // padded under mail under outer, swapping with the worn piece of the same rank.
        if(type==ClickType.QUICK_MOVE) {
            Slot moving=menu.getSlot(index);
            if(moving.container==player.getInventory()&&moving.getContainerSlot()>=9&&moving.getContainerSlot()<36) {
                ItemStack item=moving.getItem();
                if(LayerData.slot(item)!=null&&!LayerData.layered(item)) {
                    int index2=36+LayerData.SLOTS.indexOf(LayerData.slot(item));
                    Slot armor=menu.getSlot(index2);
                    ItemStack worn=armor.getItem();
                    if(worn.isEmpty()) {
                        if(!armor.mayPlace(item))return false;
                        armor.set(item.split(1));menu.broadcastChanges();return true;
                    }
                    if(LayerData.canAdd(worn,item)) {
                        var all=LayerData.pieces(worn);all.add(LayerData.plain(item));
                        armor.set(LayerData.wrap(all));item.shrink(1);menu.broadcastChanges();return true;
                    }
                    if(LayerData.rank(item)==LayerData.rank(worn)&&!LayerData.layered(item)
                            &&armor.mayPickup(player)&&!EnchantmentHelper.hasBindingCurse(worn)) {
                        var all=LayerData.pieces(worn);ItemStack previous=all.set(all.size()-1,LayerData.plain(item));
                        armor.set(LayerData.wrap(all));moving.set(previous);menu.broadcastChanges();return true;
                    }
                }
            }
            return false;
        }
        Slot slot=menu.getSlot(index);
        if(slot.container!=player.getInventory()||slot.getContainerSlot()<36||slot.getContainerSlot()>39)return false;
        ItemStack worn=slot.getItem();if(worn.isEmpty())return false;
        boolean layers=LayerData.layered(worn);
        ItemStack incoming=menu.getCarried();
        if(type==ClickType.SWAP) {
            if((button<0||button>8)&&button!=40)return layers;
            incoming=player.getInventory().getItem(button);
        }
        if(type==ClickType.PICKUP||type==ClickType.SWAP) {
            if(slot.mayPlace(incoming)&&LayerData.canAdd(worn,incoming)) {
                var all=LayerData.pieces(worn);all.add(LayerData.plain(incoming));
                slot.set(LayerData.wrap(all));incoming.shrink(1);menu.broadcastChanges();return true;
            }
            if(layers&&incoming.isEmpty()) {
                if(!slot.mayPickup(player))return true;
                var all=LayerData.pieces(worn);var outer=all.remove(all.size()-1);
                slot.set(LayerData.wrap(all));
                if(type==ClickType.SWAP)player.getInventory().setItem(button,outer);else menu.setCarried(outer);
                menu.broadcastChanges();return true;
            }
            if(layers&&!incoming.isEmpty()) {
                // Replacing the outer piece keeps its inner layers equipped.
                if(slot.mayPickup(player)&&slot.mayPlace(incoming)&&!LayerData.layered(incoming)&&LayerData.rank(incoming)==LayerData.rank(worn)) {
                    var all=LayerData.pieces(worn);var outer=all.set(all.size()-1,LayerData.plain(incoming));
                    slot.set(LayerData.wrap(all));
                    if(type==ClickType.SWAP)player.getInventory().setItem(button,outer);else menu.setCarried(outer);
                    menu.broadcastChanges();
                }
                return true;
            }
            if(!incoming.isEmpty()&&LayerData.slot(incoming)==LayerData.slot(worn)&&LayerData.rank(incoming)<LayerData.rank(worn)) {
                if(!player.level().isClientSide)player.displayClientMessage(Component.translatable("empirecraft.armor.order"),true);
                return true;
            }
        }
        if(layers&&(type==ClickType.QUICK_MOVE||type==ClickType.THROW)) {
            if(!slot.mayPickup(player))return true;
            var all=LayerData.pieces(worn);var outer=all.remove(all.size()-1);
            if(type==ClickType.QUICK_MOVE&&!player.getInventory().add(outer.copy()))return true;
            if(type==ClickType.THROW)player.drop(outer,true);
            slot.set(LayerData.wrap(all));menu.broadcastChanges();return true;
        }
        return false;
    }
}
