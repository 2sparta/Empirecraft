package net.mirrorloong.chineseweapons.init;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.*;
public final class ChineseweaponsModTabs {
 public static final DeferredRegister<CreativeModeTab> REGISTRY=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"chineseweapons");
 public static final RegistryObject<CreativeModeTab> CHINESE_WEAPONS=REGISTRY.register("chinese_weapons",()->CreativeModeTab.builder().title(Component.translatable("item_group.chineseweapons.chinese_weapons")).icon(()->new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("chineseweapons:iron_mountain_character_armor_helmet")))).displayItems((parameters,output)->ForgeRegistries.ITEMS.getValues().stream().filter(item->ForgeRegistries.ITEMS.getKey(item).getNamespace().equals("chineseweapons")).sorted(java.util.Comparator.comparing(item->ForgeRegistries.ITEMS.getKey(item).toString())).forEach(output::accept)).build());
}
