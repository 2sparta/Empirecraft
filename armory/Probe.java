package ua.empirecraft.armorytest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.stirdrem.overgeared.block.entity.AbstractSmithingAnvilBlockEntity;
import net.stirdrem.overgeared.recipe.ForgingRecipe;
import ua.empirecraft.armory.*;

@Mod("empirecraft_armory_probe")
public final class Probe {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public Probe(){MinecraftForge.EVENT_BUS.addListener(this::start);}
    static Item item(String id){var item=ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));check(item!=null&&item!=Items.AIR,"Registered item "+id);return item;}
    static void clear(WorkshopEntity table){for(int i=0;i<WorkshopEntity.SLOTS;i++)table.inventory.setStackInSlot(i,ItemStack.EMPTY);}
    static void supply(WorkshopEntity table,AssemblyRecipe r,int stage){
        for(int i=WorkshopEntity.FIRST;i<WorkshopEntity.SLOTS;i++)table.inventory.setStackInSlot(i,ItemStack.EMPTY);
        for(var ingredient:r.stage(stage)){
            check(ingredient.getItems().length>0,"No empty assembly ingredient "+r.id);var sample=ingredient.getItems()[0].copy();
            if(sample.getItem() instanceof PartItem)sample=Parts.sample(sample.getItem());
            if(sample.getItem() instanceof ArmorItem){sample.setHoverName(Component.literal("Inherited name"));sample.getOrCreateTag().putString("custom_test","retained");sample.getOrCreateTag().putString("ForgingQuality","master");sample.setDamageValue(sample.getMaxDamage()/3);}
            boolean placed=false;
            for(int i=WorkshopEntity.FIRST;i<WorkshopEntity.SLOTS;i++){var old=table.inventory.getStackInSlot(i);if(old.isEmpty()){table.inventory.setStackInSlot(i,sample);placed=true;break;}if(ItemStack.isSameItemSameTags(old,sample)&&old.getCount()<old.getMaxStackSize()){old.grow(1);placed=true;break;}}
            check(placed,"Stage fits supply slots "+r.id);
        }
    }
    void start(ServerStartedEvent event){var server=event.getServer();
        try{
            var level=server.overworld();var pos=new BlockPos(1600,100,1600);level.getChunkAt(pos);level.setBlockAndUpdate(pos,Armory.TABLE.get().defaultBlockState());var table=(WorkshopEntity)level.getBlockEntity(pos);var player=FakePlayerFactory.getMinecraft(level);player.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
            var all=AssemblyRecipe.all(level);check(all.size()>50,"Dynamic assembly catalog is populated: "+all.size());
            Set<String> constructions=new TreeSet<>(),namespaces=new TreeSet<>();int count=0;
            for(var r:all){
                check(r.result.getItem() instanceof ArmorItem,"Assembly result is armor");constructions.add(r.profile.construction());namespaces.add(ForgeRegistries.ITEMS.getKey(r.result.getItem()).getNamespace());
                var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());Armory.SERIALIZER.get().toNetwork(buffer,r);var copy=Armory.SERIALIZER.get().fromNetwork(r.id,buffer);check(copy.result.getItem()==r.result.getItem()&&copy.profile.equals(r.profile),"Recipe network round trip "+r.id);buffer.release();
                clear(table);check(table.select(all.indexOf(r)),"Select "+r.id);
                for(int stage=0;stage<4;stage++){
                    supply(table,r,stage);check(table.perform(0,player),"Assemble stage "+stage+" "+r.id+" status="+table.data.get(2));
                    if(stage<3){check(table.currentStage()==stage+1,"Stage is persisted");var work=table.inventory.getStackInSlot(0);var restored=ItemStack.of(work.save(new CompoundTag()));check(restored.getTag().getInt("stage")==stage+1&&restored.getTag().getString("recipe").equals(r.id.toString()),"Portable unfinished work preserves progress");table.inventory.setStackInSlot(0,restored);}
                }
                var result=table.inventory.getStackInSlot(2);check(result.getItem()==r.result.getItem(),"Exact model output "+r.id);check(!result.getTag().getCompound(Parts.DATA).isEmpty(),"Installed parts retained");
                if(r.profile.items().stream().anyMatch(PartItem.class::isInstance))check(Parts.quality(result)==2,"Quality inherited from critical parts");
                if(!r.base.isEmpty()){check(result.hasCustomHoverName()&&result.getTag().getString("custom_test").equals("retained"),"Upgrade retains base NBT "+r.id);if(result.isDamageableItem())check(Math.abs((double)result.getDamageValue()/Math.max(1,result.getMaxDamage())-1.0/3)<.03,"Wear fraction survives a quality change "+r.id);}
                count++;
            }
            check(constructions.containsAll(Set.of("plate","mail","lamellar","brigandine","fluted")),"All construction families covered "+constructions);
            var r=all.stream().filter(x->x.result.is(Items.IRON_CHESTPLATE)&&x.base.isEmpty()).findFirst().orElseThrow();clear(table);table.select(all.indexOf(r));
            supply(table,r,0);check(table.perform(0,player),"Start transaction fixture");supply(table,r,1);var before=table.inventory.serializeNBT().copy();
            // The finish action is authoritative and a caller cannot skip required stages.
            for(int i=WorkshopEntity.FIRST;i<WorkshopEntity.SLOTS;i++)table.inventory.setStackInSlot(i,ItemStack.EMPTY);
            var missing=table.inventory.serializeNBT().copy();check(!table.perform(0,player)&&table.inventory.serializeNBT().equals(missing),"Missing components do not consume or advance work");
            supply(table,r,1);var saved=table.saveWithFullMetadata();var loaded=(WorkshopEntity)BlockEntity.loadStatic(pos,table.getBlockState(),saved);loaded.setLevel(level);check(loaded.currentStage()==1&&loaded.inventory.serializeNBT().equals(table.inventory.serializeNBT()),"Table survives save/reload with every component");
            check(loaded.perform(0,player)&&loaded.currentStage()==2,"Reloaded work can continue");
            clear(table);var steel=Parts.sample(Armory.part("steel","front"));steel.getOrCreateTag().putInt(Parts.TREATMENT,0);table.inventory.setStackInSlot(1,steel);HeatCapability.setTemperature(steel,600);check(!table.perform(1,player),"Cold steel cannot harden");HeatCapability.setTemperature(steel,900);check(!table.perform(1,player),"No free hardening without water");level.setBlockAndUpdate(pos.east(),Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,3));check(table.perform(1,player),"Hot steel hardens with water");check(Parts.treatment(steel)==1&&HeatCapability.getTemperature(steel)==0&&level.getBlockState(pos.east()).getValue(LayeredCauldronBlock.LEVEL)==2,"Hardening consumes one water level and cools the same item");
            check(!table.perform(2,player),"Cold hardened steel cannot temper");HeatCapability.setTemperature(steel,350);check(table.perform(2,player)&&Parts.ready(steel),"Tempering completes treatment");
            check(HeatCapability.get(steel)!=null,"Parts have actual TFC heat capability");var hot=ItemStack.of(steel.save(new CompoundTag()));check(Parts.treatment(hot)==2,"Heat treatment survives item save");
            var hammer=new ItemStack(item("overgeared:smithing_hammer"));var blueprint=new ItemStack(item("overgeared:blueprint"));blueprint.getOrCreateTag().putString("ToolType","armor");blueprint.getOrCreateTag().putString("Quality","perfect");blueprint.getOrCreateTag().putInt("Uses",3);table.inventory.setStackInSlot(3,hammer);table.inventory.setStackInSlot(4,blueprint);HeatCapability.setTemperature(steel,1100);check(table.perform(3,player),"Warm component can be reworked with a better blueprint");check(Parts.quality(steel)==3&&Parts.treatment(steel)==0&&hammer.getDamageValue()==1&&blueprint.getTag().getInt("Uses")==3,"Rework costs hammer wear, resets treatment and grants no research");
            // Repairs keep names and NBT and charge metal in proportion to the replaced part.
            clear(table);var old=new ItemStack(Items.IRON_CHESTPLATE);old.setDamageValue(120);old.setHoverName(Component.literal("Old named armor"));old.getOrCreateTag().putString("custom_test","legacy");table.inventory.setStackInSlot(0,old);table.inventory.setStackInSlot(3,Parts.sample(Armory.part("iron","front")));table.inventory.setStackInSlot(4,new ItemStack(Armory.PARTS.get("rivets").get(),2));check(table.perform(4,player),"Legacy armor accepts component repair");var repaired=table.inventory.getStackInSlot(2);check(repaired.getDamageValue()<120&&repaired.hasCustomHoverName()&&repaired.getTag().getString("custom_test").equals("legacy"),"Legacy repair preserves identity");check(table.inventory.getStackInSlot(3).isEmpty()&&table.inventory.getStackInSlot(4).isEmpty(),"Repair consumes exactly one part and two rivets");
            // Every component recipe is live, unique, correctly priced and executable on a native anvil.
            int parts=0;for(var recipe:server.getRecipeManager().getRecipes())if(recipe instanceof ForgingRecipe f&&recipe.getResultItem(server.registryAccess()).getItem() instanceof PartItem p){parts++;check(f.getHammeringRequired()>=3&&f.getHammeringRequired()<=4,"Short native forging cycle");check(f.getBlueprintTypes().contains("armor"),"Existing armor blueprints apply");for(var ingredient:f.getIngredients())check(ingredient.isEmpty()||ingredient.getItems().length>0,"No missing native component ingredients "+recipe.getId());}
            check(parts==Armory.METALS.length*Armory.FORMS.size(),"Every registered metal/form has a live recipe: "+parts);
            var anvilPos=pos.west(3);var block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("overgeared:smithing_anvil"));check(block!=null,"Metal anvil registry id");level.setBlockAndUpdate(anvilPos,block.defaultBlockState());var anvil=(AbstractSmithingAnvilBlockEntity)level.getBlockEntity(anvilPos);var inv=(net.minecraftforge.items.IItemHandlerModifiable)anvil.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new AssertionError("Item capability"));
            var component=(ForgingRecipe)server.getRecipeManager().byKey(new ResourceLocation("empirecraft_armory:parts/iron_front")).orElseThrow();
            for(int y=0;y<component.height;y++)for(int x=0;x<component.width;x++){var ingredient=component.getForgingIngredients().get(y*component.width+x);if(ingredient.ingredient().isEmpty())continue;var stack=ingredient.ingredient().getItems()[0].copy();HeatCapability.setTemperature(stack,1200);inv.setStackInSlot(y*3+x,stack);}
            var recipe=anvil.getCurrentRecipe();check(recipe.isPresent()&&recipe.get().getId().equals(component.getId()),"Native anvil selects the exact pattern recipe");var craft=AbstractSmithingAnvilBlockEntity.class.getDeclaredMethod("craftItem");craft.setAccessible(true);craft.invoke(anvil);check(inv.getStackInSlot(10).getItem()==Armory.part("iron","front"),"Native forge produces the component");check(HeatCapability.getTemperature(inv.getStackInSlot(10))>900,"Native forged component retains heat");boolean patternKept=false;for(int i=0;i<9;i++)patternKept|=inv.getStackInSlot(i).getItem() instanceof PatternItem;check(patternKept,"Native crafting leaves the forming pattern reusable");
            var port=table.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new AssertionError("Item capability"));check(port.extractItem(1,64,true).isEmpty(),"Automation cannot extract unconsumed supplies or workpieces");
            var grid=new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu,2,2);var damaged=new ItemStack(Items.IRON_CHESTPLATE);damaged.getOrCreateTag().putString("ForgingQuality","poor");damaged.setDamageValue(damaged.getMaxDamage()-1);grid.setItem(0,damaged.copy());grid.setItem(1,damaged.copy());var combined=new RepairItemRecipe(new ResourceLocation("armory_test:repair"),CraftingBookCategory.MISC).assemble(grid,server.registryAccess());check(combined.hasTag()&&combined.getTag().getString("ForgingQuality").equals("poor"),"Vanilla combining cannot wash poor quality into baseline armor");
            for(var candidate:all)if(candidate.result.is(Items.DIAMOND_CHESTPLATE))check(candidate.extras.stream().anyMatch(i->i.test(new ItemStack(Items.DIAMOND))),"Diamond costs remain in the foundation stage");
            check(AssemblyCatalog.install(server)==0,"Catalog installation is idempotent");
            int finished=count,finishedParts=parts;
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((nothing,error)->server.execute(()->{try{if(error!=null)throw new RuntimeException(error);check(AssemblyRecipe.all(level).size()==finished,"Reload retains every assembly recipe");check(AssemblyCatalog.install(server)==0,"Reload already converted recipes before synchronization");System.out.println("ARMORY_NATIVE_PASS checks="+checks+" recipes="+finished+" parts="+finishedParts+" constructions="+constructions+" namespaces="+namespaces+" reload=true");}catch(Throwable t){System.out.println("ARMORY_NATIVE_FAIL "+t);t.printStackTrace();}finally{server.halt(false);}}));
        }catch(Throwable t){System.out.println("ARMORY_NATIVE_FAIL "+t);t.printStackTrace();server.halt(false);}
    }
}
