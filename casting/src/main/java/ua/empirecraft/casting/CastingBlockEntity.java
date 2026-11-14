package ua.empirecraft.casting;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.capabilities.MoldLike;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.recipes.CastingRecipe;

/** Metal is always kept in the real mold item, including while saved, removed or broken. */
public final class CastingBlockEntity extends BlockEntity implements MenuProvider {
    public static final int CYCLE_TICKS=40;
    public static final int NO_MOLD=0,NO_CRUCIBLE=1,COLD=2,NOT_ENOUGH=3,FULL=4,COOLING=5,NO_RECIPE=6,READY=7;
    private int progress,status=NO_MOLD,units=100;private boolean dropping;
    private final ItemStackHandler inventory=new ItemStackHandler(2){
        @Override public int getSlotLimit(int slot){return slot==0?1:64;}
        @Override public boolean isItemValid(int slot,ItemStack stack){return slot==0&&isMold(stack);}
        @Override protected void onContentsChanged(int slot){if(slot==0)progress=0;setChanged();visual();}
    };
    private LazyOptional<IItemHandler> top=LazyOptional.of(()->new Port(true)),output=LazyOptional.of(()->new Port(false));
    final ContainerData data=new ContainerData(){
        public int get(int index){return switch(index){case 0->progress;case 1->status;case 2->units;default->0;};}
        public void set(int index,int value){} public int getCount(){return 3;}
    };
    public CastingBlockEntity(BlockPos pos,BlockState state){super(Casting.ENTITY.get(),pos,state);}
    public IItemHandlerModifiable inventory(){return inventory;}
    public int progress(){return progress;} public int status(){return status;}
    public static boolean isMold(ItemStack stack){if(stack.isEmpty())return false;var id=ForgeRegistries.ITEMS.getKey(stack.getItem());return id!=null&&(id.toString().equals("tfc:ceramic/ingot_mold")||id.toString().equals("tfc:ceramic/fire_ingot_mold"))&&MoldLike.get(stack.getCount()==1?stack:stack.copyWithCount(1))!=null;}
    private static CastingRecipe recipe(MoldLike mold,FluidStack fluid){
        if(fluid.isEmpty())return null;
        for(var recipe:CastingRecipe.CACHE.getAll(mold.getContainer().getItem()))if(recipe.getIngredient().test(mold.getContainer())&&recipe.getFluidIngredient().ingredient().test(fluid.getFluid()))return recipe;
        return null;
    }
    private ItemStack cooledResult(MoldLike mold,CastingRecipe recipe){
        var trial=mold.getContainer().copy();var copy=MoldLike.get(trial);if(copy==null)return ItemStack.EMPTY;
        copy.setTemperature(0);var result=recipe.assemble(copy,level.registryAccess());HeatCapability.setTemperature(result,0);return result;
    }
    private boolean fits(ItemStack result){var out=inventory.getStackInSlot(1);return !result.isEmpty()&&(out.isEmpty()||ItemHandlerHelper.canItemStacksStack(out,result))&&out.getCount()+result.getCount()<=Math.min(64,result.getMaxStackSize());}
    private void state(int value){status=value;visual();}
    private void visual(){if(level==null||level.isClientSide||dropping)return;var state=level.getBlockState(worldPosition);if(state.getBlock()!=Casting.TABLE.get())return;boolean mold=!inventory.getStackInSlot(0).isEmpty(),active=status==COOLING;if(state.getValue(CastingBlock.MOLD)!=mold||state.getValue(CastingBlock.ACTIVE)!=active)level.setBlock(worldPosition,state.setValue(CastingBlock.MOLD,mold).setValue(CastingBlock.ACTIVE,active),2);}
    public static void serverTick(Level level,BlockPos pos,BlockState state,CastingBlockEntity table){if(table.progress>0||(level.getGameTime()+pos.asLong())%4==0)table.step();}
    /** One native server step; exposed for the isolated integration fixture. */
    public void step(){
        if(level==null||level.isClientSide)return;
        var mold=isMold(inventory.getStackInSlot(0))?MoldLike.get(inventory.getStackInSlot(0)):null;if(mold==null){progress=0;state(NO_MOLD);return;}
        var held=mold.getFluidInTank(0);var recipe=recipe(mold,held);
        if(recipe==null||held.getAmount()<recipe.getFluidIngredient().amount()){
            progress=0;Direction side=getBlockState().getValue(CastingBlock.FACING);var pos=worldPosition.relative(side);
            if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible)){state(NO_CRUCIBLE);return;}
            // This direct neighbor integration does not require TFC's optional pipe/automation ports.
            var source=crucible.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);if(source==null){state(NO_CRUCIBLE);return;}
            var candidate=source.drain(1,IFluidHandler.FluidAction.SIMULATE);
            if(candidate.isEmpty()){state(source.getFluidInTank(0).isEmpty()?(inventory.getStackInSlot(1).isEmpty()?NOT_ENOUGH:READY):COLD);return;}
            if(!held.isEmpty()&&!held.isFluidEqual(candidate)){state(NO_RECIPE);return;}
            recipe=recipe(mold,candidate);if(recipe==null){state(NO_RECIPE);return;}units=recipe.getFluidIngredient().amount();
            int need=units-held.getAmount();if(need<=0){state(NO_RECIPE);return;}
            var available=source.drain(need,IFluidHandler.FluidAction.SIMULATE);
            if(available.getAmount()!=need||!available.isFluidEqual(candidate)){state(NOT_ENOUGH);return;}
            var trialStack=inventory.getStackInSlot(0).copy();var trial=MoldLike.get(trialStack);if(trial==null||trial.fill(available,IFluidHandler.FluidAction.EXECUTE)!=need){state(NO_RECIPE);return;}
            trial.setTemperature(0);var result=recipe.assemble(trial,level.registryAccess());HeatCapability.setTemperature(result,0);
            if(!fits(result)){state(FULL);return;}
            var transferred=FluidUtil.tryFluidTransfer(mold,source,need,true);
            if(!transferred.isEmpty()){mold.setTemperatureIfWarmer(crucible.getTemperature());setChanged();}
            if(transferred.getAmount()!=need){state(NOT_ENOUGH);return;}
            held=mold.getFluidInTank(0);
        }
        units=recipe.getFluidIngredient().amount();if(held.getAmount()<units){state(NOT_ENOUGH);return;}
        var result=cooledResult(mold,recipe);if(!fits(result)){state(FULL);return;}
        state(COOLING);progress++;
        if(progress<CYCLE_TICKS){if(progress%20==0)setChanged();return;}
        // Every material check precedes consumption. A full output leaves the filled mold intact.
        if(mold.drainIgnoringTemperature(units,IFluidHandler.FluidAction.SIMULATE).getAmount()!=units){state(NO_RECIPE);return;}
        mold.setTemperature(0);mold.drainIgnoringTemperature(units,IFluidHandler.FluidAction.EXECUTE);
        var output=inventory.getStackInSlot(1);if(output.isEmpty())inventory.setStackInSlot(1,result);else {output.grow(result.getCount());inventory.setStackInSlot(1,output);}
        progress=0;setChanged();state(READY);
    }
    public void dropContents(){dropping=true;for(int i=0;i<inventory.getSlots();i++){var stack=inventory.getStackInSlot(i);if(!stack.isEmpty())Containers.dropItemStack(level,worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),stack.copy());inventory.setStackInSlot(i,ItemStack.EMPTY);}}
    @Override public Component getDisplayName(){return Component.translatable("block.empirecraft_casting.casting_table");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory player,Player owner){return new CastingMenu(id,player,this);}
    @Override public void load(CompoundTag nbt){super.load(nbt);inventory.deserializeNBT(nbt.getCompound("inventory"));progress=Math.max(0,Math.min(CYCLE_TICKS,nbt.getInt("progress")));}
    @Override protected void saveAdditional(CompoundTag nbt){super.saveAdditional(nbt);nbt.put("inventory",inventory.serializeNBT());nbt.putInt("progress",progress);}
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){if(cap==ForgeCapabilities.ITEM_HANDLER)return (side==Direction.UP?top:output).cast();return super.getCapability(cap,side);}
    @Override public void invalidateCaps(){super.invalidateCaps();top.invalidate();output.invalidate();}
    @Override public void reviveCaps(){super.reviveCaps();top=LazyOptional.of(()->new Port(true));output=LazyOptional.of(()->new Port(false));}
    private final class Port implements IItemHandler {
        private final boolean mold;Port(boolean mold){this.mold=mold;}
        public int getSlots(){return 1;}public ItemStack getStackInSlot(int slot){return slot==0?inventory.getStackInSlot(mold?0:1):ItemStack.EMPTY;}
        public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return slot==0&&mold?inventory.insertItem(0,stack,simulate):stack;}
        public ItemStack extractItem(int slot,int amount,boolean simulate){return slot==0&&!mold?inventory.extractItem(1,amount,simulate):ItemStack.EMPTY;}
        public int getSlotLimit(int slot){return slot==0?(mold?1:64):0;}public boolean isItemValid(int slot,ItemStack stack){return slot==0&&mold&&isMold(stack);}
    }
}
