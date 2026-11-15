package ua.empirecraft.casting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.*;

public final class CastingMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;
    public CastingMenu(int id,Inventory player,BlockPos pos){this(id,player,new ItemStackHandler(2),new SimpleContainerData(3),ContainerLevelAccess.create(player.player.level(),pos));}
    CastingMenu(int id,Inventory player,CastingBlockEntity table){this(id,player,table.inventory(),table.data,ContainerLevelAccess.create(player.player.level(),table.getBlockPos()));}
    private CastingMenu(int id,Inventory player,IItemHandler inventory,ContainerData data,ContainerLevelAccess access){
        super(Casting.MENU.get(),id);this.access=access;this.data=data;addDataSlots(data);
        addSlot(new SlotItemHandler(inventory,0,43,43){@Override public boolean mayPlace(ItemStack stack){return CastingBlockEntity.isMold(stack);}@Override public int getMaxStackSize(){return 1;}});
        addSlot(new SlotItemHandler(inventory,1,117,43){@Override public boolean mayPlace(ItemStack stack){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,8+col*18,111+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,169));
    }
    public int progress(){return data.get(0);}public int status(){return data.get(1);}public int units(){return data.get(2);}
    @Override public boolean stillValid(Player player){return stillValid(access,player,Casting.TABLE.get());}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var source=slot.getItem();var copy=source.copy();boolean moved;
        if(index<2)moved=moveItemStackTo(source,2,38,true);
        else if(CastingBlockEntity.isMold(source))moved=moveItemStackTo(source,0,1,false);
        else if(index<29)moved=moveItemStackTo(source,29,38,false);
        else moved=moveItemStackTo(source,2,29,false);
        if(!moved)return ItemStack.EMPTY;if(source.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,source);return copy;
    }
}
