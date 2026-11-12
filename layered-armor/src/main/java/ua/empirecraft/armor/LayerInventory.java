package ua.empirecraft.armor;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Twelve synchronized views of actual equipped pieces; no second persistent inventory. */
public final class LayerInventory {
    public static final int FIRST=46,COUNT=12,WIDTH=66;
    private final Player player;
    private final AbstractContainerMenu menu;
    private boolean syncing,expanded;
    private final SimpleContainer views=new SimpleContainer(COUNT) {
        @Override public void setChanged(){super.setChanged();if(!syncing&&player!=null&&!player.level().isClientSide)store();}
    };
    public LayerInventory(Player player,AbstractContainerMenu menu,Consumer<Slot> add) {
        this.player=player;this.menu=menu;
        if(menu.slots.size()!=FIRST)throw new IllegalStateException("Unexpected player slot layout: "+menu.slots.size());
        for(int rank=0;rank<3;rank++)for(int row=0;row<4;row++) {
            final int layer=rank,body=row;
            add.accept(new Slot(views,rank*4+row,-58+rank*18,8+row*18) {
                private boolean uncovered(){for(int above=layer+1;above<3;above++)if(!views.getItem(above*4+body).isEmpty())return false;return true;}
                @Override public boolean isActive(){return expanded;}
                @Override public boolean mayPlace(ItemStack item){return expanded&&uncovered()&&!LayerData.layered(item)&&LayerData.slot(item)==LayerData.SLOTS.get(body)&&LayerData.rank(item)==layer;}
                @Override public boolean mayPickup(Player actor){return expanded&&uncovered()&&(actor.isCreative()||!EnchantmentHelper.hasBindingCurse(getItem()));}
                @Override public int getMaxStackSize(){return 1;}
            });
        }
        refresh();
    }
    public boolean expanded(){return expanded;}
    public void expanded(boolean value){expanded=value;}
    public void refresh() {
        if(syncing||player.level().isClientSide)return;
        syncing=true;
        try {
            views.clearContent();
            for(int row=0;row<4;row++)for(var piece:LayerData.pieces(player.getItemBySlot(LayerData.SLOTS.get(row)))) {
                int rank=LayerData.rank(piece);if(rank>=0)views.setItem(rank*4+row,piece);
            }
        }finally{syncing=false;}
    }
    private void store() {
        if(!expanded)return;
        for(int row=0;row<4;row++) {
            var pieces=new ArrayList<ItemStack>();
            for(int rank=0;rank<3;rank++)if(!views.getItem(rank*4+row).isEmpty())pieces.add(views.getItem(rank*4+row));
            player.setItemSlot(LayerData.SLOTS.get(row),LayerData.wrap(pieces));
        }
    }
    /** Vanilla quick-move ranges end at 46; handle added views without touching those ranges. */
    public boolean click(int index,ClickType type,Player actor) {
        refresh();
        if(type==ClickType.PICKUP_ALL&&LayerData.slot(menu.getCarried())!=null)return true;
        if(index<FIRST||index>=FIRST+COUNT)return false;
        if(!expanded)return true;
        if(type==ClickType.QUICK_MOVE) {
            Slot slot=menu.getSlot(index);
            if(slot.hasItem()&&slot.mayPickup(actor)&&actor.getInventory().add(slot.getItem().copy())) {
                slot.set(ItemStack.EMPTY);menu.broadcastChanges();
            }
            return true;
        }
        // Mass collection/drag may bypass the outer-first pickup rule or write several snapshots.
        return type==ClickType.PICKUP_ALL||type==ClickType.QUICK_CRAFT;
    }
}
