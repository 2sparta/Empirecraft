package ua.empirecraft.tfc;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
import net.dries007.tfc.config.TFCConfig;

@Mod(Integration.ID)
public final class Integration {
    public static final String ID="empirecraft_tfc";
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,ID);
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
    public static final DeferredRegister<com.mojang.serialization.Codec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>> LOOT=
        DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,ID);
    public static final RegistryObject<Block> DRIVE=BLOCKS.register("quern_drive",()->new QuernDriveBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Item> DRIVE_ITEM=ITEMS.register("quern_drive",()->new BlockItem(DRIVE.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<QuernDriveBlockEntity>> DRIVE_ENTITY=ENTITIES.register("quern_drive",
        ()->BlockEntityType.Builder.of(QuernDriveBlockEntity::new,DRIVE.get()).build(null));
    static {
        for(String item:List.of("barrel_blank","gun_barrel","gun_lock","gun_stock","lead_rod"))ITEMS.register(item,()->new Item(new Item.Properties()));
        for(String tool:List.of("axe","pickaxe","hoe","shovel"))ITEMS.register("golden_"+tool+"_head_blank",()->new Item(new Item.Properties()));
        LOOT.register("crop_seeds",()->SeedLoot.CODEC);
    }
    public Integration() {
        var bus=FMLJavaModLoadingContext.get().getModEventBus();ITEMS.register(bus);BLOCKS.register(bus);ENTITIES.register(bus);LOOT.register(bus);
        bus.addListener(this::config);bus.addListener(this::setup);bus.addListener(this::creative);
        bus.addListener((net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent e)->e.register(OneTimeTerrain.State.class));
        bus.addListener(AnimalIntegration::attributes);
        bus.addListener(AnimalIntegration::spawnPlacements);
        bus.addListener(this::canonicalSilver);
        MinecraftForge.EVENT_BUS.addListener(this::itemUse);MinecraftForge.EVENT_BUS.addListener(this::blockUse);
        MinecraftForge.EVENT_BUS.addListener(this::inventory);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent e)->{
            if(RecipePolicy.forbidden(Objects.toString(ForgeRegistries.BLOCKS.getKey(e.getPlacedBlock().getBlock()),"")))e.setCanceled(true);
        });
        new OneTimeTerrain();new Agriculture();new AnimalIntegration();
    }
    private void config(ModConfigEvent e) {
        if(!e.getConfig().getModId().equals("tfc"))return;
        if(e.getConfig().getType()==net.minecraftforge.fml.config.ModConfig.Type.SERVER) {
            TFCConfig.SERVER.enableForcedTFCGameRules.set(false);TFCConfig.SERVER.enableTimeStopWhenServerEmpty.set(false);
            TFCConfig.SERVER.enableBlockCollapsing.set(false);TFCConfig.SERVER.enableExplosionCollapsing.set(false);
            TFCConfig.SERVER.enableBlockLandslides.set(false);TFCConfig.SERVER.enableChiselsStartCollapses.set(false);
            TFCConfig.SERVER.powderKegEnabled.set(false);TFCConfig.SERVER.enableOverburdening.set(false);
            TFCConfig.SERVER.enableVanillaWeatherEffects.set(true);TFCConfig.SERVER.enableThirstOverheating.set(false);
            TFCConfig.SERVER.foodDecayModifier.set(0d);TFCConfig.SERVER.enableNetherPortals.set(true);
            TFCConfig.SERVER.enableInfestations.set(false);TFCConfig.SERVER.enableVanillaDrinkingMilkClearsPotionEffects.set(true);
            TFCConfig.SERVER.enableBucketsPlacingSources.set(true);TFCConfig.SERVER.enableVanillaGolems.set(true);
            TFCConfig.SERVER.enableVanillaMobsSpawningWithVanillaEquipment.set(true);
            TFCConfig.SERVER.enableVanillaMobsSpawningWithEnchantments.set(true);
            TFCConfig.SERVER.enableVanillaMonstersOnSurface.set(true);
            TFCConfig.SERVER.enableCalendarSensitiveMoonPhases.set(false);
        }
        if(e.getConfig().getType()==net.minecraftforge.fml.config.ModConfig.Type.COMMON)TFCConfig.COMMON.defaultWorldPreset.set("minecraft:normal");
    }
    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(()->{
            for(Item item:ForgeRegistries.ITEMS) {
                ResourceLocation id=ForgeRegistries.ITEMS.getKey(item);
                if(id!=null&&id.getNamespace().equals("tfc")&&!item.canBeDepleted()&&item.getMaxStackSize()>1)
                    ((net.dries007.tfc.mixin.accessor.ItemAccessor)item).accessor$setMaxStackSize(64);
            }
        });
    }
    public static String id(Item item) { return Objects.toString(ForgeRegistries.ITEMS.getKey(item),""); }
    private void canonicalSilver(RegisterEvent e){
        if(!e.getRegistryKey().equals(ForgeRegistries.Keys.ITEMS))return;
        ResourceLocation silver=new ResourceLocation("magistuarmory:silver_ingot");
        // This release ships its model/name but conditionally omits the registration.
        if(!ForgeRegistries.ITEMS.containsKey(silver))e.register(ForgeRegistries.Keys.ITEMS,
            helper->helper.register(silver,new Item(new Item.Properties())));
    }
    private void creative(BuildCreativeModeTabContentsEvent e) {
        List<ItemStack> remove=new ArrayList<>();
        for(var entry:e.getEntries())if(RecipePolicy.forbidden(id(entry.getKey().getItem()))||RecipePolicy.ALIASES.containsKey(id(entry.getKey().getItem()))||LegacyMolds.isLegacy(entry.getKey()))remove.add(entry.getKey());
        remove.forEach(e.getEntries()::remove);
        if(e.getTabKey()==net.minecraft.world.item.CreativeModeTabs.INGREDIENTS){
            for(var item:ITEMS.getEntries())e.accept(item.get());
            var silver=ForgeRegistries.ITEMS.getValue(new ResourceLocation("magistuarmory:silver_ingot"));if(silver!=null)e.accept(silver);
        }
    }
    private void itemUse(PlayerInteractEvent.RightClickItem e) {
        if(RecipePolicy.forbidden(id(e.getItemStack().getItem()))) {
            e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);
            if(!e.getLevel().isClientSide)e.getEntity().displayClientMessage(Component.translatable("message.empirecraft_tfc.disabled"),true);
        }
    }
    private void inventory(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||e.player.level().isClientSide||e.player.tickCount%20!=0)return;
        var inventory=e.player.getInventory();
        for(int slot=0;slot<inventory.getContainerSize();slot++){
            ItemStack stack=inventory.getItem(slot);
            if(LegacyMolds.migrate(e.player,slot,stack))continue;
            String canonical=RecipePolicy.ALIASES.get(id(stack.getItem()));
            if(canonical==null)continue;
            var nbt=stack.save(new net.minecraft.nbt.CompoundTag());nbt.putString("id",canonical);
            inventory.setItem(slot,ItemStack.of(nbt));
        }
    }
    private void blockUse(PlayerInteractEvent.RightClickBlock e) {
        String block=Objects.toString(ForgeRegistries.BLOCKS.getKey(e.getLevel().getBlockState(e.getPos()).getBlock()),"");
        if(RecipePolicy.forbidden(block)||RecipePolicy.forbidden(id(e.getItemStack().getItem()))) {
            e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
