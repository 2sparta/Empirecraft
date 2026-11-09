package ua.empirecraft.survival;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.registries.*;
import sfiomn.legendarysurvivaloverhaul.api.data.json.JsonTemperatureResistance;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureUtil;
import sfiomn.legendarysurvivaloverhaul.api.thirst.*;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.temperature.*;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.thirst.*;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.wetness.*;
import sfiomn.legendarysurvivaloverhaul.common.items.drink.BottledDrinkItem;
import sfiomn.legendarysurvivaloverhaul.registry.AttributeRegistry;
import sfiomn.legendarysurvivaloverhaul.registry.ItemRegistry;
import ua.empirecraft.armor.LayerData;

/** Server-authoritative physical linings; no health or limb capabilities are attached. */
public final class SurvivalRules {
    public static final String ID="legendarysurvivaloverhaul";
    public static final Set<String> REMOVED_ITEMS=Set.of(
        "heart_container","heart_fragment","shield_container","healing_herbs","plaster","bandage","tonic","medkit","morphine","first_aid_supplies",
        "nether_chalice","sponge","heat_resistance_ring","cold_resistance_ring","thermal_resistance_ring","water_purifier",
        "cooling_coat_2","cooling_coat_3","heating_coat_2","heating_coat_3","thermal_coat_2","thermal_coat_3","cold_string","warm_string",
        "snow_helmet","snow_chestplate","snow_leggings","snow_boots","desert_helmet","desert_chestplate","desert_leggings","desert_boots",
        "chorus_fruit_juice","golden_apple_juice","golden_carrot_juice","glistering_melon_juice");
    public static final Set<String> REMOVED_EFFECTS=Set.of("painkiller","painkiller_addiction","hard_falling","vulnerability","headache","recovery");
    public static final Set<String> TEMPERATURE_ATTRIBUTES=Set.of("heating_temperature","cooling_temperature","heat_resistance","cold_resistance","thermal_resistance");
    public static final Set<String> LININGS=Set.of("heating1","cooling1","thermal1");
    public static RegistryObject<Item> RAW_WATER;
    private SurvivalRules() {}
    public static int noEnchantment(Enchantment enchantment,ItemStack stack){return 0;}
    public static ItemStack purify(net.minecraft.world.Container inventory,net.minecraft.core.RegistryAccess registry) {
        var source=inventory.getItem(0);
        if(!(source.getItem() instanceof sfiomn.legendarysurvivaloverhaul.common.items.drink.CanteenItem)||ThirstUtil.getCapacityTag(source)<=0)return ItemStack.EMPTY;
        var result=source.copy();result.setCount(1);ThirstUtil.setHydrationEnumTag(result,HydrationEnum.PURIFIED);return result;
    }
    public static void jeiSewing(Item armor,ArrayList<sfiomn.legendarysurvivaloverhaul.common.recipe.SewingRecipe> recipes) {
        if(!inner(new ItemStack(armor)))return;
        var id=ForgeRegistries.ITEMS.getKey(armor);
        for(var registered:ItemRegistry.ITEMS.getEntries())if(registered.get() instanceof sfiomn.legendarysurvivaloverhaul.common.items.CoatItem coat){
            var result=new ItemStack(armor);setCoat(result,coat.coat.id());
            recipes.add(new sfiomn.legendarysurvivaloverhaul.common.recipe.SewingRecipe(
                net.minecraft.world.item.crafting.Ingredient.of(armor),net.minecraft.world.item.crafting.Ingredient.of(coat),result,
                new ResourceLocation(ID,"sewing_"+id.getNamespace()+"_"+id.getPath()+"_"+registered.getId().getPath())));
        }
    }

