package ua.empirecraft.armory;

import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(Armory.ID)
public final class Armory {
    public static final String ID="empirecraft_armory";
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,ID);
    public static final DeferredRegister<RecipeType<?>> TYPES=DeferredRegister.create(ForgeRegistries.RECIPE_TYPES,ID);
    public static final RegistryObject<Block> TABLE=BLOCKS.register("armorer_table",()->new WorkshopBlock(BlockBehaviour.Properties.of().strength(3.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Item> TABLE_ITEM=ITEMS.register("armorer_table",()->new BlockItem(TABLE.get(),new Item.Properties()));
    public static final RegistryObject<Item> WORK=ITEMS.register("unfinished_armor",()->new WorkItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<WorkshopEntity>> ENTITY=ENTITIES.register("armorer_table",()->BlockEntityType.Builder.of(WorkshopEntity::new,TABLE.get()).build(null));
    public static final RegistryObject<MenuType<WorkshopMenu>> MENU=MENUS.register("armorer_table",()->IForgeMenuType.create((id,inventory,buf)->new WorkshopMenu(id,inventory,buf.readBlockPos())));
    public static final RegistryObject<RecipeSerializer<AssemblyRecipe>> SERIALIZER=SERIALIZERS.register("assembly",AssemblyRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<AssemblyRecipe>> TYPE=TYPES.register("assembly",()->new RecipeType<>(){public String toString(){return ID+":assembly";}});
    public static final Map<String,RegistryObject<Item>> PARTS=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<Item>> PATTERNS=new LinkedHashMap<>();
    public static final RegistryObject<Item> PATTERN_BLANK=ITEMS.register("pattern_blank",()->new Item(new Item.Properties()));
    public static final String[] METALS={"iron","steel","bronze","copper","black_steel","silver","gold"};
    public static final Map<String,Integer> FORMS=Map.ofEntries(Map.entry("front",2),Map.entry("back",2),Map.entry("shoulders",1),Map.entry("arms",1),Map.entry("waist",1),Map.entry("dome",3),Map.entry("nape",1),Map.entry("visor",1),Map.entry("thighs",3),Map.entry("knees",1),Map.entry("greaves",2),Map.entry("feet",2),Map.entry("ankles",1),Map.entry("mail_panel",2),Map.entry("mail_sleeves",2),Map.entry("lamellar_panel",2),Map.entry("brigandine_panel",2),Map.entry("fluted_front",3),Map.entry("fluted_back",3),Map.entry("joints",1));
    static {
        for(String form:new TreeSet<>(FORMS.keySet()))PATTERNS.put(form,ITEMS.register("pattern_"+form,()->new PatternItem(new Item.Properties().stacksTo(1))));
        for(String metal:METALS)for(String form:new TreeSet<>(FORMS.keySet())){
            String key=metal+"_"+form;PARTS.put(key,ITEMS.register(key,()->new PartItem(metal,form,new Item.Properties().stacksTo(16))));
        }
        for(String key:new String[]{"rivets","buckle","padded_lining","leather_panel"})PARTS.put(key,ITEMS.register(key,()->new Item(new Item.Properties())));
    }
    public static Item part(String metal,String form){return PARTS.get(metal+"_"+form).get();}
    public Armory(){
        ArmoryNetwork.register();
        var bus=FMLJavaModLoadingContext.get().getModEventBus();ITEMS.register(bus);BLOCKS.register(bus);ENTITIES.register(bus);MENUS.register(bus);SERIALIZERS.register(bus);TYPES.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS)e.accept(TABLE_ITEM.get());if(e.getTabKey()==CreativeModeTabs.INGREDIENTS){e.accept(PATTERN_BLANK.get());for(var item:PATTERNS.values())e.accept(item.get());for(var item:PARTS.values())e.accept(item.get());}});
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class,Parts::attachHeat);
    }
}
