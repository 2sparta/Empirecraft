package ua.empirecraft.casting;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(Casting.ID)
public final class Casting {
    public static final String ID="empirecraft_casting";
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,ID);
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<QualityRecipe>> QUALITY=SERIALIZERS.register("quality_shaped",QualityRecipe.Serializer::new);
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,ID);
    public static final RegistryObject<Block> TABLE=BLOCKS.register("casting_table",()->new CastingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).noOcclusion().strength(4,6).requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.METAL)));
    public static final RegistryObject<Item> ITEM=ITEMS.register("casting_table",()->new BlockItem(TABLE.get(),new Item.Properties()){
        @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag flags){
            super.appendHoverText(stack,level,lines,flags);for(String key:new String[]{"beside","automatic","retainer"})lines.add(net.minecraft.network.chat.Component.translatable("tooltip."+ID+"."+key).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    });
    public static final RegistryObject<BlockEntityType<CastingBlockEntity>> ENTITY=ENTITIES.register("casting_table",()->BlockEntityType.Builder.of(CastingBlockEntity::new,TABLE.get()).build(null));
    public static final RegistryObject<MenuType<CastingMenu>> MENU=MENUS.register("casting_table",()->IForgeMenuType.create((id,inventory,buffer)->new CastingMenu(id,inventory,buffer.readBlockPos())));
    static { for(String material:new String[]{"gold","silver"})ITEMS.register(material+"_javelin_head",()->new Item(new Item.Properties())); }
    public Casting(){net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartingEvent e)->{net.dries007.tfc.config.TFCConfig.SERVER.blastFurnaceCapacity.set(16);net.dries007.tfc.config.TFCConfig.SERVER.crucibleEnableAutomation.set(true);});var bus=FMLJavaModLoadingContext.get().getModEventBus();BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);SERIALIZERS.register(bus);bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS)e.accept(ITEM.get());if(e.getTabKey()==CreativeModeTabs.INGREDIENTS)for(var entry:ITEMS.getEntries())if(entry.getId().getPath().endsWith("_javelin_head"))e.accept(entry.get());});}
}
