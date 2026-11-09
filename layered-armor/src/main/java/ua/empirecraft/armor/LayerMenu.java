package ua.empirecraft.armor;

import java.util.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** A synchronized view of worn items, refreshed before each click and broadcast to avoid stale copies. */
public final class LayerMenu extends AbstractContainerMenu {
    private final Player player;private boolean syncing;
    private final SimpleContainer layers=new SimpleContainer(12) {
        @Override public void setChanged(){super.setChanged();if(!syncing&&player!=null&&!player.level().isClientSide)store();}
    };
    public LayerMenu(int id,Inventory inventory) {
        super(LayeredArmor.MENU.get(),id);player=inventory.player;
        if(!player.level().isClientSide)try {
            // Mohist's generic Bukkit inventory view needs its owner, even for Forge menus.
            // Reflection keeps Bukkit types out of the client and pure Forge class path.
            AbstractContainerMenu.class.getField("containerOwner").set(this,player);
        }catch(NoSuchFieldException forgeOnly){}catch(IllegalAccessException inaccessible){throw new IllegalStateException("Cannot bind the menu owner",inaccessible);}
        refresh();
        for(int rank=0;rank<3;rank++)for(int column=0;column<4;column++) {
            final int layer=rank,col=column;
            addSlot(new Slot(layers,rank*4+column,66+column*23,36+rank*22) {
                private boolean uncovered(){for(int above=layer+1;above<3;above++)if(!layers.getItem(above*4+col).isEmpty())return false;return true;}
                @Override public boolean mayPlace(ItemStack item){return uncovered()&&!LayerData.layered(item)&&LayerData.slot(item)==LayerData.SLOTS.get(col)&&LayerData.rank(item)==layer;}
                @Override public boolean mayPickup(Player actor){return uncovered()&&(actor.isCreative()||!EnchantmentHelper.hasBindingCurse(getItem()));}
                @Override public int getMaxStackSize(){return 1;}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,8+col*18,106+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,164));
    }
    private void refresh() {
        if(player.level().isClientSide)return;
        syncing=true;try {
            layers.clearContent();
            for(int col=0;col<4;col++)for(var item:LayerData.pieces(player.getItemBySlot(LayerData.SLOTS.get(col)))) {
                int rank=LayerData.rank(item);if(rank>=0)layers.setItem(rank*4+col,item);
            }
        }finally{syncing=false;}
    }
    private void store() {
        for(int col=0;col<4;col++) {
            var all=new ArrayList<ItemStack>();
            for(int rank=0;rank<3;rank++)if(!layers.getItem(rank*4+col).isEmpty())all.add(layers.getItem(rank*4+col));
            player.setItemSlot(LayerData.SLOTS.get(col),LayerData.wrap(all));
        }
    }
    @Override public void clicked(int index,int button,ClickType click,Player actor){refresh();super.clicked(index,button,click,actor);}
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public boolean stillValid(Player actor){return actor==player&&actor.isAlive()&&!actor.isRemoved();}
    @Override public ItemStack quickMoveStack(Player actor,int index) {
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem()||!slot.mayPickup(actor))return ItemStack.EMPTY;
        ItemStack item=slot.getItem(),before=item.copy();
        if(index<12){if(!moveItemStackTo(item,12,48,true))return ItemStack.EMPTY;}
        else {
            int col=LayerData.SLOTS.indexOf(LayerData.slot(item)),rank=LayerData.rank(item);
            if(col<0||rank<0||!moveItemStackTo(item,rank*4+col,rank*4+col+1,false))return ItemStack.EMPTY;
        }
        if(item.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(actor,item);return before;
    }
}
