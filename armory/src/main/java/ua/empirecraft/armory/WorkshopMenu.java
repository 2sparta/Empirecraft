package ua.empirecraft.armory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.items.*;
public final class WorkshopMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final WorkshopEntity table;
    public WorkshopMenu(int id,Inventory player,BlockPos pos){this(id,player,new ItemStackHandler(WorkshopEntity.SLOTS),new SimpleContainerData(4),ContainerLevelAccess.create(player.player.level(),pos),null);}
    public WorkshopMenu(int id,Inventory player,WorkshopEntity table){this(id,player,table.inventory,table.data,ContainerLevelAccess.create(player.player.level(),table.getBlockPos()),table);}
    private WorkshopMenu(int id,Inventory player,IItemHandler items,ContainerData data,ContainerLevelAccess access,WorkshopEntity table){
        super(Armory.MENU.get(),id);this.data=data;this.access=access;this.table=table;addDataSlots(data);
        addSlot(new SlotItemHandler(items,0,116,42){public boolean mayPlace(ItemStack s){return s.is(Armory.WORK.get())||s.getItem() instanceof ArmorItem;}});
        addSlot(new SlotItemHandler(items,1,280,42){public boolean mayPlace(ItemStack s){return s.getItem() instanceof PartItem;}});
        addSlot(new SlotItemHandler(items,2,280,98){public boolean mayPlace(ItemStack s){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<4;col++)addSlot(new SlotItemHandler(items,3+row*4+col,116+col*18,64+row*18));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,78+col*18,151+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,78+col*18,209));
    }
    public int selected(){return data.get(0);}public int stage(){return data.get(1);}public int status(){return data.get(2);}public int remaining(){return data.get(3);}
    @Override public boolean stillValid(Player p){return stillValid(access,p,Armory.TABLE.get());}
    @Override public boolean clickMenuButton(Player p,int id){if(table==null||!stillValid(p))return false;if(id>=100)return table.select(id-100);return table.request(id,p);}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack source=slot.getItem(),copy=source.copy();boolean moved;
        if(index<WorkshopEntity.SLOTS)moved=moveItemStackTo(source,WorkshopEntity.SLOTS,slots.size(),true);
        else if(source.is(Armory.WORK.get())||source.getItem() instanceof ArmorItem)moved=moveItemStackTo(source,0,1,false);
        else moved=moveItemStackTo(source,WorkshopEntity.FIRST,WorkshopEntity.SLOTS,false);
        if(!moved)return ItemStack.EMPTY;if(source.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,source);return copy;
    }
}