    /** Unregistered compatibility handles let optional original code inspect a retired field safely. */
    @SuppressWarnings({"unchecked","rawtypes"})
    public static RegistryObject<?> register(DeferredRegister<?> registry,String name,Supplier<?> supplier) {
        String kind=registry.getRegistryKey().location().getPath();
        if(kind.equals("item")&&REMOVED_ITEMS.contains(name))return RegistryObject.create(new ResourceLocation("minecraft:air"),ForgeRegistries.ITEMS);
        if(kind.equals("mob_effect")&&REMOVED_EFFECTS.contains(name))return RegistryObject.create(new ResourceLocation("minecraft:unluck"),ForgeRegistries.MOB_EFFECTS);
        if(kind.equals("attribute")&&!TEMPERATURE_ATTRIBUTES.contains(name))return RegistryObject.create(new ResourceLocation("minecraft:generic.max_health"),ForgeRegistries.ATTRIBUTES);
        if(kind.equals("potion"))return RegistryObject.create(new ResourceLocation("minecraft:water"),ForgeRegistries.POTIONS);
        if(kind.equals("enchantment"))return RegistryObject.create(new ResourceLocation("minecraft:unbreaking"),ForgeRegistries.ENCHANTMENTS);
        return ((DeferredRegister)registry).register(name,(Supplier)supplier);
    }
    public static void registerExtra() {
        if(RAW_WATER==null)RAW_WATER=ItemRegistry.ITEMS.register("raw_water_bottle",()->new BottledDrinkItem(new Item.Properties()){});
    }
    public static void lockConfiguration() {
        sfiomn.legendarysurvivaloverhaul.config.Config.Baked.healthOverhaulEnabled=false;
        sfiomn.legendarysurvivaloverhaul.config.Config.Baked.localizedBodyDamageEnabled=false;
        sfiomn.legendarysurvivaloverhaul.config.Config.Baked.customHealthRegenEnabled=false;
        sfiomn.legendarysurvivaloverhaul.config.Config.Baked.baseFoodExhaustion=0;
    }
    public static void tooltip(ItemStack stack,List<net.minecraft.network.chat.Component> text) {
        String item=ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
        text.add(net.minecraft.network.chat.Component.translatable("tooltip.empirecraft_survival.inner_only"));
        text.add(net.minecraft.network.chat.Component.translatable("tooltip.empirecraft_survival."+item));
    }
    public static void menuOwner(net.minecraft.world.inventory.AbstractContainerMenu menu,net.minecraft.world.entity.player.Inventory inventory) {
        try {net.minecraft.world.inventory.AbstractContainerMenu.class.getField("containerOwner").set(menu,inventory.player);}
        catch(NoSuchFieldException pureForge){}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot assign sewing inventory owner",failure);}
    }
    public static <T> Object packet(
            net.minecraftforge.network.simple.SimpleChannel channel,int id,Class<T> type,
            java.util.function.BiConsumer<T,net.minecraft.network.FriendlyByteBuf> encode,
            java.util.function.Function<net.minecraft.network.FriendlyByteBuf,T> decode,
            java.util.function.BiConsumer<T,Supplier<net.minecraftforge.network.NetworkEvent.Context>> consume) {
        String name=type.getSimpleName();
        if(name.contains("Body")||name.contains("Heart")||name.equals("BodyPartHealingTimeMessage"))return null;
        return channel.registerMessage(id,type,encode,decode,consume);
    }
    public static void reload(net.minecraftforge.event.AddReloadListenerEvent event,net.minecraft.server.packs.resources.PreparableReloadListener listener) {
        if(listener!=null&&!listener.getClass().getSimpleName().startsWith("Body"))event.addListener(listener);
    }
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if(!(event.getObject() instanceof Player))return;
        event.addCapability(new ResourceLocation(ID,"temperature"),new TemperatureProvider());
        event.addCapability(new ResourceLocation(ID,"thirst"),new ThirstProvider());
        event.addCapability(new ResourceLocation(ID,"wetness"),new WetnessProvider());
    }
    public static void capabilities(RegisterCapabilitiesEvent event) {
        event.register(TemperatureCapability.class);event.register(TemperatureItemCapability.class);
        event.register(ThirstCapability.class);event.register(WetnessCapability.class);
    }
    public static void attributes(EntityAttributeModificationEvent event) {
        for(var attribute:List.of(AttributeRegistry.HEATING_TEMPERATURE,AttributeRegistry.COOLING_TEMPERATURE,
                AttributeRegistry.HEAT_RESISTANCE,AttributeRegistry.COLD_RESISTANCE,AttributeRegistry.THERMAL_RESISTANCE))
            if(!event.has(EntityType.PLAYER,attribute.get()))event.add(EntityType.PLAYER,attribute.get());
    }
    public static void creative(CreativeModeTab.Output output) {
        ForgeRegistries.ITEMS.getEntries().stream().filter(e->e.getKey().location().getNamespace().equals(ID))
            .sorted(Comparator.comparing(e->e.getKey().location().toString())).forEach(e->output.accept(e.getValue()));
        for(var item:List.of(ItemRegistry.CANTEEN.get(),ItemRegistry.LARGE_CANTEEN.get())) {
            var stack=new ItemStack(item);ThirstUtil.setCapacityTag(stack,item==ItemRegistry.CANTEEN.get()?5:10);
            ThirstUtil.setHydrationEnumTag(stack,HydrationEnum.PURIFIED);output.accept(stack);
        }
    }
    public static boolean inner(ItemStack stack) {
        return !stack.isEmpty()&&LayerData.slot(stack)!=null&&LayerData.rank(stack)==0&&!LayerData.layered(stack);
    }
    public static void setCoat(ItemStack stack,String lining) {
        if(inner(stack)&&LININGS.contains(lining))stack.getOrCreateTag().putString("Coat",lining);
    }
    public static String coat(ItemStack stack) {
        if(!inner(stack)||!stack.hasTag())return "";
        String value=stack.getTag().getString("Coat");return LININGS.contains(value)?value:"";
    }
    public static JsonTemperatureResistance lining(ItemStack stack) {
        var value=new JsonTemperatureResistance();
        switch(coat(stack)) {
            case "heating1" -> value.coldResistance=2.0f;
            case "cooling1" -> value.heatResistance=1.5f;
            case "thermal1" -> value.coldResistance=0.5f;
        }
        return value;
    }
    /** Only rain is reduced. Immersion, drying and wetness-induced cold keep their native behavior. */
    public static int rainGain(int gain,Player player) {
        if(gain<=0)return gain;
        double coverage=0;
        for(var slot:LayerData.SLOTS)for(var piece:LayerData.pieces(player.getItemBySlot(slot)))if(coat(piece).equals("thermal1"))
            coverage+=switch(slot){case CHEST->0.4;case LEGS->0.3;case HEAD,FEET->0.15;default->0;};
        if(coverage==0)return gain;
        var nbt=player.getPersistentData();double amount=gain*(1-0.65*Math.min(1,coverage))+nbt.getDouble("EmpirecraftRainRemainder");
        int result=(int)Math.floor(amount+1e-9);nbt.putDouble("EmpirecraftRainRemainder",amount-result);return result;
    }
}
