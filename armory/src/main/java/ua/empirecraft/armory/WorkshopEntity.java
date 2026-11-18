package ua.empirecraft.armory;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;

/** All transactions are validated on the server; stored workpieces own their installed parts. */
public final class WorkshopEntity extends BlockEntity implements MenuProvider {
    public static final int WORK=0,PROCESS=1,OUTPUT=2,FIRST=3,SLOTS=15;
    public static final int OK=0,MISSING=1,WRONG_WORK=2,UNREADY=3,FULL=4,COLD=5,NO_WATER=6,NO_HAMMER=7,NO_BLUEPRINT=8,BUSY=9,DONE=10,INVALID=11;
    private ResourceLocation selection;
    private int stage,status,remaining,action=-1;
    private UUID operator;
    private boolean committing;
    public final ItemStackHandler inventory=new ItemStackHandler(SLOTS){
        @Override public int getSlotLimit(int slot){return slot<FIRST?1:64;}
        @Override public boolean isItemValid(int slot,ItemStack s){return slot==WORK?s.getItem()==Armory.WORK.get()||s.getItem() instanceof ArmorItem:slot==PROCESS?s.getItem() instanceof PartItem:slot!=OUTPUT;}
        @Override protected void onContentsChanged(int slot){if(!committing){remaining=0;action=-1;}setChanged();}
    };
    private LazyOptional<IItemHandler> capability=LazyOptional.of(()->new SidedInvWrapper());
    public final ContainerData data=new ContainerData(){
        public int get(int i){return switch(i){case 0->selectedIndex();case 1->currentStage();case 2->status;case 3->remaining;default->0;};}
        public void set(int i,int v){} public int getCount(){return 4;}
    };
    public WorkshopEntity(BlockPos pos,BlockState state){super(Armory.ENTITY.get(),pos,state);}
    public int currentStage(){var work=inventory.getStackInSlot(WORK);return work.is(Armory.WORK.get())&&work.hasTag()?Math.max(0,Math.min(4,work.getTag().getInt("stage"))):0;}
    public AssemblyRecipe recipe(){if(level==null)return null;var work=inventory.getStackInSlot(WORK);String key=work.is(Armory.WORK.get())&&work.hasTag()?work.getTag().getString("recipe"):selection==null?"":selection.toString();var id=ResourceLocation.tryParse(key);return id==null?null:level.getRecipeManager().byKey(id).filter(AssemblyRecipe.class::isInstance).map(AssemblyRecipe.class::cast).orElse(null);}
    private int selectedIndex(){if(level==null)return -1;var current=recipe();var list=AssemblyRecipe.all(level);for(int i=0;i<list.size();i++)if(current!=null&&list.get(i).id.equals(current.id))return i;return -1;}
    public boolean select(int index){var all=AssemblyRecipe.all(level);if(index<0||index>=all.size()||!inventory.getStackInSlot(WORK).isEmpty())return false;selection=all.get(index).id;status=OK;remaining=0;action=-1;setChanged();return true;}
    public boolean request(int request,Player player){
        if(level==null||level.isClientSide||player.isSpectator()||player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)>64||level.getBlockEntity(worldPosition)!=this)return false;
        if(request<0||request>4)return false;
        if(remaining>0){status=BUSY;return false;}
        if(request==0&&recipe()==null){status=INVALID;return false;}
        action=request;operator=player.getUUID();remaining=40;status=OK;setChanged();return true;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,WorkshopEntity table){
        if(table.remaining<=0)return;
        var player=level.getPlayerByUUID(table.operator);
        if(player==null||player.isSpectator()||player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64){table.remaining=0;table.action=-1;table.setChanged();return;}
        if(--table.remaining==0){table.perform(table.action,player);table.action=-1;table.setChanged();}
    }
    /** Also used by native integration tests; normal gameplay always enters through request/tick. */
    public boolean perform(int request,Player player){
        if(level==null||level.isClientSide)return false;
        boolean success=request==0?assemble(player):request==4?repair():process(request,player);
        if(success){status=DONE;level.playSound(null,worldPosition,SoundEvents.ANVIL_USE,SoundSource.BLOCKS,.6f,1.2f);}setChanged();return success;
    }
    /** An exact allocation prevents repeated ingredients from counting the same stack twice. */
    public int[] allocate(List<Ingredient> needs,boolean ready){
        int[] used=new int[SLOTS];
        for(Ingredient need:needs){boolean found=false;for(int i=FIRST;i<SLOTS;i++){var s=inventory.getStackInSlot(i);if(s.getCount()>used[i]&&need.test(s)&&(!ready||Parts.ready(s))){used[i]++;found=true;break;}}if(!found)return null;}
        return used;
    }
    private void consume(int[] allocation){for(int i=FIRST;i<SLOTS;i++)if(allocation[i]>0)inventory.extractItem(i,allocation[i],false);}
    private boolean assemble(Player player){
        var r=recipe();if(r==null){status=INVALID;return false;}
        selection=r.id;
        var work=inventory.getStackInSlot(WORK);if(!work.isEmpty()&&!work.is(Armory.WORK.get())){status=WRONG_WORK;return false;}
        int step=currentStage();if(step>=4){status=INVALID;return false;}
        int[] used=allocate(r.stage(step),true);if(used==null){status=allocate(r.stage(step),false)==null?MISSING:UNREADY;return false;}
        if(step==3&&!inventory.getStackInSlot(OUTPUT).isEmpty()){status=FULL;return false;}
        ItemStack next=work.isEmpty()?new ItemStack(Armory.WORK.get()):work.copy();var tag=next.getOrCreateTag();
        if(step==0){tag.putString("recipe",r.id.toString());tag.putString("targetName",r.result.getDescriptionId());tag.putInt("stages",4);tag.put("parts",new ListTag());tag.put(ArmorProvenance.KEY,ArmorProvenance.capture(player,worldPosition,r.profile.metal()));}
        var installed=tag.getList("parts",Tag.TAG_COMPOUND);
        for(int i=FIRST;i<SLOTS;i++)if(used[i]>0){var s=inventory.getStackInSlot(i);if(s.getItem() instanceof ArmorItem&&r.base.test(s))tag.put("base",s.copyWithCount(1).save(new CompoundTag()));if(s.getItem() instanceof PartItem)for(int n=0;n<used[i];n++)installed.add(Parts.partRecord(s));}
        tag.put("parts",installed);tag.putInt("stage",step+1);
        committing=true;
        try{
            consume(used);
            if(step<3)inventory.setStackInSlot(WORK,next);
            else{
                ItemStack result=r.result.copy();
                double wear=0;
                if(tag.contains("base")){var base=ItemStack.of(tag.getCompound("base"));if(base.isDamageableItem())wear=(double)base.getDamageValue()/Math.max(1,base.getMaxDamage());if(base.hasTag())result.setTag(base.getTag().copy());}
                var record=new CompoundTag();record.putString("recipe",r.id.toString());record.put("parts",installed.copy());
                record.put(ArmorProvenance.KEY,tag.contains(ArmorProvenance.KEY,Tag.TAG_COMPOUND)
                    ? tag.getCompound(ArmorProvenance.KEY).copy() : ArmorProvenance.capture(player,worldPosition,r.profile.metal()));
                result.getOrCreateTag().put(Parts.DATA,record);applyQuality(result,installed);
                if(result.isDamageableItem())result.setDamageValue((int)Math.floor(wear*result.getMaxDamage()));
                inventory.setStackInSlot(WORK,ItemStack.EMPTY);inventory.setStackInSlot(OUTPUT,result);
            }
        }finally{committing=false;}
        return true;
    }
    private static void applyQuality(ItemStack result,ListTag records){int minimum=4;boolean found=false;for(int i=0;i<records.size();i++){int quality=Parts.QUALITY.indexOf(records.getCompound(i).getString("quality"));if(quality>=0){minimum=Math.min(minimum,quality);found=true;}}if(found)result.getOrCreateTag().putString("ForgingQuality",Parts.QUALITY.get(minimum));}
    private boolean process(int operation,Player player){
        var s=inventory.getStackInSlot(PROCESS);if(!(s.getItem() instanceof PartItem part)){status=MISSING;return false;}float t=HeatCapability.getTemperature(s);
        if(operation==1){
            if(!Parts.steel(part.metal)||Parts.treatment(s)!=0){status=INVALID;return false;}
            if(t<800||t>1050){status=COLD;return false;}
            BlockPos water=null;for(Direction d:Direction.values()){var p=worldPosition.relative(d);if(level.hasChunkAt(p)&&level.getBlockState(p).is(Blocks.WATER_CAULDRON)){water=p;break;}}
            if(water==null){status=NO_WATER;return false;}
            var state=level.getBlockState(water);int fill=state.getValue(LayeredCauldronBlock.LEVEL);level.setBlockAndUpdate(water,fill==1?Blocks.CAULDRON.defaultBlockState():state.setValue(LayeredCauldronBlock.LEVEL,fill-1));s.getOrCreateTag().putInt(Parts.TREATMENT,1);HeatCapability.setTemperature(s,0);return true;
        }
        if(operation==2){if(!Parts.steel(part.metal)||Parts.treatment(s)!=1){status=INVALID;return false;}if(t<250||t>450){status=COLD;return false;}s.getOrCreateTag().putInt(Parts.TREATMENT,2);HeatCapability.setTemperature(s,0);return true;}
        if(operation==3){
            if(t<Parts.melt(part.metal)*.6f||t>=Parts.melt(part.metal)){status=COLD;return false;}
            ItemStack blueprint=ItemStack.EMPTY,hammer=ItemStack.EMPTY;
            for(int i=FIRST;i<SLOTS;i++){var candidate=inventory.getStackInSlot(i);var key=ForgeRegistries.ITEMS.getKey(candidate.getItem());if(key==null)continue;if(key.getNamespace().equals("overgeared")&&key.getPath().contains("blueprint")&&candidate.hasTag()&&Parts.blueprints(part.form).contains(candidate.getTag().getString("ToolType")))blueprint=candidate;if(candidate.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation("overgeared:smithing_hammers")))&&candidate.isDamageableItem()&&candidate.getDamageValue()<candidate.getMaxDamage())hammer=candidate;}
            if(hammer.isEmpty()){status=NO_HAMMER;return false;}if(blueprint.isEmpty()){status=NO_BLUEPRINT;return false;}
            int cap=Parts.QUALITY.indexOf(blueprint.getTag().getString("Quality"));if(cap<0||Parts.quality(s)>=cap){status=NO_BLUEPRINT;return false;}
            s.getOrCreateTag().putString("ForgingQuality",Parts.QUALITY.get(Math.min(cap,Parts.quality(s)+1)));s.getOrCreateTag().putInt(Parts.TREATMENT,0);hammer.hurtAndBreak(1,player,p->{});return true;
        }
        status=INVALID;return false;
    }
    private boolean repair(){
        var armor=inventory.getStackInSlot(WORK);if(!(armor.getItem() instanceof ArmorItem)||!armor.isDamaged()||!inventory.getStackInSlot(OUTPUT).isEmpty()){status=WRONG_WORK;return false;}
        AssemblyRecipe r=null;if(armor.hasTag()&&armor.getTag().contains(Parts.DATA)){String key=armor.getTag().getCompound(Parts.DATA).getString("recipe");var id=ResourceLocation.tryParse(key);if(id!=null)r=level.getRecipeManager().byKey(id).filter(AssemblyRecipe.class::isInstance).map(AssemblyRecipe.class::cast).orElse(null);}if(r==null)for(var candidate:AssemblyRecipe.all(level))if(candidate.result.getItem()==armor.getItem()){r=candidate;break;}if(r==null){status=INVALID;return false;}
        boolean padded=r.profile.construction().equals("padded");
        int replacement=-1;for(int i=FIRST;i<SLOTS;i++)if(r.profile.items().contains(inventory.getStackInSlot(i).getItem())&&Parts.ready(inventory.getStackInSlot(i))&&(padded||inventory.getStackInSlot(i).getItem() instanceof PartItem)){replacement=i;break;}if(replacement<0){status=MISSING;return false;}
        var component=inventory.getStackInSlot(replacement);int[] used=new int[SLOTS];used[replacement]=1;
        boolean rivet=false;Item fastener=padded?Items.STRING:Armory.PARTS.get("rivets").get();for(int i=FIRST;i<SLOTS;i++)if(inventory.getStackInSlot(i).is(fastener)&&inventory.getStackInSlot(i).getCount()>=2){used[i]+=2;rivet=true;break;}if(!rivet){status=MISSING;return false;}
        var result=armor.copy();
        if(padded){int restored=Math.max(1,result.getMaxDamage()/Math.max(1,r.profile.forms().size()));result.setDamageValue(Math.max(0,result.getDamageValue()-restored));committing=true;try{consume(used);inventory.setStackInSlot(WORK,ItemStack.EMPTY);inventory.setStackInSlot(OUTPUT,result);}finally{committing=false;}return true;}
        ListTag records;
        if(result.hasTag()&&result.getTag().contains(Parts.DATA))records=result.getTag().getCompound(Parts.DATA).getList("parts",Tag.TAG_COMPOUND).copy();else{records=new ListTag();for(Item item:r.profile.items())if(item instanceof PartItem){var old=new ItemStack(item);old.getOrCreateTag().putString("ForgingQuality",Parts.QUALITY.get(armor.hasTag()&&armor.getTag().contains("ForgingQuality")?Parts.quality(armor):2));records.add(Parts.partRecord(old));}}
        String key=ForgeRegistries.ITEMS.getKey(component.getItem()).toString();int chosen=-1,quality=99;for(int i=0;i<records.size();i++)if(records.getCompound(i).getString("id").equals(key)){int q=Parts.QUALITY.indexOf(records.getCompound(i).getString("quality"));if(q<quality){quality=q;chosen=i;}}if(chosen<0){status=INVALID;return false;}
        records.set(chosen,Parts.partRecord(component));int amount=Armory.FORMS.get(((PartItem)component.getItem()).form);int restored=Math.max(1,result.getMaxDamage()*amount/Math.max(1,r.profile.metalCost()));double damageFraction=(double)Math.max(0,result.getDamageValue()-restored)/Math.max(1,result.getMaxDamage());var data=result.getOrCreateTag().getCompound(Parts.DATA);data.putString("recipe",r.id.toString());data.put("parts",records);result.getOrCreateTag().put(Parts.DATA,data);applyQuality(result,records);result.setDamageValue((int)Math.floor(damageFraction*result.getMaxDamage()));
        committing=true;try{consume(used);inventory.setStackInSlot(WORK,ItemStack.EMPTY);inventory.setStackInSlot(OUTPUT,result);}finally{committing=false;}return true;
    }
    public void dropContents(){committing=true;try{for(int i=0;i<SLOTS;i++){var s=inventory.getStackInSlot(i);if(!s.isEmpty())Containers.dropItemStack(level,worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),s.copy());inventory.setStackInSlot(i,ItemStack.EMPTY);}}finally{committing=false;}}
    @Override public Component getDisplayName(){return Component.translatable("block.empirecraft_armory.armorer_table");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new WorkshopMenu(id,inv,this);}
    @Override public void load(CompoundTag tag){super.load(tag);inventory.deserializeNBT(tag.getCompound("inventory"));selection=ResourceLocation.tryParse(tag.getString("selection"));remaining=0;action=-1;}
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);tag.put("inventory",inventory.serializeNBT());if(selection!=null)tag.putString("selection",selection.toString());}
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side){if(cap==ForgeCapabilities.ITEM_HANDLER)return capability.cast();return super.getCapability(cap,side);}
    @Override public void invalidateCaps(){super.invalidateCaps();capability.invalidate();}
    @Override public void reviveCaps(){super.reviveCaps();capability=LazyOptional.of(()->new SidedInvWrapper());}
    private final class SidedInvWrapper implements IItemHandler {
        public int getSlots(){return SLOTS-FIRST+1;}
        private int real(int slot){if(slot<0||slot>=getSlots())throw new IndexOutOfBoundsException(slot);return slot==0?OUTPUT:slot+FIRST-1;}
        public ItemStack getStackInSlot(int slot){return inventory.getStackInSlot(real(slot));}
        public ItemStack insertItem(int slot,ItemStack s,boolean simulate){return slot==0?s:inventory.insertItem(real(slot),s,simulate);}
        public ItemStack extractItem(int slot,int amount,boolean simulate){return slot==0?inventory.extractItem(OUTPUT,amount,simulate):ItemStack.EMPTY;}
        public int getSlotLimit(int slot){return slot==0?1:64;}
        public boolean isItemValid(int slot,ItemStack s){return slot!=0;}
    }
}
