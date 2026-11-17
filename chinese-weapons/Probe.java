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
    void ottoman(net.minecraft.server.MinecraftServer server) throws Exception {
        String ns="hamzabus_ottoman_mod:";var level=server.overworld();var registry=ForgeRegistries.ITEMS;
        check(!registry.containsKey(new ResourceLocation(ns+"steel_ingot"))&&!registry.containsKey(new ResourceLocation(ns+"steel_nugget")),"Ottoman duplicate steel is physically absent");
        var original=Class.forName("net.mcreator.hamzabusottomanmod.init.HamzabusOttomanModModItems");
        for(String name:List.of("INGOT","NUGGET")){var alias=(net.minecraftforge.registries.RegistryObject<?>)original.getField("STEEL_"+name).get(null);check(alias.get()==item("overgeared:steel_"+name.toLowerCase()),"Native repair fields alias canonical steel");}
        for(String id:List.of("steel_ingot_recipe","steelnugget_recipe","reversesteel"))check(server.getRecipeManager().byKey(new ResourceLocation(ns+id)).isEmpty(),"Removed steel recipe "+id);
        var profile=Class.forName("ua.empirecraft.forging.GunsmithRules").getMethod("profile",String.class);
        check(profile.invoke(null,ns+"shishak_helmet").equals("plate"),"Shishak uses PR149 plate protection");
        for(var i:registry.getValues())if(i instanceof ArmorItem&&registry.getKey(i).getNamespace().equals("hamzabus_ottoman_mod")){
            String id=registry.getKey(i).toString();if(!id.endsWith(":shishak_helmet"))check(profile.invoke(null,id).equals("padded"),"Textile specialization "+id);
            check(new ItemStack(i).is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation("empirecraft_armor:"+(id.endsWith(":shishak_helmet")?"outer":"padded")))),"Ottoman armor layer tag "+id);
        }
        int weapons=0;for(var recipe:server.getRecipeManager().getRecipes())if(recipe instanceof ForgingRecipe f&&registry.getKey(f.getResultItem(server.registryAccess()).getItem()).getNamespace().equals("hamzabus_ottoman_mod")){
            weapons++;for(var in:f.getIngredients())check(in.isEmpty()||in.getItems().length>0,"Ottoman forge ingredient exists "+f.getId());check(f.getHammeringRequired()==4,"Ottoman weapon uses simple four-hit forging");
            var stack=f.getResultItem(server.registryAccess()).copy();if(!(stack.getItem() instanceof ShieldItem))check(HeatCapability.get(stack)!=null,"Ottoman weapon has TFC heat "+f.getId());
        }check(weapons==6,"Six Ottoman weapon/shield forging routes: "+weapons);
        var player=FakePlayerFactory.getMinecraft(level);var victim=new net.minecraft.world.entity.monster.Zombie(level);victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
        var topuz=new ItemStack(item(ns+"topuz"));int wear=victim.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getDamageValue();topuz.hurtEnemy(victim,player);check(victim.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getDamageValue()==wear,"Topuz has no special armor durability destruction");check(topuz.getDamageValue()<=2,"Topuz retains normal weapon durability cost");
        var hurt=Class.forName("ua.empirecraft.forging.Gunsmith").getDeclaredMethod("hurt",net.minecraftforge.event.entity.living.LivingHurtEvent.class);hurt.setAccessible(true);
        for(var slot:net.minecraft.world.entity.EquipmentSlot.values())if(slot.getType()==net.minecraft.world.entity.EquipmentSlot.Type.ARMOR)player.setItemSlot(slot,ItemStack.EMPTY);
        victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,topuz);player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(item(ns+"shishak_helmet")));
        var blunt=new net.minecraftforge.event.entity.living.LivingHurtEvent(player,level.damageSources().mobAttack(victim),10);hurt.invoke(null,blunt);check(Math.abs(blunt.getAmount()-10.4)<.001,"Topuz uses shared weighted plate blunt weakness");
        victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(item(ns+"yatagan")));player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(item(ns+"fez_helmet")));
        var cut=new net.minecraftforge.event.entity.living.LivingHurtEvent(player,level.damageSources().mobAttack(victim),10);hurt.invoke(null,cut);check(Math.abs(cut.getAmount()-9.8)<.001,"Yatagan uses shared weighted padded cut resistance");
        for(var slot:net.minecraft.world.entity.EquipmentSlot.values())player.setItemSlot(slot,ItemStack.EMPTY);
        var shield=new ItemStack(item(ns+"ottoman_shield"));check(shield.getItem() instanceof ShieldItem,"Kalkan supports ordinary held directional shield blocking");check(!registry.containsKey(new ResourceLocation(ns+"ottoman_shield_blocking")),"No inventory-swapping blocking item");
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,shield);var attack=new net.minecraftforge.event.entity.living.LivingAttackEvent(player,level.damageSources().mobAttack(victim),8);
        Class.forName("net.mcreator.hamzabusottomanmod.procedures.WhenShieldAttackedProcedure").getMethod("onEntityAttacked",net.minecraftforge.event.entity.living.LivingAttackEvent.class).invoke(null,attack);check(!attack.isCanceled(),"Unraised kalkan grants no global invulnerability");
        player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);victim.setDeltaMovement(0,0,0);var block=net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("m_6728_",net.minecraft.world.entity.LivingEntity.class);block.setAccessible(true);block.invoke(player,victim);check(victim.getDeltaMovement().lengthSqr()==0,"Kalkan never applies shield knockback");player.stopUsingItem();player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
        // Both shaped and shapeless recoloring must retain the entire assembled item state.
        for(String id:List.of("whitetoredrecipe","whitetogreen")){
            var recipe=server.getRecipeManager().byKey(new ResourceLocation(ns+id)).orElseThrow();check(RestyleRecipes.wrapped(recipe),"Dye recipe remains a native crafting-grid route "+id);
            var grid=new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu,3,3);var source=new ItemStack(item(ns+"yelek_chestplate"));source.setHoverName(Component.literal("Family heirloom"));source.getOrCreateTag().putString("ForgingQuality","poor");source.getOrCreateTag().putString("custom_test","cloth");source.getOrCreateTag().put(Parts.DATA,new CompoundTag());source.setDamageValue(source.getMaxDamage()/2);
            int n=0;for(var ingredient:recipe.getIngredients())if(!ingredient.isEmpty()){grid.setItem(n++,ingredient.test(source)?source:ingredient.getItems()[0].copy());}
            @SuppressWarnings("unchecked") var crafting=(Recipe<net.minecraft.world.inventory.CraftingContainer>)recipe;var result=crafting.assemble(grid,server.registryAccess());check(result.hasCustomHoverName()&&result.getTag().getString("custom_test").equals("cloth")&&result.getTag().getString("ForgingQuality").equals("poor")&&result.getTag().contains(Parts.DATA),"Dye preserves assembled armor identity and quality "+id);check(Math.abs((double)result.getDamageValue()/result.getMaxDamage()-.5)<.05,"Dye preserves wear fraction "+id);
            var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());@SuppressWarnings("unchecked") var serializer=(RecipeSerializer<Recipe<?>>)recipe.getSerializer();serializer.toNetwork(buffer,recipe);var copy=serializer.fromNetwork(recipe.getId(),buffer);check(RestyleRecipes.wrapped(copy),"Dye serializer network round trip "+id);buffer.release();
        }
    }
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
    void chinese(net.minecraft.server.MinecraftServer server)throws Exception {
        String ns="chineseweapons:";var level=server.overworld();var all=AssemblyRecipe.all(level);int armor=0,horse=0,weapon=0;
        for(var i:ForgeRegistries.ITEMS.getValues())if(ForgeRegistries.ITEMS.getKey(i).getNamespace().equals("chineseweapons")){
            String id=ForgeRegistries.ITEMS.getKey(i).toString();var stack=new ItemStack(i);
            check(!id.contains("ancient_gem")&&!id.contains("casting_table")&&!id.contains("armor_pieces")&&!id.endsWith("forging_stone"),"Removed Chinese material/item is not registered "+id);
            if(i instanceof ArmorItem){armor++;check(!id.startsWith(ns+"golden_")&&!id.startsWith(ns+"diamond_")&&!id.startsWith(ns+"netherite_"),"Only realistic Chinese armor remains");check(all.stream().anyMatch(r->r.result.getItem()==i),"Armor has actual workbench route "+id);}
            if(i instanceof HorseArmorItem){horse++;check(all.stream().anyMatch(r->r.result.getItem()==i),"Horse armor has actual workbench route");}
            check(!stack.isEnchantable()&&i.getEnchantmentValue()==0,"Enchanting disabled "+id);stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,3);check(stack.getEnchantmentTags().isEmpty()&&!stack.isEnchanted(),"Stored enchantments do not affect new equipment "+id);
            check(!i.isBookEnchantable(stack,new ItemStack(Items.ENCHANTED_BOOK)),"Enchanted books blocked "+id);
            if(i instanceof TieredItem){weapon++;var victim=new net.minecraft.world.entity.monster.Zombie(level);var attacker=FakePlayerFactory.getMinecraft(level);stack.hurtEnemy(victim,attacker);check(victim.getActiveEffects().isEmpty()&&attacker.getActiveEffects().isEmpty(),"No hit status effects "+id);var attrs=stack.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND);check(attrs.get(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()).size()<=1,"One shared reach attribute only "+id);}
        }
        check(armor==39&&horse==1,"All allowed Chinese armor: "+armor+" horse="+horse);check(weapon>=30,"Weapons retained "+weapon);
        check(ForgeRegistries.BLOCKS.getKeys().stream().noneMatch(k->k.getNamespace().equals("chineseweapons")),"Original casting table and gem blocks are absent");
        check(ForgeRegistries.MOB_EFFECTS.getKeys().stream().noneMatch(k->k.getNamespace().equals("chineseweapons")),"No Chinese status effects registered");check(ForgeRegistries.ENCHANTMENTS.getKeys().stream().noneMatch(k->k.getNamespace().equals("chineseweapons")),"No Chinese enchantments registered");
        var profile=Class.forName("ua.empirecraft.forging.GunsmithRules").getMethod("profile",String.class);check(profile.invoke(null,ns+"iron_mountain_character_armor_helmet").equals("plate"),"Chinese helmet PR149 plate profile");check(profile.invoke(null,ns+"iron_mountain_character_armor_chestplate").equals("mail"),"Chinese lamellar PR149 mail profile");
        check(all.stream().filter(r->ForgeRegistries.ITEMS.getKey(r.result.getItem()).getNamespace().equals("chineseweapons")).count()==40,"Forty Chinese assembly recipes");
        var heads=new java.util.HashSet<Item>();int forged=0;
        for(var recipe:server.getRecipeManager().getRecipes()){
            check(!recipe.getType().toString().equals("chineseweapons:weapon_casting_shaped"),"No recipe still requires the removed table");
            if(recipe instanceof ForgingRecipe f&&ForgeRegistries.ITEMS.getKey(recipe.getResultItem(server.registryAccess()).getItem()).getNamespace().equals("chineseweapons")){
                forged++;for(var ingredient:f.getIngredients()){check(ingredient.isEmpty()||ingredient.getItems().length>0,"Chinese forging ingredient exists "+f.getId());for(var sample:ingredient.getItems()){String id=ForgeRegistries.ITEMS.getKey(sample.getItem()).toString();if(id.startsWith("tfc:metal/")&&(id.contains("_head/")||id.contains("_blade/")))heads.add(sample.getItem());}}

                var result=f.getResultItem(server.registryAccess());if(!ForgeRegistries.ITEMS.getKey(result.getItem()).getPath().endsWith("shield"))check(HeatCapability.get(result.copy())!=null,"Metal weapon has TFC heat "+f.getId());
            }
        }
        check(forged==21,"Every Chinese metal weapon and shield has native forging: "+forged);
        for(var head:heads)check(server.getRecipeManager().getRecipes().stream().anyMatch(recipe->!(recipe instanceof CustomRecipe)&&recipe.getResultItem(server.registryAccess()).getItem()==head),"Required TFC head has a live production recipe "+ForgeRegistries.ITEMS.getKey(head));
    }
    void start(ServerStartedEvent event){var server=event.getServer();
        try{
            ottoman(server);
            chinese(server);
            var level=server.overworld();var pos=new BlockPos(1600,100,1600);level.getChunkAt(pos);level.setBlockAndUpdate(pos,Armory.TABLE.get().defaultBlockState());var table=(WorkshopEntity)level.getBlockEntity(pos);var player=FakePlayerFactory.getMinecraft(level);player.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
            var all=AssemblyRecipe.all(level);check(all.size()>50,"Dynamic assembly catalog is populated: "+all.size());
            Set<String> constructions=new TreeSet<>(),namespaces=new TreeSet<>();int count=0;
            for(var r:all){
                check(r.result.getItem() instanceof ArmorItem||r.result.getItem() instanceof HorseArmorItem,"Assembly result is player or horse armor");constructions.add(r.profile.construction());namespaces.add(ForgeRegistries.ITEMS.getKey(r.result.getItem()).getNamespace());
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
            var chineseWeapon=(ForgingRecipe)server.getRecipeManager().byKey(new ResourceLocation("chineseweapons:iron_glaive")).orElseThrow();for(int i=0;i<inv.getSlots();i++)inv.setStackInSlot(i,ItemStack.EMPTY);
            for(int y=0;y<chineseWeapon.height;y++)for(int x=0;x<chineseWeapon.width;x++){var ingredient=chineseWeapon.getForgingIngredients().get(y*chineseWeapon.width+x);if(ingredient.ingredient().isEmpty())continue;var stack=ingredient.ingredient().getItems()[0].copy();HeatCapability.setTemperature(stack,1200);inv.setStackInSlot(y*3+x,stack);}
            check(anvil.getCurrentRecipe().isPresent()&&anvil.getCurrentRecipe().get().getId().equals(chineseWeapon.getId()),"Native anvil selects Chinese weapon recipe");craft.invoke(anvil);check(inv.getStackInSlot(10).getItem()==item("chineseweapons:iron_glaive"),"Native anvil actually forges Chinese weapon");check(inv.getStackInSlot(10).hasTag()&&inv.getStackInSlot(10).getTag().contains("ForgingQuality"),"Forged Chinese weapon retains equipment quality");
            var port=table.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new AssertionError("Item capability"));check(port.extractItem(1,64,true).isEmpty(),"Automation cannot extract unconsumed supplies or workpieces");
            var grid=new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu,2,2);var damaged=new ItemStack(Items.IRON_CHESTPLATE);damaged.getOrCreateTag().putString("ForgingQuality","poor");damaged.setDamageValue(damaged.getMaxDamage()-1);grid.setItem(0,damaged.copy());grid.setItem(1,damaged.copy());var combined=new RepairItemRecipe(new ResourceLocation("armory_test:repair"),CraftingBookCategory.MISC).assemble(grid,server.registryAccess());check(combined.hasTag()&&combined.getTag().getString("ForgingQuality").equals("poor"),"Vanilla combining cannot wash poor quality into baseline armor");
            for(var candidate:all)if(candidate.result.is(Items.DIAMOND_CHESTPLATE))check(candidate.extras.stream().anyMatch(i->i.test(new ItemStack(Items.DIAMOND))),"Diamond costs remain in the foundation stage");
            check(AssemblyCatalog.install(server)==0,"Catalog installation is idempotent");
            int finished=count,finishedParts=parts;
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((nothing,error)->server.execute(()->{try{if(error!=null)throw new RuntimeException(error);check(AssemblyRecipe.all(level).size()==finished,"Reload retains every assembly recipe");check(AssemblyCatalog.install(server)==0,"Reload already converted recipes before synchronization");System.out.println("ARMORY_NATIVE_PASS checks="+checks+" recipes="+finished+" parts="+finishedParts+" constructions="+constructions+" namespaces="+namespaces+" reload=true");}catch(Throwable t){System.out.println("ARMORY_NATIVE_FAIL "+t);t.printStackTrace();}finally{server.halt(false);}}));
        }catch(Throwable t){System.out.println("ARMORY_NATIVE_FAIL "+t);t.printStackTrace();server.halt(false);}
    }
}
