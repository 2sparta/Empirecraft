package ua.empirecraft.chinese;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.*;
public final class RegistryHooks {
 public static final String NS="chineseweapons";
 public static final Map<String,String> ALIASES=new LinkedHashMap<>();
 static {try(var in=RegistryHooks.class.getResourceAsStream("/empirecraft/chinese-aliases.json")){var data=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();for(var e:data.entrySet())ALIASES.put(e.getKey(),e.getValue().getAsString());}catch(IOException e){throw new ExceptionInInitializerError(e);}}
 private RegistryHooks(){}
 @SuppressWarnings({"rawtypes","unchecked"}) public static RegistryObject item(DeferredRegister register,String name,Supplier factory){var target=ALIASES.get(NS+":"+name);return target==null?register.register(name,factory):RegistryObject.create(new ResourceLocation(target),ForgeRegistries.ITEMS);}
 @SuppressWarnings({"rawtypes","unchecked"}) public static RegistryObject block(DeferredRegister register,String name,Supplier factory){return RegistryObject.create(new ResourceLocation(name.equals("armor_casting_table")?"empirecraft_armory:armorer_table":name.contains("deepslate")?"minecraft:deepslate":"minecraft:stone"),ForgeRegistries.BLOCKS);}
 @SuppressWarnings({"rawtypes","unchecked"}) public static RegistryObject blockEntity(DeferredRegister register,String name,Supplier factory){return RegistryObject.create(new ResourceLocation("empirecraft_armory:armorer_table"),ForgeRegistries.BLOCK_ENTITY_TYPES);}
 @SuppressWarnings({"rawtypes","unchecked"}) public static RegistryObject effect(DeferredRegister register,String name,Supplier factory){return RegistryObject.create(new ResourceLocation("minecraft:luck"),ForgeRegistries.MOB_EFFECTS);}
 @SuppressWarnings({"rawtypes","unchecked"}) public static RegistryObject blockItem(RegistryObject block){return RegistryObject.create(block.getId(),ForgeRegistries.ITEMS);}
 public static boolean rejectEffect(net.minecraft.world.entity.LivingEntity owner,net.minecraft.world.effect.MobEffectInstance effect){return false;}
 public static boolean rejectEffect(net.minecraft.world.entity.LivingEntity owner,net.minecraft.world.effect.MobEffectInstance effect,net.minecraft.world.entity.Entity source){return false;}
 public static double reach(Item item,double fallback){var key=ForgeRegistries.ITEMS.getKey(item);if(key==null)return fallback;String p=key.getPath();var type=p.contains("guduo")?com.magistuarmory.item.WeaponTypes.HEAVY_MACE:p.contains("glaive")?com.magistuarmory.item.WeaponTypes.CONCAVE_EDGED_HALBERD:p.contains("sky_piercing_halberd")?com.magistuarmory.item.WeaponTypes.PIKE:p.contains("saber")?com.magistuarmory.item.WeaponTypes.BASTARD_SWORD:com.magistuarmory.item.WeaponTypes.RANSEUR;return type.getBonusAttackReach();}
 public static int enchantmentLevel(Enchantment e,ItemStack s){return 0;}
}
